package family.fisa.hangangpay.domain.transaction.service.exchange.v1;

import family.fisa.hangangpay.client.bank.dto.request.ExchangeRequest;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpay.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * EXCHANGE 상태 쓰기/조회 전담
 *
 * <p>각 메서드는 REQUIRES_NEW로 자기 트랜잭션을 열고 독립적인 Commit 수행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeStateWriterV1 implements ExchangeStateWriter {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final AccountRepository accountRepository;

    /** intent 생성 = PENDING. transactionUuid는 서버가 발급한다(FE 신뢰 안 함). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExchangeIntentResponse createIntent(
            Long partyId,
            ExchangeIntentCreateRequest request,
            AccountType depositType,
            LocalDateTime expiresAt) {
        // 1. 거래 식별자 서버 발급
        String transactionUuid = UUID.randomUUID().toString();

        // 2. 입금 지갑/계좌 조회
        Wallet fromWallet =
                walletRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Account toAccount =
                accountRepository
                        .findByParty_IdAndAccountType(partyId, depositType)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // 3. PENDING insert (transaction_uuid UNIQUE가 최종 방어선)
        Transaction saved =
                transactionRepository.save(
                        Transaction.forExchange(
                                transactionUuid,
                                fromWallet.getParty(),
                                fromWallet,
                                toAccount,
                                request.amount(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));

        log.info(
                "환전 intent 생성. transactionUuid={}, transactionId={}",
                transactionUuid,
                saved.getId());
        return ExchangeIntentResponse.from(saved, expiresAt);
    }

    /**
     * 실행 선점(CAS): PENDING -> PROCESSING 원자적 전이. 영향 행 1이면 내가 선점한 것이므로 PROCESSING을 반환하고, 0이면 이미
     * 비-PENDING(종단/진행중)이므로 현재 상태를 재조회해 반환한다. read-then-write race를 제거한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionStatus claimForExecution(String uuid) {
        int affected = transactionRepository.claimForExecution(uuid);
        if (affected == 1) {
            return TransactionStatus.PROCESSING;
        }
        return findByUuid(uuid).getStatus();
    }

    /** 소유자 검증 - 남의 거래 실행 차단. claim 전에 호출한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void validateOwner(String uuid, Long partyId) {
        findByUuid(uuid).validateOwner(partyId);
    }

    /** bank 호출용 요청 빌드 (트랜잭션 내부에서 수행) */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ExchangeRequest getBankRequest(String uuid) {
        Transaction tx = findByUuid(uuid);
        return new ExchangeRequest(
                uuid,
                tx.getToAccount().getInstitution().getId(),
                tx.getFromWallet().getAddress(),
                tx.getToAccount().getAccountNumber(),
                tx.getAmount());
    }

    /** 미저장 거래를 SUCCESS로 확정하며 저장 + 응답 빌드 */
    public ExchangeExecuteResponse completeExchange(
            Transaction tx, String txHash, String bankTransactionId) {

        tx.markSuccess(txHash, bankTransactionId);
        Transaction saved = transactionRepository.save(tx);
        return ExchangeExecuteResponse.from(saved);
    }

    /** 현재 상태 응답 빌드 - 멱등 재요청(이미 SUCCESS/FAILED) 시 그대로 돌려주기 위함 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ExchangeExecuteResponse getResponse(String uuid) {
        return ExchangeExecuteResponse.from(findByUuid(uuid));
    }

    /** SUCCESS 확정 + 응답 빌드 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExchangeExecuteResponse markSuccess(
            String uuid, String txHash, String bankTransactionId) {
        Transaction tx = findByUuid(uuid);
        tx.markSuccess(txHash, bankTransactionId); // status=SUCCESS
        tx.assignApprovalNumber(makeApvNumber(tx.getId()));
        log.info("환전 SUCCESS. transactionUuid={}, txHash={}", uuid, txHash);
        return ExchangeExecuteResponse.from(tx);
    }

    private String makeApvNumber(Long id) {
        return "APV-" + LocalDateTime.now().getYear() + "-" + String.format("%08d", id);
    }

    /** FAILED 확정 + 응답 빌드 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExchangeExecuteResponse markFailed(String uuid) {
        Transaction tx = findByUuid(uuid);
        tx.markFailed(); // status=FAILED
        log.info("환전 FAILED. transactionUuid={}", uuid);
        return ExchangeExecuteResponse.from(tx);
    }

    /** UNKNOWN 확정 (retry_count++) + 응답 빌드 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExchangeExecuteResponse markUnknown(String uuid) {
        Transaction tx = findByUuid(uuid);
        tx.markUnknown(); // status=UNKNOWN
        tx.incrementReconcileAttempt();
        log.warn(
                "환전 UNKNOWN. transactionUuid={}, retryCount={}",
                uuid,
                tx.getReconcileAttemptCount());
        return ExchangeExecuteResponse.from(tx);
    }

    /** reconcile 재시도 예산 +1 (배치에서 bank가 아직 PENDING일 떄) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementRetry(String uuid) {
        findByUuid(uuid).incrementReconcileAttempt();
    }

    /** 버려진 PENDING intent 만료 처리 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExpired(String uuid) {
        Transaction tx = findByUuid(uuid);
        tx.markExpired();
        log.info("환전 intent 만료(EXPIRED). transactionUuid={}", uuid);
    }

    /** uuid로 EXCHANGE 거래 조회 */
    private Transaction findByUuid(String uuid) {
        return transactionRepository
                .findByTransactionUuid(uuid)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.EXCHANGE_NOT_FOUND));
    }
}
