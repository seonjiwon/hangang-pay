package family.fisa.hangangpay.domain.account.entity;

import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유자 (party.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    /** 은행 (institution.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    /** 계좌 종류 (PRIMARY, SECONDARY, SETTLEMENT) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    /** 계좌번호 */
    @Column(nullable = false)
    private String accountNumber;

    /** 계좌 유형 변경 메서드, JPA 변경 감지로 자동 반영 */
    public void updateAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    /** 은행(기관) 및 계좌번호 변경 메서드, JPA 변경 감지로 자동 반영 */
    public void updateBankAccount(Institution institution, String accountNumber) {
        this.institution = institution;
        this.accountNumber = accountNumber;
    }
}
