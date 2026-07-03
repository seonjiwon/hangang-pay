package family.fisa.hangangpaybank.domain.transaction.service.scheduler;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.BlockchainOutboxRepository;
import family.fisa.hangangpaybank.domain.transaction.service.sync.BlockchainLedgerStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Slf4j
@Component
public class BlockchainLedgerReconcileScheduler {

    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final BlockchainOutboxRepository blockchainOutboxRepository;
    private final ContractCallService contractCallService;
    private final BlockchainLedgerStateWriter ledgerStateWriter;
    private final long staleMinutes;
    private final int batchSize;

    public BlockchainLedgerReconcileScheduler(
            BlockchainLedgerRepository blockchainLedgerRepository,
            BlockchainOutboxRepository blockchainOutboxRepository,
            ContractCallService contractCallService,
            BlockchainLedgerStateWriter ledgerStateWriter,
            @Value("${blockchain.reconcile.stale-minutes:10}") long staleMinutes,
            @Value("${blockchain.reconcile.batch-size:50}") int batchSize) {
        this.blockchainLedgerRepository = blockchainLedgerRepository;
        this.blockchainOutboxRepository = blockchainOutboxRepository;
        this.contractCallService = contractCallService;
        this.ledgerStateWriter = ledgerStateWriter;
        this.staleMinutes = staleMinutes;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${blockchain.reconcile.delay-ms:60000}")
    public void reconcile() {
        reconcileOnce(LocalDateTime.now().minusMinutes(staleMinutes), batchSize);
    }

    void reconcileOnce(LocalDateTime updatedBefore, int limit) {
        reopenFailedOutboxes(updatedBefore, limit);
        reconcilePendingLedgers(updatedBefore, limit);
        reconcileSubmittedLedgers(updatedBefore, limit);
        reconcileFailedLedgersWithTxHash(updatedBefore, limit);
    }

    private void reopenFailedOutboxes(LocalDateTime updatedBefore, int limit) {
        for (BlockchainOutbox outbox :
                blockchainOutboxRepository.findStaleByStatus(
                        BlockchainOutboxStatus.FAILED, updatedBefore, limit)) {
            reopenOutbox(outbox, "FAILED outbox");
        }
    }

    private void reconcilePendingLedgers(LocalDateTime updatedBefore, int limit) {
        for (BlockchainLedger ledger :
                blockchainLedgerRepository.findStaleByStatus(
                        BlockchainTxStatus.PENDING, updatedBefore, limit)) {
            if (hasText(ledger.getTxHash())) {
                reconcileReceipt(ledger);
                continue;
            }
            reopenOutboxByLedger(ledger, "PENDING ledger without txHash");
        }
    }

    private void reconcileSubmittedLedgers(LocalDateTime updatedBefore, int limit) {
        for (BlockchainLedger ledger :
                blockchainLedgerRepository.findStaleByStatus(
                        BlockchainTxStatus.SUBMITTED, updatedBefore, limit)) {
            if (!hasText(ledger.getTxHash())) {
                log.error(
                        "[reconcile] SUBMITTED ledger has no txHash. ledgerId={}, uuid={}",
                        ledger.getId(),
                        ledger.getIdempotentKey());
                continue;
            }
            reconcileReceipt(ledger);
        }
    }

    private void reconcileFailedLedgersWithTxHash(LocalDateTime updatedBefore, int limit) {
        for (BlockchainLedger ledger :
                blockchainLedgerRepository.findStaleWithTxHashByStatus(
                        BlockchainTxStatus.FAILED, updatedBefore, limit)) {
            reconcileReceipt(ledger);
        }
    }

    private void reconcileReceipt(BlockchainLedger ledger) {
        try {
            TransactionReceipt receipt =
                    contractCallService.waitForReceiptByHash(ledger.getTxHash());
            if (receipt.isStatusOK()) {
                ledgerStateWriter.markSuccess(ledger.getId(), ledger.getIdempotentKey(), receipt);
            } else {
                ledgerStateWriter.markFailed(ledger.getId(), ledger.getIdempotentKey());
            }
            log.info(
                    "[reconcile] receipt reconciled. ledgerId={}, uuid={}, txHash={}",
                    ledger.getId(),
                    ledger.getIdempotentKey(),
                    ledger.getTxHash());
        } catch (BusinessException e) {
            log.warn(
                    "[reconcile] receipt 조회 실패. 다음 주기에 재시도. ledgerId={}, uuid={}, txHash={}, code={}",
                    ledger.getId(),
                    ledger.getIdempotentKey(),
                    ledger.getTxHash(),
                    e.getCode());
        }
    }

    private void reopenOutboxByLedger(BlockchainLedger ledger, String reason) {
        blockchainOutboxRepository
                .findByBlockchainLedgerId(ledger.getId())
                .ifPresentOrElse(
                        outbox -> reopenOutbox(outbox, reason),
                        () ->
                                log.error(
                                        "[reconcile] outbox 없음. 수동 확인 필요. ledgerId={}, uuid={}, reason={}",
                                        ledger.getId(),
                                        ledger.getIdempotentKey(),
                                        reason));
    }

    private void reopenOutbox(BlockchainOutbox outbox, String reason) {
        if (outbox.getStatus() == BlockchainOutboxStatus.NEW) {
            return;
        }
        outbox.reopenForReconcile();
        blockchainOutboxRepository.save(outbox);
        log.info(
                "[reconcile] outbox 재발행 대기 전환. outboxId={}, ledgerId={}, uuid={}, reason={}",
                outbox.getId(),
                outbox.getBlockchainLedgerId(),
                outbox.getTransactionUuid(),
                reason);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
