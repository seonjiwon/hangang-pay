package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    /** 거래 저장 */
    @Override
    public Transaction save(Transaction transaction) {
        return jpaRepository.save(transaction);
    }

    /** 거래 저장 후 즉시 flush */
    @Override
    public Transaction saveAndFlush(Transaction transaction) {
        return jpaRepository.saveAndFlush(transaction);
    }

    /** PK로 거래 단건 조회 */
    @Override
    public Optional<Transaction> findById(Long id) {
        return jpaRepository.findById(id);
    }

    /** 비즈니스 식별자(UUID)로 거래 단건 조회 */
    @Override
    public Optional<Transaction> findByTransactionUuid(String transactionUuid) {
        return jpaRepository.findByTransactionUuid(transactionUuid);
    }

    /** 실행 선점 CAS - PENDING -> PROCESSING */
    @Override
    public int claimForExecution(String transactionUuid) {
        return jpaRepository.claimForExecution(transactionUuid);
    }

    /** 사용자 거래 이력 커서 페이징 (상태, 유형 필터) */
    @Override
    public Window<Transaction> findTransactionByPartyId(
            Long partyId,
            TransactionStatus status,
            List<TransactionType> types,
            ScrollPosition position,
            Limit limit) {

        return jpaRepository
                .findByFromParty_IdAndStatusAndTransactionTypeInOrderByCreatedAtDescIdDesc(
                        partyId, status, types, position, limit);
    }

    /** 가맹점 수취 결제, 취소 이력 커서 페이징 */
    @Override
    public Window<Transaction> findPaymentTransactionsByMerchantPartyId(
            Long partyId, TransactionStatus status, ScrollPosition position, Limit limit) {
        return jpaRepository
                .findByStatusAndTransactionTypeAndToParty_IdOrStatusAndTransactionTypeAndFromParty_IdOrderByCreatedAtDescIdDesc(
                        status,
                        TransactionType.PAYMENT,
                        partyId,
                        status,
                        TransactionType.CANCEL,
                        partyId,
                        position,
                        limit);
    }

    /** id + 거래유형 목록으로 거래 상세 조회 */
    @Override
    public Optional<Transaction> findDetailByIdAndTypes(Long id, List<TransactionType> types) {
        return jpaRepository.findByIdAndTransactionTypeIn(id, types);
    }

    /** 특정 월 거래 유형별 누적 금액 조회 */
    @Override
    public BigDecimal sumMonthlyAmount(
            Long partyId,
            TransactionType type,
            TransactionStatus status,
            LocalDateTime startOfMonth,
            LocalDateTime startOfNextMonth) {
        return jpaRepository.sumMonthlyAmount(
                partyId, type, status, startOfMonth, startOfNextMonth);
    }

    /** 가장 최근 SUCCESS CHARGE 1건 조회 - 환전 자격 검증용 */
    @Override
    public Optional<Transaction> findLatestSuccessCharge(Long partyId) {
        return jpaRepository
                .findFirstByFromParty_IdAndTransactionTypeAndStatusOrderByCreatedAtDescIdDesc(
                        partyId, TransactionType.CHARGE, TransactionStatus.SUCCESS);
    }

    /** 만료 대상 - CHARGE + PENDING + createdAt < threshold */
    @Override
    public List<Transaction> findStalePendingChargeIntents(LocalDateTime threshold) {
        return jpaRepository.findStalePendingExchangeIntents(
                TransactionType.CHARGE, TransactionStatus.PENDING, threshold);
    }

    /** 만료 대상 - PAYMENT + PENDING + createdAt < threshold */
    @Override
    public int expireStalePendingPaymentIntents(LocalDateTime threshold, LocalDateTime now) {
        return jpaRepository.expireStalePendingIntents(TransactionType.PAYMENT, now, threshold);
    }

    /** 재사용 대상 - PAYMENT + PENDING + 같은 from/to/amount + createdAt > threshold(만료 전) 최신 1건 */
    @Override
    public Optional<Transaction> findLivePendingPayment(
            Long fromPartyId, Long toPartyId, BigDecimal amount, LocalDateTime threshold) {
        return jpaRepository
                .findFirstByFromParty_IdAndToParty_IdAndAmountAndTransactionTypeAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        fromPartyId,
                        toPartyId,
                        amount,
                        TransactionType.PAYMENT,
                        TransactionStatus.PENDING,
                        threshold);
    }

    /** 특정 시점 이전(exclusive) SUCCESS 거래 유형별 누적 금액 */
    @Override
    public BigDecimal sumSuccessByTypeBefore(
            Long partyId, TransactionType type, LocalDateTime before) {
        return jpaRepository.sumSuccessByTypeBefore(partyId, type, before);
    }

    /** 특정 시점 이후(inclusive) SUCCESS 거래 유형별 누적 금액 */
    @Override
    public BigDecimal sumSuccessByTypeSince(
            Long partyId, TransactionType type, LocalDateTime since) {
        return jpaRepository.sumSuccessByTypeSince(partyId, type, since);
    }

    @Override
    public boolean existsSuccessCancelByOriginalTransactionUuid(String originalTransactionUuid) {
        return jpaRepository.existsByOriginalTransactionUuidAndTransactionTypeAndStatus(
                originalTransactionUuid, TransactionType.CANCEL, TransactionStatus.SUCCESS);
    }

    @Override
    public boolean existsSuccessCancelFor(String originalTransactionUuid) {
        return jpaRepository.existsByOriginalTransactionUuidAndTransactionTypeAndStatus(
                originalTransactionUuid, TransactionType.CANCEL, TransactionStatus.SUCCESS);
    }

    @Override
    public Optional<Transaction> findReconcilableCancelByOriginalTransactionUuid(
            String originalTransactionUuid) {
        // UNKNOWN + 오래된 PROCESSING(sweep 대상) 둘 다 복구 가능 CANCEL로 본다.
        return jpaRepository.findByOriginalTransactionUuidAndTransactionTypeAndStatusIn(
                originalTransactionUuid,
                TransactionType.CANCEL,
                List.of(TransactionStatus.UNKNOWN, TransactionStatus.PROCESSING));
    }

    @Override
    public List<Transaction> findMerchantPaymentsBetween(
            Long merchantPartyId,
            TransactionStatus status,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        return jpaRepository.findMerchantPaymentsBetween(
                merchantPartyId, status, startInclusive, endExclusive);
    }

    @Override
    public List<Transaction> findReconcileTargets(
            TransactionType type, int maxAttempts, LocalDateTime threshold) {
        return jpaRepository.findReconcileTargets(type, maxAttempts, threshold);
    }

    @Override
    public List<Transaction> findStalePendingExchangeIntents(LocalDateTime threshold) {
        return jpaRepository.findStalePendingExchangeIntents(
                TransactionType.EXCHANGE, TransactionStatus.PENDING, threshold);
    }

    @Override
    public List<Transaction> findAbandonedTargets(TransactionType type, int maxAttempts) {
        return jpaRepository.findAbandonedTargets(
                type,
                List.of(TransactionStatus.PROCESSING, TransactionStatus.UNKNOWN),
                maxAttempts);
    }
}
