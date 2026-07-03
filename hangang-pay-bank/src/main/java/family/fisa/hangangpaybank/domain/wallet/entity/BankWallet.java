package family.fisa.hangangpaybank.domain.wallet.entity;

import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bank_wallet")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankWallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(nullable = false)
    private String walletAddress;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Builder.Default
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    public void updateBalance(BigDecimal newBalance) {
        this.balance = newBalance;
    }

    /** 잔액 증가. */
    public void increaseBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    /** 잔액 차감. */
    public void decreaseBalance(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }
}
