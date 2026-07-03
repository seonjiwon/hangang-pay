package family.fisa.hangangpaybank.domain.blockchainoutbox.service;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncHandler;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** BlockchainSyncHandler를 type별로 저장하기 위한 Registry */
@Component
public class BlockchainSyncHandlerRegistry {

    private final Map<BlockchainSyncType, BlockchainSyncHandler> handlers;

    public BlockchainSyncHandlerRegistry(List<BlockchainSyncHandler> handlerList) {
        handlers = new EnumMap<>(BlockchainSyncType.class);
        for (BlockchainSyncHandler handler : handlerList) {
            handlers.put(handler.type(), handler);
        }
    }

    public BlockchainSyncHandler get(BlockchainSyncType type) {
        BlockchainSyncHandler handler = handlers.get(type);
        if (handler == null) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_SYNC_HANDLER_NOT_FOUND);
        }
        return handler;
    }
}
