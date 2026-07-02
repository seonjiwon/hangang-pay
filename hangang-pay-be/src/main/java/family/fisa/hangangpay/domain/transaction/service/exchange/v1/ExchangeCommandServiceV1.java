package family.fisa.hangangpay.domain.transaction.service.exchange.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.request.ExchangeRequest;
import family.fisa.hangangpay.client.bank.dto.response.ExchangeResponse;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.IntentCreationGuard;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeCommandService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeQueryService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpay.domain.transaction.service.support.BankCallExecutor;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** EXCHANGE 명령 오케스트레이터 종단(SUCCESS/FAILED)이 확정되면 Redis도 동기화 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeCommandServiceV1 implements ExchangeCommandService {

    /** intent TTL(분). 만료 스케줄러 기준. */
    private static final long INTENT_TTL_MINUTES = 10L;

    /** 환전 종단 실패로 매핑할 bank 오류 코드. 현재는 fallback(EXCHANGE_CONTRACT_FAILED)만 사용. */
    private static final Map<String, BaseErrorCode> EXCHANGE_BANK_FAIL_CODE_MAP = Map.of();

    private final ExchangeQueryService exchangeQueryService;
    private final ExchangeStateWriter stateWriter;
    private final BankClient bankClient;
    private final BankCallExecutor bankCallExecutor;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    // 의도 중복 생성 가드 (best-effort 부하 제어)
    private final IntentCreationGuard intentCreationGuard;

    // Redis 멱등 게이트
    private final ExchangeIdempotencyStore idempotencyStore;
    private final ExchangeRequestHashGenerator requestHashGenerator;

    /** 사용자 환전 intent - PRIMARY, 자격 검증 후 PENDING 생성 */
    public ExchangeIntentResponse createUserIntent(
            Long partyId, ExchangeIntentCreateRequest request) {
        intentCreationGuard.check(TransactionType.EXCHANGE, partyId, request.amount());
        log.info("환전 intent 생성 시작. partyId={}", partyId);
        verifyEligibility(partyId);
        return stateWriter.createIntent(partyId, request, AccountType.PRIMARY, expiresAt());
    }

    /** 가맹점 환전 intent */
    public ExchangeIntentResponse createMerchantIntent(
            Long partyId, ExchangeIntentCreateRequest request) {
        intentCreationGuard.check(TransactionType.EXCHANGE, partyId, request.amount());
        log.info("환전 intent 생성 시작(merchant). partyId={}", partyId);
        return stateWriter.createIntent(partyId, request, AccountType.SETTLEMENT, expiresAt());
    }

    /** 사용자 환전 실행 */
    public ExchangeExecuteResponse executeUserExchange(
            Long partyId, String uuid, ExchangeExecuteRequest request) {
        log.info("환전 실행 시작. partyId={}, transactionUuid={}", partyId, uuid);
        verifyUserPaymentPin(partyId, request.paymentPin());
        return doExecute(partyId, uuid);
    }

    /** 가맹점 환전 실행 */
    public ExchangeExecuteResponse executeMerchantExchange(
            Long partyId, String uuid, ExchangeExecuteRequest request) {
        log.info("환전 실행 시작(merchant). partyId={}, transactionUuid={}", partyId, uuid);
        verifyMerchantPaymentPin(partyId, request.paymentPin());
        return doExecute(partyId, uuid);
    }

    /** 게이트 → 선점(PENDING→PROCESSING) → bank */
    private ExchangeExecuteResponse doExecute(Long partyId, String uuid) {
        // 1. Redis 멱등 게이트 (동시 실행 직렬화 + 완료/실패 재요청 쳐내기)
        Optional<ExchangeExecuteResponse> hit = openIdempotencyGate(partyId, uuid);
        if (hit.isPresent()) {
            log.info("멱등 hit. transactionUuid={}", uuid);
            return hit.get();
        }

        // 2. 소유자 검증 - 남의 PENDING 거래 실행 차단
        stateWriter.validateOwner(uuid, partyId);

        // 3. intent 선점(CAS): PENDING -> PROCESSING
        TransactionStatus status = stateWriter.claimForExecution(uuid);

        return switch (status) {
            case PROCESSING -> runBank(uuid); // 방금 PENDING→PROCESSING 선점함
            case SUCCESS, FAILED -> { // Redis는 NEW였으나 DB가 종단(만료 등) → 결과 반환 + Redis 동기화
                ExchangeExecuteResponse response = stateWriter.getResponse(uuid);
                syncIdempotency(uuid, response);
                yield response;
            }
            default -> stateWriter.getResponse(uuid); // UNKNOWN 등 처리중
        };
    }

    /** bank 호출(일시적 오류 1회 재시도) + 결과 분기. 종단이면 Redis 동기화 */
    private ExchangeExecuteResponse runBank(String uuid) {
        ExchangeRequest request = stateWriter.getBankRequest(uuid);
        BankOutcome<ExchangeResponse> outcome =
                bankCallExecutor.callBankWithRetry(
                        () -> bankClient.exchange(request),
                        EXCHANGE_BANK_FAIL_CODE_MAP,
                        TransactionErrorCode.EXCHANGE_CONTRACT_FAILED);

        ExchangeExecuteResponse response =
                switch (outcome.type()) {
                    case SUCCESS -> success(uuid, outcome.value());
                    case TERMINAL_FAILED -> stateWriter.markFailed(uuid);
                    case UNKNOWN -> stateWriter.markUnknown(uuid);
                };

        syncIdempotency(uuid, response); // SUCCESS -> snapshot, FAILED -> fail, UNKNOWN -> 그대로 둠
        return response;
    }

    private ExchangeExecuteResponse success(String uuid, ExchangeResponse body) {
        return stateWriter.markSuccess(uuid, null, String.valueOf(body.bankTransactionId()));
    }

    /**
     * 종단 결과를 Redis 멱등 cord에 반영
     *
     * <p>SUCCESS -> snapshot 저장 (이후 같은 Uuid는 게이트에서 바로 반환) FAILED -> 실패 마킹 (이후 간은 uuid는
     * ALREADY_FAILED) UNKNOWN -> 아무것도 하지 않음
     */
    private void syncIdempotency(String uuid, ExchangeExecuteResponse response) {
        switch (response.status()) {
            case SUCCESS -> idempotencyStore.completeExecution(uuid, response);
            case FAILED -> idempotencyStore.failExecution(uuid);
            default -> {}
        }
    }

    /** Redis 멱등 게이트. 첫 요청이면 empty(진행), 그 외에는 분기. */
    private Optional<ExchangeExecuteResponse> openIdempotencyGate(Long partyId, String uuid) {
        String requestHash = requestHashGenerator.generate(partyId, uuid);
        ExchangeIdempotencyDecision decision = idempotencyStore.beginExecution(uuid, requestHash);

        return switch (decision.type()) {
            case NEW_REQUEST -> Optional.empty();
            case RETURN_SNAPSHOT -> Optional.of(decision.responseSnapshot());
            case ALREADY_FAILED ->
                    throw new BusinessException(TransactionErrorCode.EXCHANGE_ALREADY_FAILED);
            case PROCESSING ->
                    throw new BusinessException(TransactionErrorCode.EXCHANGE_IN_PROGRESS);
            case CONFLICT -> throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        };
    }

    private LocalDateTime expiresAt() {
        return LocalDateTime.now().plusMinutes(INTENT_TTL_MINUTES);
    }

    /** 사용자 결제 PIN 검증 */
    private void verifyUserPaymentPin(Long partyId, String paymentPin) {
        User user =
                userRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(paymentPin, user.getPaymentPinHash())) {
            log.warn("환전 PIN 불일치(user). partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }
    }

    /** 가맹점 결제 PIN 검증 */
    private void verifyMerchantPaymentPin(Long partyId, String paymentPin) {
        Merchant merchant =
                merchantRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
        if (!passwordEncoder.matches(paymentPin, merchant.getPaymentPinHash())) {
            log.warn("환전 PIN 불일치(merchant). partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }
    }

    /** 환전 자격 검증 (최근 충전액의 60% 이상 사용) — 소비자 전용 */
    private void verifyEligibility(Long partyId) {
        if (!exchangeQueryService.checkEligibility(partyId)) {
            log.warn("환전 자격 미달. partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.EXCHANGE_NOT_ELIGIBLE);
        }
    }
}
