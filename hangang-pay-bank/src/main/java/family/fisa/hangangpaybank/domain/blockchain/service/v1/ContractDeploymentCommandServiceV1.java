package family.fisa.hangangpaybank.domain.blockchain.service.v1;

import family.fisa.hangangpaybank.domain.blockchain.service.ContractDeploymentCommandService;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.request.ContractDeploymentRequest;
import family.fisa.hangangpaybank.domain.blockchain.dto.request.SaveContractDeploymentsRequest;
import family.fisa.hangangpaybank.domain.blockchain.dto.response.SaveContractDeploymentsResponse;
import family.fisa.hangangpaybank.domain.blockchain.dto.response.SavedContractDeploymentResponse;
import family.fisa.hangangpaybank.domain.institution.code.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.Contract;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.blockchain.repository.ContractRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractDeploymentCommandServiceV1 implements ContractDeploymentCommandService {

    private static final Pattern ETH_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final InstitutionRepository institutionRepository;
    private final ContractRepository contractRepository;

    /*
     * Hardhat deploy script가 전달한 컨트랙트 배포 결과를 저장한다.
     * 이미 존재하는 컨트랙트는 proxy 주소를 갱신하고,
     * 없으면 신규 생성한다.
     */
    public SaveContractDeploymentsResponse save(SaveContractDeploymentsRequest request) {
        validateRequest(request);

        List<SavedContractDeploymentResponse> contracts =
                request.contracts().stream().map(this::saveContract).toList();

        return new SaveContractDeploymentsResponse(contracts);
    }

    /*
     * 기관 및 컨트랙트 타입 기준으로 proxy 주소를 저장한다.
     * 기존 데이터가 존재하면 주소를 업데이트하고,
     * 없으면 새로운 Contract 엔티티를 생성한다.
     */
    private SavedContractDeploymentResponse saveContract(ContractDeploymentRequest request) {
        validateContract(request);

        Institution institution =
                institutionRepository
                        .findByInstitutionCode(request.institutionCode())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        Contract contract =
                contractRepository
                        .findByInstitutionInstitutionCodeAndName(
                                request.institutionCode(), request.contractType())
                        .map(
                                existing -> {
                                    existing.updateAddress(request.proxyAddress());
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        contractRepository.save(
                                                Contract.builder()
                                                        .institution(institution)
                                                        .name(request.contractType())
                                                        .address(request.proxyAddress())
                                                        .build()));

        return new SavedContractDeploymentResponse(
                institution.getId(),
                institution.getInstitutionCode(),
                institution.getInstitutionName(),
                contract.getName(),
                contract.getAddress(),
                request.implementationAddress());
    }

    /*
     * 배포 결과 요청값이 비어있는지 검증한다.
     */
    private static void validateRequest(SaveContractDeploymentsRequest request) {
        if (request == null || request.contracts() == null || request.contracts().isEmpty()) {
            throw new BusinessException(
                    BlockchainErrorCode.BLOCKCHAIN_CONTRACT_DEPLOYMENT_RESULT_INVALID);
        }
    }

    /*
     * 기관 코드, 컨트랙트 타입, proxy 주소 형식이 올바른지 검증한다.
     * implementation 주소는 선택값이며, 존재할 경우 Ethereum 주소 형식을 만족해야 한다.
     */
    private static void validateContract(ContractDeploymentRequest request) {
        if (request == null
                || isBlank(request.institutionCode())
                || request.contractType() == null
                || !isEthAddress(request.proxyAddress())
                || !isOptionalEthAddress(request.implementationAddress())) {
            throw new BusinessException(
                    BlockchainErrorCode.BLOCKCHAIN_CONTRACT_DEPLOYMENT_RESULT_INVALID);
        }
    }

    /*
     * implementation 주소가 비어있거나 Ethereum 주소 형식인지 확인한다.
     */
    private static boolean isOptionalEthAddress(String value) {
        return isBlank(value) || isEthAddress(value);
    }

    /*
     * Ethereum 주소 형식(0x + 40자리 hex)인지 확인한다.
     */
    private static boolean isEthAddress(String value) {
        return value != null && ETH_ADDRESS_PATTERN.matcher(value).matches();
    }

    /*
     * 문자열이 null 또는 공백인지 확인한다.
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
