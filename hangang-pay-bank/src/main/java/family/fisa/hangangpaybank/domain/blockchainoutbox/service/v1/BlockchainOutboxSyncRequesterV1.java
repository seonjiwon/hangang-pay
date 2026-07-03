package family.fisa.hangangpaybank.domain.blockchainoutbox.service.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequestResult;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.BlockchainOutboxRepository;
import family.fisa.hangangpaybank.domain.blockchainoutbox.service.BlockchainOrderingSequenceAllocator;
import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.blockchain.repository.ContractRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BlockchainOutboxSyncRequesterV1 implements BlockchainSyncRequester {

    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final BlockchainOutboxRepository blockchainOutboxRepository;
    private final ContractRepository contractRepository;
    private final ObjectMapper objectMapper;
    private final BlockchainOrderingSequenceAllocator sequenceAllocator;

    /** 블록체인으로 요청을 전송하기 위한 준비 작업. 블록체인 원장과 outbox에 요청 기록을 남긴다. */
    @Override
    public BlockchainSyncRequestResult request(BlockchainSyncRequest request) {
        // LOCAL_CURRENCY 컨트랙트의 발행 기관을 찾는다.
        Institution institution = findLocalCurrencyOwner();
        String orderingKey = normalizeOrderingKey(request.orderingKey());
        Long seqNo = sequenceAllocator.allocate(orderingKey);

        // blockchain_ledger에 PENDING 상태의 거래를 기록한다.
        BlockchainLedger ledger =
                blockchainLedgerRepository.save(
                        BlockchainLedger.of(
                                institution,
                                BlockchainTxStatus.PENDING,
                                request.transactionUuid()));

        // blockchain_outbox에 NEW 상태의 메시지를 저장한다.
        BlockchainOutbox outbox =
                blockchainOutboxRepository.save(
                        BlockchainOutbox.builder()
                                .blockchainLedgerId(ledger.getId())
                                .transactionUuid(request.transactionUuid())
                                .orderingKey(orderingKey)
                                .seqNo(seqNo)
                                .type(request.type())
                                .status(BlockchainOutboxStatus.NEW)
                                .payload(serializePayload(request.payload()))
                                .retryCount(0)
                                .build());

        return new BlockchainSyncRequestResult(ledger.getId(), outbox.getId());
    }

    private Institution findLocalCurrencyOwner() {
        return contractRepository
                .findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND))
                .getInstitution();
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
    }

    private static String normalizeOrderingKey(String orderingKey) {
        if (orderingKey == null || orderingKey.isBlank()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_INVALID_ADDRESS);
        }
        String lower = orderingKey.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }
}
