package family.fisa.hangangpay.domain.party.entity;

import family.fisa.hangangpay.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "party")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Party extends BaseEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 종류 (USER, MERCHANT) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartyType partyType;

    public static Party of(PartyType partyType) {
        return Party.builder().partyType(partyType).build();
    }
}
