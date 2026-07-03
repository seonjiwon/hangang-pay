package family.fisa.hangangpaybank.domain.transaction.service.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.BlockchainOutboxRepository;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.transaction.service.sync.BlockchainLedgerStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class BlockchainLedgerReconcileSchedulerTest {

    @Mock private BlockchainLedgerRepository blockchainLedgerRepository;
    @Mock private BlockchainOutboxRepository blockchainOutboxRepository;
    @Mock private ContractCallService contractCallService;
    @Mock private BlockchainLedgerStateWriter ledgerStateWriter;
    @Mock private TransactionReceipt receipt;

    private BlockchainLedgerReconcileScheduler scheduler;
    private final LocalDateTime cutoff = LocalDateTime.of(2026, 6, 9, 12, 0);

    @BeforeEach
    void setUp() {
        scheduler =
                new BlockchainLedgerReconcileScheduler(
                        blockchainLedgerRepository,
                        blockchainOutboxRepository,
                        contractCallService,
                        ledgerStateWriter,
                        10,
                        50);
    }

    @Test
    @DisplayName("오래된 FAILED outbox를 NEW로 되돌려 publisher 재발행 대상으로 만든다")
    void reopensFailedOutbox() {
        BlockchainOutbox outbox = outbox(10L, 1L, BlockchainOutboxStatus.FAILED);
        given(
                        blockchainOutboxRepository.findStaleByStatus(
                                BlockchainOutboxStatus.FAILED, cutoff, 50))
                .willReturn(List.of(outbox));
        givenEmptyLedgerQueries();

        scheduler.reconcileOnce(cutoff, 50);

        verify(blockchainOutboxRepository).save(outbox);
    }

    @Test
    @DisplayName("오래된 PENDING ledger에 txHash가 없으면 연결된 outbox를 NEW로 되돌린다")
    void reopensOutboxForPendingLedgerWithoutTxHash() {
        BlockchainLedger ledger = ledger(1L, BlockchainTxStatus.PENDING, null);
        BlockchainOutbox outbox = outbox(11L, 1L, BlockchainOutboxStatus.SENT);
        givenEmptyFailedOutboxQuery();
        given(blockchainLedgerRepository.findStaleByStatus(BlockchainTxStatus.PENDING, cutoff, 50))
                .willReturn(List.of(ledger));
        given(blockchainOutboxRepository.findByBlockchainLedgerId(1L))
                .willReturn(Optional.of(outbox));
        givenEmptySubmittedAndFailedLedgerQueries();

        scheduler.reconcileOnce(cutoff, 50);

        verify(blockchainOutboxRepository).save(outbox);
        verify(contractCallService, never()).waitForReceiptByHash(any());
    }

    @Test
    @DisplayName("오래된 PENDING ledger에 txHash가 있고 성공 receipt면 SUCCESS로 마킹한다")
    void marksSuccessForPendingLedgerWithTxHash() {
        BlockchainLedger ledger = ledger(2L, BlockchainTxStatus.PENDING, "0xHASH");
        givenEmptyFailedOutboxQuery();
        given(blockchainLedgerRepository.findStaleByStatus(BlockchainTxStatus.PENDING, cutoff, 50))
                .willReturn(List.of(ledger));
        given(contractCallService.waitForReceiptByHash("0xHASH")).willReturn(receipt);
        given(receipt.isStatusOK()).willReturn(true);
        givenEmptySubmittedAndFailedLedgerQueries();

        scheduler.reconcileOnce(cutoff, 50);

        verify(ledgerStateWriter).markSuccess(2L, "uuid-2", receipt);
    }

    @Test
    @DisplayName("오래된 PENDING ledger에 txHash가 있고 실패 receipt면 FAILED로 마킹한다")
    void marksFailedForPendingLedgerWithTxHash() {
        BlockchainLedger ledger = ledger(2L, BlockchainTxStatus.PENDING, "0xHASH");
        givenEmptyFailedOutboxQuery();
        given(blockchainLedgerRepository.findStaleByStatus(BlockchainTxStatus.PENDING, cutoff, 50))
                .willReturn(List.of(ledger));
        given(contractCallService.waitForReceiptByHash("0xHASH")).willReturn(receipt);
        given(receipt.isStatusOK()).willReturn(false);
        givenEmptySubmittedAndFailedLedgerQueries();

        scheduler.reconcileOnce(cutoff, 50);

        verify(ledgerStateWriter).markFailed(2L, "uuid-2");
    }

    @Test
    @DisplayName("오래된 SUBMITTED ledger는 txHash로 receipt를 재조회하고 성공이면 SUCCESS로 마킹한다")
    void marksSuccessForSubmittedLedger() {
        BlockchainLedger ledger = ledger(3L, BlockchainTxStatus.SUBMITTED, "0xSUBMITTED");
        givenEmptyFailedOutboxQuery();
        givenEmptyPendingLedgerQuery();
        given(
                        blockchainLedgerRepository.findStaleByStatus(
                                BlockchainTxStatus.SUBMITTED, cutoff, 50))
                .willReturn(List.of(ledger));
        given(contractCallService.waitForReceiptByHash("0xSUBMITTED")).willReturn(receipt);
        given(receipt.isStatusOK()).willReturn(true);
        givenEmptyFailedLedgerQuery();

        scheduler.reconcileOnce(cutoff, 50);

        verify(ledgerStateWriter).markSuccess(3L, "uuid-3", receipt);
    }

    @Test
    @DisplayName("txHash가 있는 FAILED ledger는 성공 receipt로 복구될 수 있도록 재조회한다")
    void rechecksFailedLedgerWithTxHash() {
        BlockchainLedger ledger = ledger(4L, BlockchainTxStatus.FAILED, "0xFAILED");
        givenEmptyFailedOutboxQuery();
        givenEmptyPendingLedgerQuery();
        givenEmptySubmittedLedgerQuery();
        given(
                        blockchainLedgerRepository.findStaleWithTxHashByStatus(
                                BlockchainTxStatus.FAILED, cutoff, 50))
                .willReturn(List.of(ledger));
        given(contractCallService.waitForReceiptByHash("0xFAILED")).willReturn(receipt);
        given(receipt.isStatusOK()).willReturn(true);

        scheduler.reconcileOnce(cutoff, 50);

        verify(ledgerStateWriter).markSuccess(4L, "uuid-4", receipt);
    }

    @Test
    @DisplayName("receipt 조회가 timeout이면 상태를 바꾸지 않고 다음 주기에 맡긴다")
    void leavesLedgerWhenReceiptLookupTimesOut() {
        BlockchainLedger ledger = ledger(5L, BlockchainTxStatus.SUBMITTED, "0xTIMEOUT");
        givenEmptyFailedOutboxQuery();
        givenEmptyPendingLedgerQuery();
        given(
                        blockchainLedgerRepository.findStaleByStatus(
                                BlockchainTxStatus.SUBMITTED, cutoff, 50))
                .willReturn(List.of(ledger));
        given(contractCallService.waitForReceiptByHash("0xTIMEOUT"))
                .willThrow(new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RECEIPT_TIMEOUT));
        givenEmptyFailedLedgerQuery();

        scheduler.reconcileOnce(cutoff, 50);

        verify(ledgerStateWriter, never()).markSuccess(any(), any(), any());
        verify(ledgerStateWriter, never()).markFailed(any(), any());
    }

    private void givenEmptyFailedOutboxQuery() {
        given(
                        blockchainOutboxRepository.findStaleByStatus(
                                BlockchainOutboxStatus.FAILED, cutoff, 50))
                .willReturn(List.of());
    }

    private void givenEmptyLedgerQueries() {
        givenEmptyPendingLedgerQuery();
        givenEmptySubmittedLedgerQuery();
        givenEmptyFailedLedgerQuery();
    }

    private void givenEmptyPendingLedgerQuery() {
        given(blockchainLedgerRepository.findStaleByStatus(BlockchainTxStatus.PENDING, cutoff, 50))
                .willReturn(List.of());
    }

    private void givenEmptySubmittedLedgerQuery() {
        given(
                        blockchainLedgerRepository.findStaleByStatus(
                                BlockchainTxStatus.SUBMITTED, cutoff, 50))
                .willReturn(List.of());
    }

    private void givenEmptySubmittedAndFailedLedgerQueries() {
        givenEmptySubmittedLedgerQuery();
        givenEmptyFailedLedgerQuery();
    }

    private void givenEmptyFailedLedgerQuery() {
        given(
                        blockchainLedgerRepository.findStaleWithTxHashByStatus(
                                BlockchainTxStatus.FAILED, cutoff, 50))
                .willReturn(List.of());
    }

    private static BlockchainLedger ledger(Long id, BlockchainTxStatus status, String txHash) {
        return BlockchainLedger.builder()
                .id(id)
                .institution(Institution.builder().id(1L).build())
                .status(status)
                .idempotentKey("uuid-" + id)
                .txHash(txHash)
                .build();
    }

    private static BlockchainOutbox outbox(Long id, Long ledgerId, BlockchainOutboxStatus status) {
        return BlockchainOutbox.builder()
                .id(id)
                .blockchainLedgerId(ledgerId)
                .transactionUuid("uuid-" + ledgerId)
                .type(BlockchainSyncType.PAYMENT)
                .status(status)
                .payload("{}")
                .retryCount(3)
                .build();
    }
}
