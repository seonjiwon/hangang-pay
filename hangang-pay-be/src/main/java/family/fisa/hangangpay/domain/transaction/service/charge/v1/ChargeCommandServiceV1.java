package family.fisa.hangangpay.domain.transaction.service.charge.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.ChargeResponse;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.IntentCreationGuard;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeCommandService;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeStateWriter;
import family.fisa.hangangpay.domain.transaction.service.support.BankCallExecutor;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeCommandServiceV1 implements ChargeCommandService {

    /** intent TTL(분). 만료 스케줄러 기준. */
    private static final long INTENT_TTL_MINUTES = 10L;

    /** 충전 종단 실패로 매핑할 bank 오류 코드. 미매핑 코드는 fallback(CHARGE_FAILED). */
    private static final Map<String, BaseErrorCode> CHARGE_BANK_FAIL_CODE_MAP =
            Map.of(
                    "TRANSACTION_INSUFFICIENT_BALANCE",
                    TransactionErrorCode.CHARGE_INSUFFICIENT_BALANCE,
                    "TRANSACTION_ALREADY_FAILED",
                    TransactionErrorCode.CHARGE_ALREADY_FAILED);

    private final BankClient bankClient;
    private final ChargeIdempotencyStore chargeIdempotencyStore;
    private final ChargeStateWriter chargeStateWriter;
    private final BankCallExecutor bankCallExecutor;

    // 의도 중복 생성 가드 (best-effort 부하 제어)
    private final IntentCreationGuard intentCreationGuard;

    /** 충전 intent - 금액·출금 계좌 바인딩 후 PENDING 생성 */
    public ChargeIntentResponse createIntent(Long partyId, ChargeIntentCreateRequest request) {
        intentCreationGuard.check(
                TransactionType.CHARGE, partyId, request.amount(), request.accountId());
        log.info("충전 intent 생성 시작. partyId={}", partyId);
        return chargeStateWriter.createIntent(partyId, request, expiresAt());
    }

    /** 충전 실행 오케스트레이션: 멱등성 판단 → 은행 충전 요청 → 상태 전환 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChargeExecuteResponse execute(
            Long partyId, String transactionUuid, ChargeExecuteRequest request) {

        ChargeExecutionPreparationResult result =
                chargeStateWriter.prepareProcessing(partyId, transactionUuid, request.paymentPin());

        if (result.hasSnapshot()) {
            return result.responseSnapshot();
        }

        ChargeExecutionPrepared prepared = result.prepared();

        // 은행 충전 요청 (일시적 오류 1회 재시도 → 결과 분류)
        log.info("충전 은행 요청 시작. transactionUuid={}", prepared.transactionUuid());
        BankOutcome<ChargeResponse> outcome =
                bankCallExecutor.callBankWithRetry(
                        () -> bankClient.charge(prepared.toBankChargeRequest()),
                        CHARGE_BANK_FAIL_CODE_MAP,
                        TransactionErrorCode.CHARGE_FAILED);

        return switch (outcome.type()) {
            case SUCCESS -> {
                ChargeResponse bankResponse = outcome.value();
                log.info("충전 은행 요청 완료. transactionUuid={}", prepared.transactionUuid());
                ChargeExecuteResponse response =
                        chargeStateWriter.completeSuccess(
                                prepared.transactionUuid(),
                                bankResponse.txHash(),
                                String.valueOf(bankResponse.bankTransactionId()),
                                bankResponse.confirmedAt(),
                                bankResponse.walletBalance());
                chargeIdempotencyStore.completeExecution(prepared.transactionUuid(), response);
                yield response;
            }
            case UNKNOWN -> {
                log.warn("충전 은행 응답 불확실(UNKNOWN). transactionUuid={}", prepared.transactionUuid());
                ChargeExecuteResponse response =
                        chargeStateWriter.markUnknown(prepared.transactionUuid());
                chargeIdempotencyStore.markExecutionStatus(
                        prepared.transactionUuid(), TransactionStatus.UNKNOWN);
                yield response;
            }
            case TERMINAL_FAILED -> {
                log.warn("충전 종단 실패. transactionUuid={}", prepared.transactionUuid());
                chargeStateWriter.markFailed(prepared.transactionUuid());
                chargeIdempotencyStore.markExecutionStatus(
                        prepared.transactionUuid(), TransactionStatus.FAILED);
                throw new BusinessException(outcome.errorCode());
            }
        };
    }

    private LocalDateTime expiresAt() {
        return LocalDateTime.now().plusMinutes(INTENT_TTL_MINUTES);
    }
}
