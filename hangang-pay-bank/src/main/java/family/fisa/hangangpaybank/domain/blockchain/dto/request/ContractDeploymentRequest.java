package family.fisa.hangangpaybank.domain.blockchain.dto.request;

import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컨트랙트 단건 배포 결과")
public record ContractDeploymentRequest(
        @Schema(description = "기관 코드", example = "BoK") String institutionCode,
        @Schema(description = "컨트랙트 종류", example = "CBDC") ContractType contractType,
        @Schema(description = "프록시 컨트랙트 주소", example = "0x1234567890abcdef1234567890abcdef12345678")
                String proxyAddress,
        @Schema(description = "구현 컨트랙트 주소", example = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")
                String implementationAddress,
        @Schema(
                        description = "컨트랙트 owner 주소",
                        example = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")
                String ownerAddress) {}
