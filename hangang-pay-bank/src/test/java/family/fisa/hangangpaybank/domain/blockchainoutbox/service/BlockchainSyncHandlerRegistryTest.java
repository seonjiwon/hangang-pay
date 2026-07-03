package family.fisa.hangangpaybank.domain.blockchainoutbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncHandler;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockchainSyncHandlerRegistryTest {

    @Test
    @DisplayName("등록된 타입에 대해 올바른 핸들러를 반환한다")
    void returnsHandlerForRegisteredType() {
        BlockchainSyncHandler paymentHandler = stubHandler(BlockchainSyncType.PAYMENT);
        BlockchainSyncHandlerRegistry registry =
                new BlockchainSyncHandlerRegistry(List.of(paymentHandler));

        BlockchainSyncHandler found = registry.get(BlockchainSyncType.PAYMENT);

        assertThat(found).isSameAs(paymentHandler);
    }

    @Test
    @DisplayName("여러 핸들러 중 타입에 맞는 핸들러만 반환한다")
    void returnsCorrectHandlerAmongMultiple() {
        BlockchainSyncHandler paymentHandler = stubHandler(BlockchainSyncType.PAYMENT);
        BlockchainSyncHandler cancelHandler = stubHandler(BlockchainSyncType.CANCEL);
        BlockchainSyncHandlerRegistry registry =
                new BlockchainSyncHandlerRegistry(List.of(paymentHandler, cancelHandler));

        assertThat(registry.get(BlockchainSyncType.PAYMENT)).isSameAs(paymentHandler);
        assertThat(registry.get(BlockchainSyncType.CANCEL)).isSameAs(cancelHandler);
    }

    @Test
    @DisplayName("등록되지 않은 타입 조회 시 BusinessException을 던진다")
    void throwsBusinessExceptionForUnregisteredType() {
        BlockchainSyncHandlerRegistry registry = new BlockchainSyncHandlerRegistry(List.of());

        assertThatThrownBy(() -> registry.get(BlockchainSyncType.PAYMENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getCode())
                                        .isEqualTo(
                                                BlockchainErrorCode
                                                        .BLOCKCHAIN_SYNC_HANDLER_NOT_FOUND));
    }

    @Test
    @DisplayName("핸들러가 없는 타입은 다른 타입이 등록돼 있어도 예외를 던진다")
    void throwsExceptionForMissingTypeEvenWhenOthersExist() {
        BlockchainSyncHandler paymentHandler = stubHandler(BlockchainSyncType.PAYMENT);
        BlockchainSyncHandlerRegistry registry =
                new BlockchainSyncHandlerRegistry(List.of(paymentHandler));

        assertThatThrownBy(() -> registry.get(BlockchainSyncType.CANCEL))
                .isInstanceOf(BusinessException.class);
    }

    private BlockchainSyncHandler stubHandler(BlockchainSyncType type) {
        return new BlockchainSyncHandler() {
            @Override
            public BlockchainSyncType type() {
                return type;
            }

            @Override
            public void handle(BlockchainSyncMessage message) {}
        };
    }
}
