package family.fisa.hangangpaybank.domain.wallet.entity;

import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "wallet_ledger",
        uniqueConstraints = @UniqueConstraint(columnNames = {"transaction_uuid", "bank_wallet_id"}))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_uuid", nullable = false, length = 36)
    private String transactionUuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_wallet_id", nullable = false)
    private BankWallet bankWallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WalletLedgerDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletLedgerStatus status;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    @Column private LocalDateTime confirmedAt;
}
