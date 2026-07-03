package family.fisa.hangangpaybank.domain.transaction.service.sync.v1;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.SubmittedBlockchainTx;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.CancelBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.ExchangeBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.PaymentBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.transaction.service.sync.BlockchainLedgerStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class BlockchainSyncProcessorV1Test {

    @Mock private BlockchainLedgerRepository blockchainLedgerRepository;
    @Mock private ContractCallService contractCallService;
    @Mock private BlockchainLedgerStateWriter ledgerStateWriter;
    @Mock private TransactionReceipt receipt;

    private BlockchainSyncProcessorV1 processor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        processor =
                new BlockchainSyncProcessorV1(
                        blockchainLedgerRepository,
                        contractCallService,
                        ledgerStateWriter,
                        objectMapper);
    }

    // ── PAYMENT ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "PAYMENT - txHash 없는 PENDING ledger: submitPayment 호출 → markSubmitted → receipt 결과 반영")
    void processPayment_pendingWithoutTxHash_submitsAndMarksSuccess() throws Exception {
        // given
        BlockchainSyncMessage message = paymentMessage("uuid-1", "0xFROM", "0xTO", "100");
        BlockchainLedger ledger = pendingLedger(1L, null);

        given(blockchainLedgerRepository.findById(1L)).willReturn(Optional.of(ledger));
        given(contractCallService.submitPayment(eq("uuid-1"), eq("0xFROM"), eq("0xTO"), any()))
                .willReturn(new SubmittedBlockchainTx("0xHASH"));
        given(contractCallService.waitForReceiptByHash("0xHASH")).willReturn(receipt);
        given(receipt.isStatusOK()).willReturn(true);

        // when
        processor.processPayment(message);

        // then
        verify(contractCallService).submitPayment(eq("uuid-1"), eq("0xFROM"), eq("0xTO"), any());
        verify(ledgerStateWriter).markSubmitted(1L, "uuid-1", "0xHASH");
        verify(ledgerStateWriter).markSuccess(1L, "uuid-1", receipt);
    }

    @Test
    @DisplayName("PAYMENT - txHash 있는 SUBMITTED ledger: submitPayment 재호출 없이 receipt만 조회")
    void processPayment_submittedWithTxHash_skipsSubmitAndChecksReceipt() throws Exception {
        // given
        BlockchainSyncMessage message = paymentMessage("uuid-2", "0xFROM", "0xTO", "100");
        BlockchainLedger ledger = pendingLedger(2L, "0xEXISTING_HASH");

        given(blockchainLedgerRepository.findById(2L)).willReturn(Optional.of(ledger));
        given(contractCallService.waitForReceiptByHash("0xEXISTING_HASH")).willReturn(receipt);
        given(receipt.isStatusOK()).willReturn(true);

        // when
        processor.processPayment(message);

        // then - submit 호출 없이 기존 txHash로 receipt 조회
        verify(contractCallService, never()).submitPayment(any(), any(), any(), any());
        verify(ledgerStateWriter, never()).markSubmitted(any(), any(), any());
        verify(ledgerStateWriter).markSuccess(2L, "uuid-2", receipt);
    }

    @Test
    @DisplayName("PAYMENT - 이미 SUCCESS 상태: 컨트랙트 호출 없이 즉시 반환 (멱등)")
    void processPayment_alreadySuccess_returnsWithoutContractCall() throws Exception {
        // given
        BlockchainSyncMessage message = paymentMessage("uuid-3", "0xFROM", "0xTO", "100");
        BlockchainLedger ledger = terminalLedger(3L, BlockchainTxStatus.SUCCESS);

        given(blockchainLedgerRepository.findById(3L)).willReturn(Optional.of(ledger));

        // when
        processor.processPayment(message);

        // then
        verify(contractCallService, never()).submitPayment(any(), any(), any(), any());
        verify(contractCallService, never()).waitForReceiptByHash(any());
    }

    @Test
    @DisplayName("PAYMENT - 이미 FAILED 상태: 컨트랙트 호출 없이 즉시 반환 (멱등)")
    void processPayment_alreadyFailed_returnsWithoutContractCall() throws Exception {
        // given
        BlockchainSyncMessage message = paymentMessage("uuid-4", "0xFROM", "0xTO", "100");
        BlockchainLedger ledger = terminalLedger(4L, BlockchainTxStatus.FAILED);

        given(blockchainLedgerRepository.findById(4L)).willReturn(Optional.of(ledger));

        // when
        processor.processPayment(message);

        // then
        verify(contractCallService, never()).submitPayment(any(), any(), any(), any());
    }

    // ── CANCEL ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "CANCEL - txHash 없는 PENDING ledger: submitCancelPayment 호출 → markSubmitted → receipt 결과 반영")
    void processCancel_pendingWithoutTxHash_submitsAndMarksSuccess() throws Exception {
        // given
        BlockchainSyncMessage message =
                cancelMessage("uuid-5", "orig-1", "0xMERCHANT", "0xUSER", "50");
        BlockchainLedger ledger = pendingLedger(5L, null);

        given(blockchainLedgerRepository.findById(5L)).willReturn(Optional.of(ledger));
        given(
                        contractCallService.submitCancelPayment(
                                eq("uuid-5"), eq("0xMERCHANT"), eq("0xUSER"), any()))
                .willReturn(new SubmittedBlockchainTx("0xCANCEL_HASH"));
        given(contractCallService.waitForReceiptByHash("0xCANCEL_HASH")).willReturn(receipt);
        given(receipt.isStatusOK()).willReturn(true);

        // when
        processor.processCancel(message);

        // then
        verify(contractCallService)
                .submitCancelPayment(eq("uuid-5"), eq("0xMERCHANT"), eq("0xUSER"), any());
        verify(ledgerStateWriter).markSubmitted(5L, "uuid-5", "0xCANCEL_HASH");
        verify(ledgerStateWriter).markSuccess(5L, "uuid-5", receipt);
    }

    // ── 오류 처리 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BLOCKCHAIN_RPC_FAILED: retryable → BusinessException 전파 (NACK 유도)")
    void processPayment_rpcFailed_rethrowsForNack() throws Exception {
        // given
        BlockchainSyncMessage message = paymentMessage("uuid-6", "0xFROM", "0xTO", "100");
        BlockchainLedger ledger = pendingLedger(6L, null);

        given(blockchainLedgerRepository.findById(6L)).willReturn(Optional.of(ledger));
        given(contractCallService.submitPayment(any(), any(), any(), any()))
                .willThrow(new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED));

        // when & then - 예외가 전파돼 Spring AMQP가 NACK 처리
        assertThatThrownBy(() -> processor.processPayment(message))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);

        verify(ledgerStateWriter, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("BLOCKCHAIN_RECEIPT_TIMEOUT: retryable → BusinessException 전파 (NACK 유도)")
    void processPayment_receiptTimeout_rethrowsForNack() throws Exception {
        // given
        BlockchainSyncMessage message = paymentMessage("uuid-7", "0xFROM", "0xTO", "100");
        BlockchainLedger ledger = pendingLedger(7L, null);

        given(blockchainLedgerRepository.findById(7L)).willReturn(Optional.of(ledger));
        given(contractCallService.submitPayment(any(), any(), any(), any()))
                .willReturn(new SubmittedBlockchainTx("0xHASH7"));
        given(contractCallService.waitForReceiptByHash("0xHASH7"))
                .willThrow(new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RECEIPT_TIMEOUT));

        // when & then
        assertThatThrownBy(() -> processor.processPayment(message))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_RECEIPT_TIMEOUT);

        verify(ledgerStateWriter).markSubmitted(7L, "uuid-7", "0xHASH7");
        verify(ledgerStateWriter, never()).markFailed(any(), any());
        verify(ledgerStateWriter, never()).markSuccess(any(), any(), any());
    }

    @Test
    @DisplayName("BLOCKCHAIN_MERCHANT_NOT_REGISTERED: non-retryable → markFailed 후 정상 반환 (ACK)")
    void processPayment_merchantNotRegistered_marksFailedAndAcks() throws Exception {
        // given
        BlockchainSyncMessage message = paymentMessage("uuid-8", "0xFROM", "0xTO", "100");
        BlockchainLedger ledger = pendingLedger(8L, null);

        given(blockchainLedgerRepository.findById(8L)).willReturn(Optional.of(ledger));
        given(contractCallService.submitPayment(any(), any(), any(), any()))
                .willThrow(
                        new BusinessException(
                                BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED));

        // when - 예외가 전파되지 않고 정상 반환 → ACK
        processor.processPayment(message);

        // then
        verify(ledgerStateWriter).markFailed(8L, "uuid-8");
    }

    // ── EXCHANGE ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "EXCHANGE - txHash 없는 PENDING ledger: submitRefund 호출 → markSubmitted → markSuccess")
    void processExchange_pendingWithoutTxHash_submitsAndMarksSuccess() throws Exception {
        // given
        BlockchainSyncMessage message = exchangeMessage(10L, "uuid-ex-1", 1L, "0xUSER", "100");
        BlockchainLedger ledger = pendingLedger(10L, null);

        given(blockchainLedgerRepository.findById(10L)).willReturn(Optional.of(ledger));
        given(contractCallService.submitRefund(eq(1L), eq("0xUSER"), any()))
                .willReturn(new SubmittedBlockchainTx("0xREFUND_HASH"));
        given(contractCallService.waitForReceiptByHash("0xREFUND_HASH")).willReturn(receipt);

        // when
        processor.processExchange(message);

        // then
        verify(contractCallService).submitRefund(eq(1L), eq("0xUSER"), any());
        verify(ledgerStateWriter).markSubmitted(10L, "uuid-ex-1", "0xREFUND_HASH");
        verify(ledgerStateWriter).markSuccess(eq(10L), eq("uuid-ex-1"), eq(receipt));
    }

    @Test
    @DisplayName("EXCHANGE - txHash 있는 SUBMITTED ledger: submitRefund 재호출 없이 receipt만 조회")
    void processExchange_submittedWithTxHash_skipsSubmit() throws Exception {
        // given
        BlockchainSyncMessage message = exchangeMessage(11L, "uuid-ex-2", 1L, "0xUSER", "100");
        BlockchainLedger ledger = pendingLedger(11L, "0xEXISTING_HASH");

        given(blockchainLedgerRepository.findById(11L)).willReturn(Optional.of(ledger));
        given(contractCallService.waitForReceiptByHash("0xEXISTING_HASH")).willReturn(receipt);

        // when
        processor.processExchange(message);

        // then - submit 호출 없이 기존 txHash로 receipt 조회
        verify(contractCallService, never()).submitRefund(any(), any(), any());
        verify(ledgerStateWriter, never()).markSubmitted(any(), any(), any());
        verify(ledgerStateWriter).markSuccess(eq(11L), eq("uuid-ex-2"), eq(receipt));
    }

    @Test
    @DisplayName("EXCHANGE - 이미 SUCCESS 상태: 컨트랙트 호출 없이 즉시 반환 (멱등)")
    void processExchange_alreadySuccess_returnsWithoutContractCall() throws Exception {
        // given
        BlockchainSyncMessage message = exchangeMessage(12L, "uuid-ex-3", 1L, "0xUSER", "100");
        BlockchainLedger ledger = terminalLedger(12L, BlockchainTxStatus.SUCCESS);

        given(blockchainLedgerRepository.findById(12L)).willReturn(Optional.of(ledger));

        // when
        processor.processExchange(message);

        // then
        verify(contractCallService, never()).submitRefund(any(), any(), any());
        verify(contractCallService, never()).waitForReceiptByHash(any());
    }

    @Test
    @DisplayName("EXCHANGE - INSUFFICIENT_TOKEN_BALANCE: non-retryable → markFailed 후 정상 반환 (ACK)")
    void processExchange_insufficientToken_marksFailed() throws Exception {
        // given
        BlockchainSyncMessage message = exchangeMessage(13L, "uuid-ex-4", 1L, "0xUSER", "100");
        BlockchainLedger ledger = pendingLedger(13L, null);

        given(blockchainLedgerRepository.findById(13L)).willReturn(Optional.of(ledger));
        given(contractCallService.submitRefund(any(), any(), any()))
                .willThrow(
                        new BusinessException(
                                BlockchainErrorCode.BLOCKCHAIN_INSUFFICIENT_TOKEN_BALANCE));

        // when - 예외 전파 없이 정상 반환 → ACK
        processor.processExchange(message);

        // then
        verify(ledgerStateWriter).markFailed(13L, "uuid-ex-4");
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private BlockchainSyncMessage paymentMessage(String uuid, String from, String to, String amount)
            throws Exception {
        PaymentBlockchainPayload payload =
                new PaymentBlockchainPayload(from, to, new BigDecimal(amount));
        return new BlockchainSyncMessage(
                "msg-" + uuid,
                1L,
                Long.parseLong(uuid.replaceAll("[^0-9]", "").substring(0, 1)),
                uuid,
                BlockchainSyncType.PAYMENT,
                from,
                objectMapper.valueToTree(payload));
    }

    private BlockchainSyncMessage cancelMessage(
            String uuid, String originalUuid, String from, String to, String amount)
            throws Exception {
        CancelBlockchainPayload payload =
                new CancelBlockchainPayload(originalUuid, from, to, new BigDecimal(amount));
        return new BlockchainSyncMessage(
                "msg-" + uuid,
                1L,
                Long.parseLong(uuid.replaceAll("[^0-9]", "").substring(0, 1)),
                uuid,
                BlockchainSyncType.CANCEL,
                to,
                objectMapper.valueToTree(payload));
    }

    private BlockchainSyncMessage exchangeMessage(
            Long ledgerId, String uuid, Long institutionId, String wallet, String amount) {
        ExchangeBlockchainPayload payload =
                new ExchangeBlockchainPayload(institutionId, wallet, new BigDecimal(amount));
        return new BlockchainSyncMessage(
                "msg-" + uuid,
                1L,
                ledgerId,
                uuid,
                BlockchainSyncType.EXCHANGE,
                wallet,
                objectMapper.valueToTree(payload));
    }

    private static BlockchainLedger pendingLedger(Long id, String txHash) {
        return BlockchainLedger.builder()
                .id(id)
                .institution(Institution.builder().id(1L).build())
                .status(BlockchainTxStatus.PENDING)
                .idempotentKey("uuid-" + id)
                .txHash(txHash)
                .build();
    }

    private static BlockchainLedger terminalLedger(Long id, BlockchainTxStatus status) {
        return BlockchainLedger.builder()
                .id(id)
                .institution(Institution.builder().id(1L).build())
                .status(status)
                .idempotentKey("uuid-" + id)
                .build();
    }
}
