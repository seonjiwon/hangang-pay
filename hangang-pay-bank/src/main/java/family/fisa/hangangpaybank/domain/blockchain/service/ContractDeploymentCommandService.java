package family.fisa.hangangpaybank.domain.blockchain.service;

import family.fisa.hangangpaybank.domain.blockchain.dto.request.SaveContractDeploymentsRequest;
import family.fisa.hangangpaybank.domain.blockchain.dto.response.SaveContractDeploymentsResponse;

/** 컨트랙트 배포 주소 저장 포트. 현재 구현은 {@code v1.ContractDeploymentCommandServiceV1}. */
public interface ContractDeploymentCommandService {

    SaveContractDeploymentsResponse save(SaveContractDeploymentsRequest request);
}
