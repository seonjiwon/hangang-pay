package family.fisa.hangangpaybank.domain.blockchain.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.request.ContractDeploymentRequest;
import family.fisa.hangangpaybank.domain.blockchain.dto.request.SaveContractDeploymentsRequest;
import family.fisa.hangangpaybank.domain.blockchain.dto.response.SaveContractDeploymentsResponse;
import family.fisa.hangangpaybank.domain.blockchain.entity.Contract;
import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.entity.InstitutionCode;
import family.fisa.hangangpaybank.domain.blockchain.repository.ContractRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractDeploymentCommandServiceV1Test {

    private static final String PROXY_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String UPDATED_PROXY_ADDRESS =
            "0x2234567890abcdef1234567890abcdef12345678";
    private static final String IMPLEMENTATION_ADDRESS =
            "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";

    @Mock private InstitutionRepository institutionRepository;
    @Mock private ContractRepository contractRepository;

    @InjectMocks private ContractDeploymentCommandServiceV1 contractDeploymentCommandService;

    @Test
    @DisplayName("Hardhat 배포 결과의 proxy 주소를 신규 컨트랙트 주소로 저장한다")
    void saveNewDeployment() {
        Institution centralBank = institution();
        Contract savedContract =
                Contract.builder()
                        .institution(centralBank)
                        .name(ContractType.CBDC)
                        .address(PROXY_ADDRESS)
                        .build();

        given(institutionRepository.findByInstitutionCode(InstitutionCode.BOK.getCode()))
                .willReturn(Optional.of(centralBank));
        given(
                        contractRepository.findByInstitutionInstitutionCodeAndName(
                                InstitutionCode.BOK.getCode(), ContractType.CBDC))
                .willReturn(Optional.empty());
        given(contractRepository.save(any(Contract.class))).willReturn(savedContract);

        SaveContractDeploymentsResponse response =
                contractDeploymentCommandService.save(
                        request(
                                new ContractDeploymentRequest(
                                        InstitutionCode.BOK.getCode(),
                                        ContractType.CBDC,
                                        PROXY_ADDRESS,
                                        IMPLEMENTATION_ADDRESS,
                                        null)));

        assertThat(response.contracts()).hasSize(1);
        assertThat(response.contracts().get(0).proxyAddress()).isEqualTo(PROXY_ADDRESS);
        assertThat(response.contracts().get(0).implementationAddress())
                .isEqualTo(IMPLEMENTATION_ADDRESS);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    @DisplayName("이미 저장된 기관/컨트랙트 조합은 proxy 주소를 갱신한다")
    void updateExistingDeployment() {
        Institution centralBank = institution();
        Contract existingContract =
                Contract.builder()
                        .institution(centralBank)
                        .name(ContractType.CBDC)
                        .address(PROXY_ADDRESS)
                        .build();

        given(institutionRepository.findByInstitutionCode(InstitutionCode.BOK.getCode()))
                .willReturn(Optional.of(centralBank));
        given(
                        contractRepository.findByInstitutionInstitutionCodeAndName(
                                InstitutionCode.BOK.getCode(), ContractType.CBDC))
                .willReturn(Optional.of(existingContract));

        SaveContractDeploymentsResponse response =
                contractDeploymentCommandService.save(
                        request(
                                new ContractDeploymentRequest(
                                        InstitutionCode.BOK.getCode(),
                                        ContractType.CBDC,
                                        UPDATED_PROXY_ADDRESS,
                                        IMPLEMENTATION_ADDRESS,
                                        null)));

        assertThat(existingContract.getAddress()).isEqualTo(UPDATED_PROXY_ADDRESS);
        assertThat(response.contracts().get(0).proxyAddress()).isEqualTo(UPDATED_PROXY_ADDRESS);
        verify(contractRepository, never()).save(any(Contract.class));
    }

    @Test
    @DisplayName("proxy 주소 형식이 올바르지 않으면 배포 결과 검증 예외를 던진다")
    void invalidProxyAddress() {
        assertThatThrownBy(
                        () ->
                                contractDeploymentCommandService.save(
                                        request(
                                                new ContractDeploymentRequest(
                                                        InstitutionCode.BOK.getCode(),
                                                        ContractType.CBDC,
                                                        "invalid",
                                                        IMPLEMENTATION_ADDRESS,
                                                        null))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_CONTRACT_DEPLOYMENT_RESULT_INVALID);

        verify(institutionRepository, never()).findByInstitutionCode(any());
    }

    private static SaveContractDeploymentsRequest request(ContractDeploymentRequest contract) {
        return new SaveContractDeploymentsRequest(
                "besu", 1337L, "2026-05-25T00:00:00.000Z", List.of(contract));
    }

    private static Institution institution() {
        return Institution.builder()
                .id(1L)
                .institutionCode(InstitutionCode.BOK.getCode())
                .institutionName("한국은행")
                .build();
    }
}
