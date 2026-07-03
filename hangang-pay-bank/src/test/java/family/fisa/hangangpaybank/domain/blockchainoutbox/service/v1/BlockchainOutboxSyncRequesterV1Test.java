package family.fisa.hangangpaybank.domain.blockchainoutbox.service.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequestResult;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.CancelBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.PaymentBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.BlockchainOutboxRepository;
import family.fisa.hangangpaybank.domain.blockchainoutbox.service.BlockchainOrderingSequenceAllocator;
import family.fisa.hangangpaybank.domain.blockchain.entity.Contract;
import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.blockchain.repository.ContractRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockchainOutboxSyncRequesterV1Test {

    @Mock private BlockchainLedgerRepository blockchainLedgerRepository;
    @Mock private BlockchainOutboxRepository blockchainOutboxRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private BlockchainOrderingSequenceAllocator sequenceAllocator;

    private BlockchainSyncRequester requester;

    @BeforeEach
    void setUp() {
        requester =
                new BlockchainOutboxSyncRequesterV1(
                        blockchainLedgerRepository,
                        blockchainOutboxRepository,
                        contractRepository,
                        new ObjectMapper(),
                        sequenceAllocator);
    }

    @Test
    @DisplayName("결제 payload로 request() 호출 시 PENDING ledger와 NEW outbox가 저장된다")
    void requestWithPaymentPayloadSavesPendingLedgerAndNewOutbox() {
        Institution institution = institution();
        stubLocalCurrencyOwner(institution);

        BlockchainLedger savedLedger = ledgerWithId(10L, institution);
        BlockchainOutbox savedOutbox = outboxWithId(20L);
        given(blockchainLedgerRepository.save(any())).willReturn(savedLedger);
        given(blockchainOutboxRepository.save(any())).willReturn(savedOutbox);
        given(sequenceAllocator.allocate("0xfrom")).willReturn(1L);

        BlockchainSyncRequest request =
                syncRequest(
                        BlockchainSyncType.PAYMENT,
                        "uuid-1",
                        new PaymentBlockchainPayload("0xFROM", "0xTO", new BigDecimal("100")));

        BlockchainSyncRequestResult result = requester.request(request);

        assertThat(result.blockchainLedgerId()).isEqualTo(10L);
        assertThat(result.outboxId()).isEqualTo(20L);

        ArgumentCaptor<BlockchainLedger> ledgerCaptor =
                ArgumentCaptor.forClass(BlockchainLedger.class);
        verify(blockchainLedgerRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getStatus()).isEqualTo(BlockchainTxStatus.PENDING);
        assertThat(ledgerCaptor.getValue().getIdempotentKey()).isEqualTo("uuid-1");

        ArgumentCaptor<BlockchainOutbox> outboxCaptor =
                ArgumentCaptor.forClass(BlockchainOutbox.class);
        verify(blockchainOutboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(BlockchainOutboxStatus.NEW);
        assertThat(outboxCaptor.getValue().getType()).isEqualTo(BlockchainSyncType.PAYMENT);
        assertThat(outboxCaptor.getValue().getTransactionUuid()).isEqualTo("uuid-1");
        assertThat(outboxCaptor.getValue().getOrderingKey()).isEqualTo("0xfrom");
        assertThat(outboxCaptor.getValue().getSeqNo()).isEqualTo(1L);
        assertThat(outboxCaptor.getValue().getRetryCount()).isZero();
    }

    @Test
    @DisplayName("취소 payload로 request() 호출 시 outbox payload에 originalTransactionUuid가 포함된다")
    void requestWithCancelPayloadIncludesOriginalTransactionUuid() throws Exception {
        Institution institution = institution();
        stubLocalCurrencyOwner(institution);
        given(blockchainLedgerRepository.save(any())).willReturn(ledgerWithId(11L, institution));
        given(blockchainOutboxRepository.save(any())).willReturn(outboxWithId(21L));
        given(sequenceAllocator.allocate("0xuser")).willReturn(2L);

        CancelBlockchainPayload cancelPayload =
                new CancelBlockchainPayload(
                        "orig-uuid", "0xMERCHANT", "0xUSER", new BigDecimal("50"));
        BlockchainSyncRequest request =
                syncRequest(BlockchainSyncType.CANCEL, "uuid-2", cancelPayload);

        requester.request(request);

        ArgumentCaptor<BlockchainOutbox> captor = ArgumentCaptor.forClass(BlockchainOutbox.class);
        verify(blockchainOutboxRepository).save(captor.capture());

        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("orig-uuid");
        assertThat(captor.getValue().getType()).isEqualTo(BlockchainSyncType.CANCEL);
    }

    @Test
    @DisplayName("LOCAL_CURRENCY 컨트랙트가 없으면 BusinessException을 던진다")
    void throwsWhenLocalCurrencyContractNotFound() {
        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                requester.request(
                                        syncRequest(
                                                BlockchainSyncType.PAYMENT,
                                                "uuid-3",
                                                new PaymentBlockchainPayload(
                                                        "0xA", "0xB", BigDecimal.ONE))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND);
    }

    @Test
    @DisplayName("incrementRetryOrFail - MAX_RETRY 미만이면 retryCount만 증가한다")
    void incrementRetryOrFailIncreasesRetryCountBelowMax() {
        BlockchainOutbox outbox = outboxWithStatus(BlockchainOutboxStatus.NEW);

        outbox.incrementRetryOrFail();

        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(BlockchainOutboxStatus.NEW);
    }

    @Test
    @DisplayName("incrementRetryOrFail - MAX_RETRY 도달 시 FAILED로 전환된다")
    void incrementRetryOrFailMarksFailedAtMaxRetry() {
        BlockchainOutbox outbox = outboxWithStatus(BlockchainOutboxStatus.NEW);

        for (int i = 0; i < 3; i++) {
            outbox.incrementRetryOrFail();
        }

        assertThat(outbox.getStatus()).isEqualTo(BlockchainOutboxStatus.FAILED);
    }

    @Test
    @DisplayName("findAllByStatus - NEW 상태만 조회하면 NEW 레코드만 반환된다")
    void findAllByStatusReturnsOnlyMatchingStatus() {
        BlockchainOutbox newOutbox = outboxWithStatus(BlockchainOutboxStatus.NEW);
        given(blockchainOutboxRepository.findAllByStatus(BlockchainOutboxStatus.NEW))
                .willReturn(java.util.List.of(newOutbox));

        var result = blockchainOutboxRepository.findAllByStatus(BlockchainOutboxStatus.NEW);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(BlockchainOutboxStatus.NEW);
    }

    private void stubLocalCurrencyOwner(Institution institution) {
        Contract contract =
                Contract.builder()
                        .name(ContractType.LOCAL_CURRENCY)
                        .address("0xCONTRACT")
                        .institution(institution)
                        .build();
        given(contractRepository.findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY))
                .willReturn(Optional.of(contract));
    }

    private static Institution institution() {
        return Institution.builder()
                .id(1L)
                .institutionCode("BoK")
                .institutionName("한국은행")
                .operatorWalletAddress("0xOWNER")
                .operatorEncryptedPrivateKey("key")
                .rpcEndpoint("http://localhost:8545")
                .build();
    }

    private static BlockchainLedger ledgerWithId(Long id, Institution institution) {
        return BlockchainLedger.builder()
                .id(id)
                .institution(institution)
                .status(BlockchainTxStatus.PENDING)
                .idempotentKey("uuid-1")
                .build();
    }

    private static BlockchainOutbox outboxWithId(Long id) {
        return BlockchainOutbox.builder()
                .id(id)
                .blockchainLedgerId(10L)
                .transactionUuid("uuid-1")
                .orderingKey("0xfrom")
                .seqNo(1L)
                .type(BlockchainSyncType.PAYMENT)
                .status(BlockchainOutboxStatus.NEW)
                .payload("{}")
                .retryCount(0)
                .build();
    }

    private static BlockchainOutbox outboxWithStatus(BlockchainOutboxStatus status) {
        return BlockchainOutbox.builder()
                .blockchainLedgerId(1L)
                .transactionUuid("uuid")
                .orderingKey("0xfrom")
                .seqNo(1L)
                .type(BlockchainSyncType.PAYMENT)
                .status(status)
                .payload("{}")
                .retryCount(0)
                .build();
    }

    private static BlockchainSyncRequest syncRequest(
            BlockchainSyncType type, String transactionUuid, Object payload) {
        return new BlockchainSyncRequest() {
            @Override
            public BlockchainSyncType type() {
                return type;
            }

            @Override
            public String transactionUuid() {
                return transactionUuid;
            }

            @Override
            public String orderingKey() {
                if (payload instanceof PaymentBlockchainPayload paymentPayload) {
                    return paymentPayload.fromWalletAddress();
                }
                if (payload instanceof CancelBlockchainPayload cancelPayload) {
                    return cancelPayload.toWalletAddress();
                }
                return "0xORDERING";
            }

            @Override
            public Object payload() {
                return payload;
            }
        };
    }
}
