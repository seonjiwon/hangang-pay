package family.fisa.hangangpaybank.domain.transaction.service.sync.v1;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncHandler;
import family.fisa.hangangpaybank.domain.transaction.service.sync.BlockchainSyncProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** PAYMENT 타입 블록체인 동기화 메시지 핸들러. */
@Component
@RequiredArgsConstructor
public class PaymentBlockchainSyncHandlerV1 implements BlockchainSyncHandler {

    private final BlockchainSyncProcessor processor;

    @Override
    public BlockchainSyncType type() {
        return BlockchainSyncType.PAYMENT;
    }

    @Override
    public void handle(BlockchainSyncMessage message) {
        processor.processPayment(message);
    }
}
