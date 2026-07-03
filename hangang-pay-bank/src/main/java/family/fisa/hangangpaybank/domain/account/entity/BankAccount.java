package family.fisa.hangangpaybank.domain.account.entity;

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
@Table(name = "bank_account")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(nullable = false)
    private String accountNumber;

    @Column(length = 50)
    private String ownerName;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance;

    /** 잔액 변경 메서드, JPA 변경 감지로 자동 반영 */
    public void updateBalance(BigDecimal balance) {
        this.balance = balance;
    }

    /** 잔액 증가 */
    public void increaseBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
