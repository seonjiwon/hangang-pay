package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Transaction saveAndFlush(Transaction transaction);

    Optional<Transaction> findById(Long id);

    /** 비즈니스 식별자(transaction_uuid)로 단건 조회 - CANCEL 시 원본 PAYMENT 조회용 */
    Optional<Transaction> findByTransactionUuid(String transactionUuid);

    /** 실행 선점 CAS - PENDING -> PROCESSING 원자적 전이. 영향 행 수(1=선점 성공, 0=비-PENDING) */
    int claimForExecution(String transactionUuid);

    /** 거래 이력 페이징 (TransactionStatus=SUCCESS, TransactionType= ?) */
    Window<Transaction> findTransactionByPartyId(
            Long partyId,
            TransactionStatus status,
            List<TransactionType> types,
            ScrollPosition position,
            Limit limit);

    /** 가맹점 수취 결제/취소 이력 페이징 */
    Window<Transaction> findPaymentTransactionsByMerchantPartyId(
            Long partyId, TransactionStatus status, ScrollPosition position, Limit limit);

    /** 거래 상세 - id + type IN, fromParty/fromAccount/toAccount/fromWallet/toWallet fetch join */
    Optional<Transaction> findDetailByIdAndTypes(Long id, List<TransactionType> types);

    /** 파티 식별자 기준 특정 월의 거래 유형별 누적 금액 조회 */
    BigDecimal sumMonthlyAmount(
            Long partyId,
            TransactionType type,
            TransactionStatus status,
            LocalDateTime startOfMonth,
            LocalDateTime startOfNextMonth);

    /** 가장 최근 SUCCESS CHARGE 1건 - 환전 자격 검증용 */
    Optional<Transaction> findLatestSuccessCharge(Long partyId);

    /** 만료 대상 - CHARGE + PENDING + createdAt < threshold */
    List<Transaction> findStalePendingChargeIntents(LocalDateTime threshold);

    /** 만료 대상 - PAYMENT + PENDING + createdAt < threshold */
    int expireStalePendingPaymentIntents(LocalDateTime threshold, LocalDateTime now);

    /** 재사용 대상 - PAYMENT + PENDING + 같은 from/to/amount + createdAt > threshold(만료 전) */
    Optional<Transaction> findLivePendingPayment(
            Long fromPartyId, Long toPartyId, BigDecimal amount, LocalDateTime threshold);

    /** 특정 시점 이전(exclusive)의 SUCCESS 거래 타입별 누적 금액 - 잔액 산정용 */
    BigDecimal sumSuccessByTypeBefore(Long partyId, TransactionType type, LocalDateTime before);

    /** 특정 시점 이후(inclusive)의 SUCCESS 거래 타입별 누적 금액 - 사용액 산정용 */
    BigDecimal sumSuccessByTypeSince(Long partyId, TransactionType type, LocalDateTime since);

    /** 원거래 UUID를 참조하는 SUCCESS CANCEL 거래 존재 여부 */
    boolean existsSuccessCancelByOriginalTransactionUuid(String originalTransactionUuid);

    /** 원본 PAYMENT의 SUCCESS + CANCEL 존재 여부 확인 - 재취소 방지용 */
    boolean existsSuccessCancelFor(String originalTransactionUuid);

    /** 복구 가능한 CANCEL 조회 - CANCEL + status = UNKNOWN */
    Optional<Transaction> findReconcilableCancelByOriginalTransactionUuid(
            String originalTransactionUuid);

    List<Transaction> findMerchantPaymentsBetween(
            Long merchantPartyId,
            TransactionStatus status,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive);

    /** reconcile 대상(공통) - UNKNOWN(즉시) + PROCESSING(threshold 이전, 라이브 제외), 시도 한도 미만 */
    List<Transaction> findReconcileTargets(
            TransactionType type, int maxAttempts, LocalDateTime threshold);

    /** 만료 대상 - EXCHANGE + PENDING + createdAt < threshold */
    List<Transaction> findStalePendingExchangeIntents(LocalDateTime threshold);

    /** reconcile 포기 대상(공통) - PROCESSING/UNKNOWN + 시도 한도 소진(>=) */
    List<Transaction> findAbandonedTargets(TransactionType type, int maxAttempts);
}
