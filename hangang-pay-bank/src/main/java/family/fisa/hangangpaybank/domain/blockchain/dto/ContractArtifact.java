package family.fisa.hangangpaybank.domain.blockchain.dto;

/** Hardhat artifact JSON에서 컨트랙트 배포에 필요한 bytecode만 담는 값 객체. */
public record ContractArtifact(String bytecode) {}
