package family.fisa.hangangpay.domain.transaction.service.charge.v1;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyDecisionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeStateWriter;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ChargeStateWriterV1 implements ChargeStateWriter {

    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.1");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChargeIdempotencyStore chargeIdempotencyStore;
    private final ChargeRequestHashGenerator chargeRequestHashGenerator;

    /** intent 생성 = PENDING (금액·출금 계좌·할인액 바인딩). transactionUuid는 서버가 발급한다. */
    public ChargeIntentResponse createIntent(
            Long partyId, ChargeIntentCreateRequest request, LocalDateTime expiresAt) {
        // 1. 거래 식별자 서버 발급
        String transactionUuid = UUID.randomUUID().toString();

        // 2. 출금 계좌(본인 소유) / 입금 지갑 조회
        Account fromAccount =
                accountRepository
                        .findByIdAndParty_Id(request.accountId(), partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        Wallet toWallet =
                walletRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        // 3. 할인액 계산 (충전가 * 할인율, 원 단위 절사)
        BigDecimal amount = request.amount();
        BigDecimal discountAmount = amount.multiply(DISCOUNT_RATE).setScale(0, RoundingMode.DOWN);

        // 4. PENDING insert (transaction_uuid UNIQUE가 최종 방어선)
        Transaction saved =
                transactionRepository.save(
                        Transaction.forCharge(
                                transactionUuid,
                                fromAccount.getParty(),
                                fromAccount,
                                toWallet,
                                amount,
                                discountAmount,
                                DISCOUNT_RATE));

        log.info(
                "충전 intent 생성. transactionUuid={}, transactionId={}",
                transactionUuid,
                saved.getId());
        return ChargeIntentResponse.from(saved, expiresAt);
    }

    /** 충전 거래 실행 준비: 검증, 멱등성 판단, PROCESSING 전환 */
    public ChargeExecutionPreparationResult prepareProcessing(
            Long partyId, String transactionUuid, String paymentPin) {

        // PENDING CHARGE 조회
        Transaction transaction =
                transactionRepository
                        .findByTransactionUuid(transactionUuid)
                        .filter(t -> t.getTransactionType() == TransactionType.CHARGE)
                        .orElseThrow(
                                () -> new BusinessException(TransactionErrorCode.CHARGE_NOT_FOUND));

        // 소유권 검증
        transaction.validateOwner(partyId);

        // intent에 바인딩된 출금 계좌
        Account account = transaction.getFromAccount();

        // PIN 검증
        User user =
                userRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(paymentPin, user.getPaymentPinHash())) {
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }

        // 요청 중복 여부 확인 (intent에 바인딩된 계좌·금액 기준)
        String requestHash =
                chargeRequestHashGenerator.generate(
                        transactionUuid, partyId, account.getId(), transaction.getAmount());

        ChargeIdempotencyDecision decision =
                chargeIdempotencyStore.beginExecution(
                        transactionUuid, requestHash, transaction.getId());

        if (decision.type() == PaymentIdempotencyDecisionType.RETURN_SNAPSHOT) {
            return ChargeExecutionPreparationResult.snapshot(decision.responseSnapshot());
        }
        if (decision.type() == PaymentIdempotencyDecisionType.CONFLICT) {
            throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (decision.type() == PaymentIdempotencyDecisionType.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.CHARGE_ALREADY_PROCESSING);
        }

        // 실행 가능 상태 검증 (PENDING)
        transaction.validateExecutableStatus();

        // PENDING → PROCESSING
        transaction.markProcessing();

        // 계좌 차감 금액 = 충전가 - 할인액
        BigDecimal finalAmount = transaction.getAmount().subtract(transaction.getDiscountAmount());

        log.info("충전 실행 준비 완료. transactionUuid={}, partyId={}", transactionUuid, partyId);

        return ChargeExecutionPreparationResult.prepared(
                new ChargeExecutionPrepared(
                        transactionUuid,
                        requestHash,
                        account.getInstitution().getId(),
                        account.getAccountNumber(),
                        transaction.getToWallet().getAddress(),
                        finalAmount, // 계좌 차감 금액 (실 결제 금액, ex. 할인율 10% = 충전가의 90%)
                        transaction.getAmount())); // 지갑 mint 금액 (충전가)
    }

    /** 충전 성공 처리 */
    public ChargeExecuteResponse completeSuccess(
            String transactionUuid,
            String txHash,
            String bankTransactionId,
            LocalDateTime confirmedAt,
            BigDecimal walletBalance) {
        Transaction transaction = getChargeTransaction(transactionUuid);
        transaction.markSuccess(txHash, bankTransactionId);
        log.info("충전 성공. transactionUuid={}, txHash={}", transactionUuid, txHash);
        return ChargeExecuteResponse.from(transaction, confirmedAt, walletBalance);
    }

    /** 충전 상태 불명 처리 */
    public ChargeExecuteResponse markUnknown(String transactionUuid) {
        Transaction transaction = getChargeTransaction(transactionUuid);
        transaction.markUnknown();
        log.warn("충전 상태 불명. transactionUuid={}", transactionUuid);
        return ChargeExecuteResponse.from(transaction, LocalDateTime.now());
    }

    /** 충전 실패 처리 */
    public ChargeExecuteResponse markFailed(String transactionUuid) {
        Transaction transaction = getChargeTransaction(transactionUuid);
        transaction.markFailed();
        log.warn("충전 실패. transactionUuid={}", transactionUuid);
        return ChargeExecuteResponse.from(transaction, LocalDateTime.now());
    }

    /** TTL 지난 PENDING intent를 EXPIRED 처리 */
    public void markExpired(String transactionUuid) {
        Transaction transaction = getChargeTransaction(transactionUuid);
        transaction.markExpired();
        log.info("충전 intent 만료(EXPIRED). transactionUuid={}", transactionUuid);
    }

    /* 충전 거래 조회 */
    private Transaction getChargeTransaction(String transactionUuid) {
        return transactionRepository
                .findByTransactionUuid(transactionUuid)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.CHARGE_NOT_FOUND));
    }
}
