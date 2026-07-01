package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionJpaRepository extends JpaRepository<Transaction, Long> {

    /** 비즈니스 식별자(transaction_uuid)로 단건 조회 */
    @EntityGraph(
            attributePaths = {
                "fromParty",
                "toParty",
                "fromAccount",
                "toAccount",
                "toAccount.institution",
                "fromWallet",
                "toWallet"
            })
    Optional<Transaction> findByTransactionUuid(String transactionUuid);

    /** 실행 선점 CAS - PENDING -> PROCESSING 원자적 전이. 영향 행 1=선점 성공, 0=비-PENDING */
    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE Transaction t "
                    + "SET t.status = family.fisa.hangangpay.domain.transaction.entity.TransactionStatus.PROCESSING "
                    + "WHERE t.transactionUuid = :uuid "
                    + "AND t.status = family.fisa.hangangpay.domain.transaction.entity.TransactionStatus.PENDING")
    int claimForExecution(@Param("uuid") String uuid);

    /** 거래 이력 페이징 - 수취자(toParty) fetch join */
    @EntityGraph(attributePaths = {"toParty"})
    Window<Transaction> findByFromParty_IdAndStatusAndTransactionTypeInOrderByCreatedAtDescIdDesc(
            Long fromPartyId,
            TransactionStatus status,
            List<TransactionType> transactionTypes,
            ScrollPosition position,
            Limit limit);

    /**
     * 가맹점 결제 이력 페이징. - PAYMENT: 사용자 -> 가맹점 결제이므로 가맹점은 toParty - CANCEL: 가맹점 -> 사용자 환불이므로 가맹점은
     * fromParty
     */
    @EntityGraph(attributePaths = {"fromParty", "toParty"})
    Window<Transaction>
            findByStatusAndTransactionTypeAndToParty_IdOrStatusAndTransactionTypeAndFromParty_IdOrderByCreatedAtDescIdDesc(
                    TransactionStatus paymentStatus,
                    TransactionType paymentType,
                    Long merchantToPartyId,
                    TransactionStatus cancelStatus,
                    TransactionType cancelType,
                    Long merchantFromPartyId,
                    ScrollPosition position,
                    Limit limit);

    /** 거래 상세 - fromParty + Account + Wallet fetch join */
    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.transactionType IN :types")
    @EntityGraph(
            attributePaths = {
                "fromParty",
                "toParty",
                "fromAccount",
                "fromAccount.institution",
                "toAccount",
                "toAccount.institution",
                "fromWallet",
                "toWallet"
            })
    Optional<Transaction> findByIdAndTransactionTypeIn(
            @Param("id") Long id, @Param("types") List<TransactionType> types);

    /** 스케줄러용 - UNKNOWN 상태 PAYMENT 목록 조회 (fromParty fetch join) */
    @EntityGraph(attributePaths = {"fromParty"})
    List<Transaction> findByStatusAndTransactionType(
            TransactionStatus status, TransactionType type);

    /** 파티 식별자 기준 특정 월의 거래 유형별 누적 금액 조회 */
    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
                    + "WHERE t.fromParty.id = :partyId "
                    + "AND t.transactionType = :type "
                    + "AND t.status = :status "
                    + "AND t.createdAt >= :startOfMonth "
                    + "AND t.createdAt < :startOfNextMonth")
    BigDecimal sumMonthlyAmount(
            @Param("partyId") Long partyId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("startOfMonth") LocalDateTime startOfMonth,
            @Param("startOfNextMonth") LocalDateTime startOfNextMonth);

    /** 가장 최근 SUCCESS CHARGE 1건 */
    Optional<Transaction>
            findFirstByFromParty_IdAndTransactionTypeAndStatusOrderByCreatedAtDescIdDesc(
                    Long fromPartyId, TransactionType transactionType, TransactionStatus status);

    /** 살아있는 PENDING 결제 의도 재사용 - 같은 from+to+amount, createdAt 이후(=만료 전) 가장 최근 1건 */
    Optional<Transaction>
            findFirstByFromParty_IdAndToParty_IdAndAmountAndTransactionTypeAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                    Long fromPartyId,
                    Long toPartyId,
                    BigDecimal amount,
                    TransactionType transactionType,
                    TransactionStatus status,
                    LocalDateTime threshold);

    /** 특정 시점 이전(exclusive)의 SUCCESS 거래 타입별 누적 금액 */
    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
                    + "WHERE t.fromParty.id = :partyId "
                    + "AND t.transactionType = :type "
                    + "AND t.status = "
                    + "  family.fisa.hangangpay.domain.transaction.entity.TransactionStatus.SUCCESS "
                    + "AND t.createdAt < :before")
    BigDecimal sumSuccessByTypeBefore(
            @Param("partyId") Long partyId,
            @Param("type") TransactionType type,
            @Param("before") LocalDateTime before);

    /** 특정 시점 이후(inclusive)의 SUCCESS 거래 타입별 누적 금액 */
    @Query(
            "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
                    + "WHERE t.fromParty.id = :partyId "
                    + "AND t.transactionType = :type "
                    + "AND t.status = "
                    + "  family.fisa.hangangpay.domain.transaction.entity.TransactionStatus.SUCCESS "
                    + "AND t.createdAt >= :since")
    BigDecimal sumSuccessByTypeSince(
            @Param("partyId") Long partyId,
            @Param("type") TransactionType type,
            @Param("since") LocalDateTime since);

    /** 원거래 UUID를 참조하는 특정 상태/타입 거래 존재 여부 */
    boolean existsByOriginalTransactionUuidAndTransactionTypeAndStatus(
            String originalTransactionUuid,
            TransactionType transactionType,
            TransactionStatus status);

    /** 복구 가능한 CANCEL 조회 - IN (UNKNOWN, PROCESSING) */
    @EntityGraph(attributePaths = {"fromParty"})
    Optional<Transaction> findByOriginalTransactionUuidAndTransactionTypeAndStatusIn(
            String originalTransactionUuid,
            TransactionType transactionType,
            Collection<TransactionStatus> statuses);

    @Query(
            "SELECT t FROM Transaction t "
                    + "WHERE t.toParty.id = :merchantPartyId "
                    + "AND t.transactionType = family.fisa.hangangpay.domain.transaction.entity.TransactionType.PAYMENT "
                    + "AND t.status = :status "
                    + "AND t.createdAt >= :startInclusive "
                    + "AND t.createdAt < :endExclusive")
    List<Transaction> findMerchantPaymentsBetween(
            @Param("merchantPartyId") Long merchantPartyId,
            @Param("status") TransactionStatus status,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    /** 복구 대상 - 특정 상태 + 타입 + updatedAt 이전 + 시도 한도 미만 */
    @EntityGraph(attributePaths = {"fromParty"})
    List<Transaction>
            findByStatusAndTransactionTypeAndUpdatedAtBeforeAndReconcileAttemptCountLessThan(
                    TransactionStatus status,
                    TransactionType type,
                    LocalDateTime threshold,
                    int maxAttempts);

    /** 포기(alert) 대상 - updatedAt 이전 + 시도 횟수 정확히 일치(원샷 알림용) */
    @EntityGraph(attributePaths = {"fromParty"})
    List<Transaction> findByStatusAndTransactionTypeAndUpdatedAtBeforeAndReconcileAttemptCount(
            TransactionStatus status,
            TransactionType type,
            LocalDateTime threshold,
            int attemptCount);

    @Query(
            "SELECT t FROM Transaction t "
                    + "WHERE t.transactionType = :type "
                    + "AND t.reconcileAttemptCount < :maxRetry "
                    + "AND (t.status = family.fisa.hangangpay.domain.transaction.entity.TransactionStatus.UNKNOWN "
                    + "  OR (t.status = family.fisa.hangangpay.domain.transaction.entity.TransactionStatus.PROCESSING "
                    + "      AND t.updatedAt < :threshold))")
    List<Transaction> findExchangeReconcileTargets(
            @Param("type") TransactionType type,
            @Param("maxRetry") int maxRetry,
            @Param("threshold") LocalDateTime threshold);

    @Query(
            "SELECT t FROM Transaction t "
                    + "WHERE t.transactionType = :type "
                    + "AND t.status = :status "
                    + "AND t.createdAt < :threshold")
    List<Transaction> findStalePendingExchangeIntents(
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("threshold") LocalDateTime threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE Transaction t "
                    + "SET t.status = EXPIRED, t.updatedAt = :now "
                    + "WHERE t.transactionType = :type "
                    + "  AND t.status = PENDING "
                    + "  AND t.createdAt < :threshold")
    int expireStalePendingIntents(
            @Param("type") TransactionType type,
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold);

    /** 환전 reconcile 포기 대상 - PROCESSING/UNKNOWN + 시도 한도 소진 */
    @Query(
            "SELECT t FROM Transaction t "
                    + "WHERE t.transactionType = :type "
                    + "AND t.status IN :statuses "
                    + "AND t.reconcileAttemptCount >= :maxRetry")
    List<Transaction> findExchangeAbandonedTargets(
            @Param("type") TransactionType type,
            @Param("statuses") List<TransactionStatus> statuses,
            @Param("maxRetry") int maxRetry);
}
