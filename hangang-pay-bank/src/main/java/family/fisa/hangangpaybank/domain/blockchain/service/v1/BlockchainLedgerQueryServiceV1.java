package family.fisa.hangangpaybank.domain.blockchain.service.v1;

import family.fisa.hangangpaybank.domain.blockchain.service.BlockchainLedgerQueryService;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.response.BlockchainLedgerResponse;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockchainLedgerQueryServiceV1 implements BlockchainLedgerQueryService {

    private final BlockchainLedgerRepository blockchainLedgerRepository;

    public BlockchainLedgerResponse getByTxHash(String txHash) {
        // 1. 트랜잭션 해시로 블록체인 거래 조회
        BlockchainLedger ledger =
                blockchainLedgerRepository
                        .findByTxHash(txHash)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                BlockchainErrorCode.BLOCKCHAIN_LEDGER_NOT_FOUND));

        // 2. Response 반환
        return BlockchainLedgerResponse.from(ledger);
    }
}
