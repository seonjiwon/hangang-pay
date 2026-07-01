package family.fisa.hangangpay.domain.transaction.entity;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.entity.BaseEntity;
import family.fisa.hangangpay.global.exception.BusinessException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 거래 통합 Entity. */
@Entity
@Table(name = "transaction")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseEntity {

    /** 사건 식별자 (물리적 PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 비즈니스 거래 식별자 (프론트 생성 멱등키) */
    @Column(name = "transaction_uuid", nullable = false, unique = true, length = 36)
    private String transactionUuid;

    /** CANCEL 전용 - 원본 PAYMENT의 transaction_uuid 참조 */
    @Column(name = "original_transaction_uuid", length = 36)
    private String originalTransactionUuid;

    /** 거래 종류 (CHARGE / EXCHANGE / PAYMENT / CANCEL) */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    /** 거래 상태 (PENDING / SUCCESS / FAILED) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /** 출발 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_party_id")
    private Party fromParty;

    /** 도착 사용자 (PAYMENT/CANCEL 전용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_party_id")
    private Party toParty;

    /** 출금 계좌 (CHARGE 전용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    /** 입금 계좌 (EXCHANGE 전용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    /** 출금 지갑 (EXCHANGE/PAYMENT/CANCEL) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id")
    private Wallet fromWallet;

    /** 입금 지갑 (CHARGE/PAYMENT/CANCEL) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id")
    private Wallet toWallet;

    /** 거래 금액 */
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 할인 금액 (CHARGE/EXCHANGE 전용) */
    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    /** 할인율 (CHARGE/EXCHANGE 전용) */
    @Column(name = "discount_rate", precision = 5, scale = 2)
    private BigDecimal discountRate;

    /** PAYMENT/CANCEL/EXCHANGE 전용 - 승인번호 (APV-YYYY-NNNNNNNN) */
    @Column(name = "approval_number", unique = true, length = 50)
    private String approvalNumber;

    /** PAYMENT 전용 - 상품명 */
    @Column(name = "item_name", length = 100)
    private String itemName;

    /** 블록체인 증거 (blockchain_ledger 매칭 키, FK 없음) */
    @Column(name = "tx_hash", length = 100)
    private String txHash;

    /**
     * 은행 거래 ID (PAYMENT/CANCEL: blockchain_ledger.id, CHARGE/EXCHANGE: account_ledger.id, FK 없음)
     */
    @Column(name = "bank_transaction_id", length = 100)
    private String bankTransactionId;

    /** reconcile 시도 횟수 - 임계값 도달 시 배치 대상에서 제외 */
    @Column(name = "reconcile_attempt_count", nullable = false)
    @Builder.Default
    private Integer reconcileAttemptCount = 0;

    /** CHARGE: 계좌 → 토큰 mint */
    public static Transaction forCharge(
            String transactionUuid,
            Party fromParty,
            Account fromAccount,
            Wallet toWallet,
            BigDecimal amount,
            BigDecimal discountAmount,
            BigDecimal discountRate) {
        return Transaction.builder()
                .transactionUuid(transactionUuid)
                .transactionType(TransactionType.CHARGE)
                .status(TransactionStatus.PENDING)
                .fromParty(fromParty)
                .fromAccount(fromAccount)
                .toWallet(toWallet)
                .amount(amount)
                .discountAmount(discountAmount)
                .discountRate(discountRate)
                .build();
    }

    /** EXCHANGE: 토큰 burn → 계좌 입금 */
    public static Transaction forExchange(
            String transactionUuid,
            Party fromParty,
            Wallet fromWallet,
            Account toAccount,
            BigDecimal amount,
            BigDecimal discountAmount,
            BigDecimal discountRate) {
        return Transaction.builder()
                .transactionUuid(transactionUuid)
                .transactionType(TransactionType.EXCHANGE)
                .status(TransactionStatus.PENDING)
                .fromParty(fromParty)
                .fromWallet(fromWallet)
                .toAccount(toAccount)
                .amount(amount)
                .discountAmount(discountAmount)
                .discountRate(discountRate)
                .build();
    }

    /** PAYMENT: 지갑 → 지갑 transfer */
    public static Transaction forPayment(
            String transactionUuid,
            Party fromParty,
            Party toParty,
            Wallet fromWallet,
            Wallet toWallet,
            BigDecimal amount,
            String approvalNumber,
            String itemName) {
        return Transaction.builder()
                .transactionUuid(transactionUuid)
                .transactionType(TransactionType.PAYMENT)
                .status(TransactionStatus.PENDING)
                .fromParty(fromParty)
                .toParty(toParty)
                .fromWallet(fromWallet)
                .toWallet(toWallet)
                .amount(amount)
                .approvalNumber(approvalNumber)
                .itemName(itemName)
                .build();
    }

    /** CANCEL: PAYMENT 역방향 transfer */
    public Transaction createCancel(String cancelUuid) {
        return Transaction.builder()
                .transactionUuid(cancelUuid)
                .originalTransactionUuid(this.transactionUuid)
                .transactionType(TransactionType.CANCEL)
                .status(TransactionStatus.PENDING)
                .fromParty(this.toParty) // toParty = 기존 가맹점
                .toParty(this.fromParty) // fromParty = 기존 소비자
                .fromWallet(this.toWallet) // 기존 가맹점
                .toWallet(this.fromWallet) // 기존 소비자
                .amount(this.amount)
                .approvalNumber(null) // save 이후 생성
                .build();
    }

    /** 트랜잭션 상태 전이 메서드 */
    public void markProcessing() {
        this.status = TransactionStatus.PROCESSING;
    }

    /** BankClient 응답을 반영해 성공 상태로 마무리 (JPA 변경감지) */
    public void markSuccess(String txHash, String bankTransactionId) {
        this.txHash = txHash;
        this.bankTransactionId = bankTransactionId;
        this.status = TransactionStatus.SUCCESS;
    }

    /** 거래 실패 마킹 (JPA 변경감지) */
    public void markFailed() {
        this.status = TransactionStatus.FAILED;
    }

    /** 네트워크 오류로 인한 확인 불가 상태 */
    public void markUnknown() {
        this.status = TransactionStatus.UNKNOWN;
    }

    /** intent가 TTL 내 실행되지 않아 만료됨 */
    public void markExpired() {
        this.status = TransactionStatus.EXPIRED;
    }

    /** Bank 조회 결과가 SUCCESS면 로컬 거래도 성공으로 확정한다. */
    public void recoverSuccess(String txHash, String bankTransactionId) {
        this.txHash = txHash;
        this.bankTransactionId = bankTransactionId;
        this.status = TransactionStatus.SUCCESS;
    }

    /** Bank 조회 결과가 FAILED면 로컬 거래도 실패로 확정한다. */
    public void recoverFailed() {
        this.status = TransactionStatus.FAILED;
    }

    /** reconcile 시도 횟수 1 증가 (JPA 변경감지) */
    public void incrementReconcileAttempt() {
        this.reconcileAttemptCount = this.reconcileAttemptCount + 1;
    }

    /** id 확보 후, 승인번호 세팅 - forCancel 팩토리에서는 id가 없으므로 별도 메서드 사용 */
    public void assignApprovalNumber(String approvalNumber) {
        this.approvalNumber = approvalNumber;
    }

    /** 결제 요청자가 거래 소유자인지 검증 */
    public void validateOwner(Long partyId) {
        if (!this.fromParty.getId().equals(partyId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }
    }

    /** 결제 실행 가능한 상태인지 검증 */
    public void validateExecutableStatus() {
        if (this.status == TransactionStatus.EXPIRED) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_INTENT_EXPIRED);
        }
        if (this.status != TransactionStatus.PENDING) {
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    /** UNKNOWN/PROCESSING 결제만 Bank 상태 조회로 복구할 수 있다. */
    public void validateRecoverableStatus() {
        if (this.status != TransactionStatus.UNKNOWN
                && this.status != TransactionStatus.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_NOT_RECOVERABLE);
        }
    }

    /** 취소 요청자가 원본 결제의 수신 가맹점인지 검증 */
    public void validateMerchantIsReceiver(Long merchantPartyId) {
        if (!this.toParty.getId().equals(merchantPartyId)) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);
        }
    }

    /** 취소 가능한 거래인지 검증 - PAYMENT +SUCCESS 조합에만 허용 */
    public void validateCancellable() {
        if (this.transactionType != TransactionType.PAYMENT
                || this.status != TransactionStatus.SUCCESS) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_NOT_CANCELLABLE);
        }
    }
}
