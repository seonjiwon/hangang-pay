package family.fisa.hangangpaybank.domain.transaction.service.sync;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;

/** 비동기 blockchain 동기화 메시지 처리 포트. 현재 구현은 {@code v1.BlockchainSyncProcessorV1}. */
public interface BlockchainSyncProcessor {

    void processPayment(BlockchainSyncMessage message);

    void processCancel(BlockchainSyncMessage message);

    void processExchange(BlockchainSyncMessage message);
}
