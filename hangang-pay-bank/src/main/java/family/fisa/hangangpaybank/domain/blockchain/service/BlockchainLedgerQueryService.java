package family.fisa.hangangpaybank.domain.blockchain.service;

import family.fisa.hangangpaybank.domain.blockchain.dto.response.BlockchainLedgerResponse;

/** blockchain_ledger 조회 포트. 현재 구현은 {@code v1.BlockchainLedgerQueryServiceV1}. */
public interface BlockchainLedgerQueryService {

    BlockchainLedgerResponse getByTxHash(String txHash);
}
