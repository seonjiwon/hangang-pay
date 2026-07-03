package family.fisa.hangangpaybank.domain.institution.entity;

import family.fisa.hangangpaybank.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "institution")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Institution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String institutionCode;

    @Column(nullable = false)
    private String institutionName;

    private String operatorWalletAddress;

    @Column(columnDefinition = "TEXT")
    private String operatorEncryptedPrivateKey;

    private String rpcEndpoint;
}
