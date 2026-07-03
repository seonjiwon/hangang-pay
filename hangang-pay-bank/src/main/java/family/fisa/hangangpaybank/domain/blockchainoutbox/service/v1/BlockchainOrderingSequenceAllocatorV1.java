package family.fisa.hangangpaybank.domain.blockchainoutbox.service.v1;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOrderingState;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa.BlockchainOrderingStateJpaRepository;
import family.fisa.hangangpaybank.domain.blockchainoutbox.service.BlockchainOrderingSequenceAllocator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * ordering_key 단위로 seq_no를 원자적으로 발급한다.
 *
 * <p>같은 ordering_key(사용자 지갑 주소)에 대해 비관적 락을 걸고 nextSeqNo를 증가시켜, 동시 요청이 들어와도 seq_no가 중복·역전되지 않도록
 * 보장한다.
 */
@Component
@RequiredArgsConstructor
public class BlockchainOrderingSequenceAllocatorV1 implements BlockchainOrderingSequenceAllocator {

    private final BlockchainOrderingStateJpaRepository repository;

    /**
     * 주어진 ordering_key에 대한 다음 seq_no를 발급한다.
     *
     * <p>state row가 없으면 새로 생성(seq=1부터 시작)하고, 이미 있으면 비관적 락으로 조회한 뒤 nextSeqNo를 반환하고 1 증가시킨다.
     */
    @Override
    public Long allocate(String orderingKey) {
        return repository
                .findByOrderingKeyWithLock(orderingKey)
                .map(BlockchainOrderingState::issueNextSeqNo)
                .orElseGet(() -> createAndIssue(orderingKey));
    }

    /**
     * state row가 없을 때 신규 생성 후 seq=1을 발급한다.
     *
     * <p>동시 삽입 경합으로 unique 제약 위반이 발생하면 이미 삽입된 row를 락으로 재조회해 발급한다.
     */
    private Long createAndIssue(String orderingKey) {
        try {
            BlockchainOrderingState state = BlockchainOrderingState.start(orderingKey);
            Long seqNo = state.issueNextSeqNo();
            repository.saveAndFlush(state);
            return seqNo;
        } catch (DataIntegrityViolationException e) {
            return repository
                    .findByOrderingKeyWithLock(orderingKey)
                    .orElseThrow(() -> e)
                    .issueNextSeqNo();
        }
    }
}
