package family.fisa.hangangpaybank.domain.blockchainoutbox.service;

/**
 * ordering_key 단위로 seq_no를 원자적으로 발급하는 포트. 현재 구현은 {@code v1.BlockchainOrderingSequenceAllocatorV1}.
 */
public interface BlockchainOrderingSequenceAllocator {

    /** 주어진 ordering_key에 대한 다음 seq_no를 발급한다. */
    Long allocate(String orderingKey);
}
