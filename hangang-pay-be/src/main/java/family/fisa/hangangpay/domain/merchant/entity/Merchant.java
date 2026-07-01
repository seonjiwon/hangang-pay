package family.fisa.hangangpay.domain.merchant.entity;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "merchant")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상위 Party (party.id) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false, unique = true)
    private Party party;

    /** 사용자 실명 */
    @Column(nullable = false, unique = true)
    private String username;

    /** 비밀번호 해시 */
    @Column(nullable = false)
    private String passwordHash;

    /** PIN 해시 */
    @Column(nullable = false)
    private String paymentPinHash;

    /** 사업자번호 */
    @Column(nullable = false, unique = true)
    private String businessNumber;

    /** 가맹점명 */
    @Column(nullable = false)
    private String merchantName;

    /** 대표자명 */
    @Column(nullable = false)
    private String ownerName;

    /** 연락처 */
    private String phoneNumber;

    /** 주소 */
    private String address;

    /** 위도 */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    /** 경도 */
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;
}
