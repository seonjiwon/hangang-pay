package family.fisa.hangangpaybank.domain.blockchainoutbox.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOrderingState;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa.BlockchainOrderingStateJpaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainOrderingSequenceAllocatorV1Test {

    @Mock private BlockchainOrderingStateJpaRepository repository;

    private BlockchainOrderingSequenceAllocatorV1 allocator;

    @BeforeEach
    void setUp() {
        allocator = new BlockchainOrderingSequenceAllocatorV1(repository);
    }

    @Test
    @DisplayName("ordering state가 없으면 새로 생성하고 seq=1을 발급한다")
    void allocateCreatesNewStateAndReturnsSeqOne() {
        given(repository.findByOrderingKeyWithLock("0xuser")).willReturn(Optional.empty());
        BlockchainOrderingState created = BlockchainOrderingState.start("0xuser");
        given(repository.saveAndFlush(any())).willReturn(created);

        Long seq = allocator.allocate("0xuser");

        assertThat(seq).isEqualTo(1L);
        ArgumentCaptor<BlockchainOrderingState> captor =
                ArgumentCaptor.forClass(BlockchainOrderingState.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOrderingKey()).isEqualTo("0xuser");
        assertThat(captor.getValue().getNextSeqNo()).isEqualTo(2L);
    }

    @Test
    @DisplayName("ordering state가 이미 있으면 nextSeqNo를 발급하고 증가시킨다")
    void allocateReturnsNextSeqNoFromExistingState() {
        BlockchainOrderingState existing =
                BlockchainOrderingState.builder()
                        .orderingKey("0xuser")
                        .nextSeqNo(5L)
                        .lastCompletedSeqNo(4L)
                        .build();
        given(repository.findByOrderingKeyWithLock("0xuser")).willReturn(Optional.of(existing));

        Long seq = allocator.allocate("0xuser");

        assertThat(seq).isEqualTo(5L);
        assertThat(existing.getNextSeqNo()).isEqualTo(6L);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 orderingKey로 여러 번 allocate하면 seq가 순서대로 증가한다")
    void allocateIncrementsSeqNoOnEachCall() {
        BlockchainOrderingState state = BlockchainOrderingState.start("0xuser");
        given(repository.findByOrderingKeyWithLock("0xuser")).willReturn(Optional.of(state));

        Long seq1 = allocator.allocate("0xuser");
        Long seq2 = allocator.allocate("0xuser");
        Long seq3 = allocator.allocate("0xuser");

        assertThat(seq1).isEqualTo(1L);
        assertThat(seq2).isEqualTo(2L);
        assertThat(seq3).isEqualTo(3L);
    }
}
