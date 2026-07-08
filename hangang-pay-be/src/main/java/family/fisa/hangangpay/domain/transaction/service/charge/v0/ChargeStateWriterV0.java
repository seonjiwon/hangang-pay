package family.fisa.hangangpay.domain.transaction.service.charge.v0;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPrepared;
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

/**
 * CHARGE(충전) 상태 쓰기 전담. 각 메서드는 REQUIRES_NEW로 독립 트랜잭션을 커밋한다.
 *
 * <p>동일 사용자의 동시 요청은 wallet 비관적 락으로 직렬화하고, 멱등성은 거래 상태(transaction_uuid + status)로 판단한다.
 */
@Slf4j
// @Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ChargeStateWriterV0 implements ChargeStateWriter {

    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.1");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** intent 생성 = PENDING (금액·출금 계좌·할인액 바인딩). transactionUuid는 서버가 발급한다. */
    public ChargeIntentResponse createIntent(
            Long partyId, ChargeIntentCreateRequest request, LocalDateTime expiresAt) {
        // 지갑 행 락 - 동일 사용자의 동시 요청을 직렬화
        Wallet toWallet =
                walletRepository
                        .findByParty_IdForUpdate(partyId)
                        .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        // 출금 계좌(본인 소유) 조회
        Account fromAccount =
                accountRepository
                        .findByIdAndParty_Id(request.accountId(), partyId)
                        .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // 거래 식별자 서버 발급 + 할인액 계산 (충전가 * 할인율, 원 단위 절사)
        String transactionUuid = UUID.randomUUID().toString();
        BigDecimal amount = request.amount();
        BigDecimal discountAmount = amount.multiply(DISCOUNT_RATE).setScale(0, RoundingMode.DOWN);

        // PENDING insert (transaction_uuid UNIQUE가 최종 방어선)
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

        // 지갑 행 락 - 동일 사용자의 동시 실행을 직렬화 (PENDING -> PROCESSING 경합 방지)
        walletRepository
                .findByParty_IdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        // PENDING CHARGE 조회
        Transaction transaction =
                transactionRepository
                        .findByTransactionUuid(transactionUuid)
                        .filter(t -> t.getTransactionType() == TransactionType.CHARGE)
                        .orElseThrow(
                                () -> new BusinessException(TransactionErrorCode.CHARGE_NOT_FOUND));

        // 소유권 검증
        transaction.validateOwner(partyId);

        // 멱등: 거래 상태로 중복 요청 판단 (SUCCESS -> 기존 결과 재사용, PROCESSING -> 진행 중)
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            return ChargeExecutionPreparationResult.snapshot(
                    ChargeExecuteResponse.from(transaction, LocalDateTime.now()));
        }
        if (transaction.getStatus() == TransactionStatus.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.CHARGE_ALREADY_PROCESSING);
        }

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

        // 실행 가능 상태 검증 (PENDING) -> PROCESSING
        transaction.validateExecutableStatus();
        transaction.markProcessing();

        // 계좌 차감 금액 = 충전가 - 할인액
        BigDecimal finalAmount = transaction.getAmount().subtract(transaction.getDiscountAmount());

        log.info("충전 실행 준비 완료. transactionUuid={}, partyId={}", transactionUuid, partyId);

        return ChargeExecutionPreparationResult.prepared(
                new ChargeExecutionPrepared(
                        transactionUuid,
                        null, // 요청 해시 미사용
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

    /** reconcile: 은행 재조회 결과를 반영하고 응답을 만든다(SUCCESS/FAILED만 상태 전환, PROCESSING은 무변경). */
    public ChargeExecuteResponse applyReconcileResult(
            String transactionUuid, BankTransactionStatusResponse bankStatus) {
        Transaction transaction = getChargeTransaction(transactionUuid);

        if (bankStatus.status() == TransactionStatus.SUCCESS) {
            validateBankSuccessReconcileResult(bankStatus);
            transaction.reconcileSuccess(
                    bankStatus.txHash(), String.valueOf(bankStatus.bankTransactionId()));
        }

        if (bankStatus.status() == TransactionStatus.FAILED) {
            transaction.reconcileFailed();
        }

        // PROCESSING(은행 아직 처리 중)이면 상태를 바꾸지 않고 현재 상태 그대로 반환한다.
        return ChargeExecuteResponse.from(transaction, bankStatus.confirmedAt());
    }

    /** reconcile 시도 횟수 증가 (PROCESSING 유지) */
    public void incrementReconcileAttempt(String transactionUuid) {
        getChargeTransaction(transactionUuid).incrementReconcileAttempt();
    }

    private void validateBankSuccessReconcileResult(BankTransactionStatusResponse bankStatus) {
        if (bankStatus.bankTransactionId() == null) {
            throw new BusinessException(TransactionErrorCode.CHARGE_RECOVERY_RESULT_INVALID);
        }
    }

    /* 충전 거래 조회 */
    private Transaction getChargeTransaction(String transactionUuid) {
        return transactionRepository
                .findByTransactionUuid(transactionUuid)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.CHARGE_NOT_FOUND));
    }
}
