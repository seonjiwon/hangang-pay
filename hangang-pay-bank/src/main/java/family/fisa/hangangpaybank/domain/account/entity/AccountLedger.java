package family.fisa.hangangpaybank.domain.account.entity;

import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AccountLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 플랫폼이 발행한 거래 식별자
    @Column(name = "idempotent_key", nullable = false, unique = true, length = 36)
    private String idempotentKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_type", nullable = false, length = 20)
    private LedgerType ledgerType;

    // 원장 처리 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LedgerStatus status;

    @Column(name = "amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 20, scale = 4)
    private BigDecimal balanceAfter;
}
