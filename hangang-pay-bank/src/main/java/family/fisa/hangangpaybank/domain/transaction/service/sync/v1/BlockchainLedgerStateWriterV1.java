package family.fisa.hangangpaybank.domain.transaction.service.sync.v1;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.service.sync.BlockchainLedgerStateWriter;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * 비동기 컨슈머 경로에서 blockchain_ledger 상태를 전환하는 전용 서비스.
 *
 * <p>각 메서드는 REQUIRES_NEW로 독립 커밋된다. BlockchainSyncProcessor가 블록체인 I/O 사이에 체크포인트를 즉시 저장할 수 있도록 별도 빈으로
 * 분리 (self-invocation 프록시 우회 문제 방지).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainLedgerStateWriterV1 implements BlockchainLedgerStateWriter {

    private final BlockchainLedgerRepository blockchainLedgerRepository;

    /** 블록체인 submit 직후 txHash를 즉시 커밋. 프로세스 재시작 시 재제출 방지를 위한 체크포인트. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSubmitted(Long ledgerId, String transactionUuid, String txHash) {
        BlockchainLedger ledger = fetchById(ledgerId, transactionUuid);
        ledger.markSubmitted(txHash);
        log.info("[ledger] SUBMITTED. uuid={}, txHash={}", transactionUuid, txHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long ledgerId, String transactionUuid, TransactionReceipt receipt) {
        BlockchainLedger ledger = fetchById(ledgerId, transactionUuid);
        ledger.markSuccess(receipt);
        log.info(
                "[ledger] SUCCESS. uuid={}, txHash={}",
                transactionUuid,
                receipt.getTransactionHash());
    }

    /** 온체인에서 AlreadyProcessed 반환 시: 이전 tx가 이미 확정됐음을 의미하므로 SUCCESS로 마킹. txHash는 불명이므로 null 유지. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAlreadyProcessed(Long ledgerId, String transactionUuid) {
        BlockchainLedger ledger = fetchById(ledgerId, transactionUuid);
        ledger.markAlreadyProcessed();
        log.info("[ledger] SUCCESS(already-processed). uuid={}", transactionUuid);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long ledgerId, String transactionUuid) {
        BlockchainLedger ledger = fetchById(ledgerId, transactionUuid);
        ledger.markFailed();
        log.warn("[ledger] FAILED. uuid={}", transactionUuid);
    }

    private BlockchainLedger fetchById(Long ledgerId, String transactionUuid) {
        return blockchainLedgerRepository
                .findById(ledgerId)
                .orElseThrow(
                        () -> {
                            log.error(
                                    "[ledger] ledger 조회 실패. ledgerId={}, uuid={}",
                                    ledgerId,
                                    transactionUuid);
                            return new NoSuchElementException(
                                    "BlockchainLedger not found: id=" + ledgerId);
                        });
    }
}
