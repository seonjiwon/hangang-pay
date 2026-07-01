package family.fisa.hangangpay.domain.user.entity;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상위 Party (party.id) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false, unique = true)
    private Party party;

    /** 사용자 실명 */
    @Column(nullable = false)
    private String username;

    /** 비밀번호 해시 */
    @Column(nullable = false)
    private String passwordHash;

    /** PIN 해시 */
    @Column(nullable = false)
    private String paymentPinHash;

    /** 휴대폰 번호 */
    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    /** 생년월일 */
    private LocalDate birthDate;

    /** 거주 지역 */
    private String region;
}
