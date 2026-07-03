package family.fisa.hangangpaybank.domain.blockchain.dto.response;

import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장된 컨트랙트 배포 결과")
public record SavedContractDeploymentResponse(
        @Schema(description = "기관 ID", example = "1") Long institutionId,
        @Schema(description = "기관 코드", example = "BoK") String institutionCode,
        @Schema(description = "기관명", example = "한국은행") String institutionName,
        @Schema(description = "컨트랙트 종류", example = "CBDC") ContractType contractType,
        @Schema(
                        description = "DB에 저장된 프록시 컨트랙트 주소",
                        example = "0x1234567890abcdef1234567890abcdef12345678")
                String proxyAddress,
        @Schema(
                        description = "배포된 구현 컨트랙트 주소",
                        example = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")
                String implementationAddress) {}
