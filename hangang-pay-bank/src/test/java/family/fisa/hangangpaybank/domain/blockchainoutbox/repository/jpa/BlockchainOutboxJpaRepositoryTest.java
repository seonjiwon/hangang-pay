package family.fisa.hangangpaybank.domain.blockchainoutbox.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class BlockchainOutboxJpaRepositoryTest {

    @Autowired private BlockchainOutboxJpaRepository repository;
    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("같은 orderingKey에 NEW가 여러 개 있어도 가장 작은 미완료 seq만 publish 후보로 조회한다")
    void findPublishableNewReturnsOnlyHeadSeqPerOrderingKey() {
        Institution institution = persistInstitution();
        BlockchainLedger ledger10 =
                persistLedger(institution, "uuid-10", BlockchainTxStatus.PENDING);
        BlockchainLedger ledger11 =
                persistLedger(institution, "uuid-11", BlockchainTxStatus.PENDING);
        BlockchainLedger ledger12 =
                persistLedger(institution, "uuid-12", BlockchainTxStatus.PENDING);
        persistOutbox(ledger10.getId(), "uuid-10", "0xuser", 10L);
        persistOutbox(ledger11.getId(), "uuid-11", "0xuser", 11L);
        persistOutbox(ledger12.getId(), "uuid-12", "0xuser", 12L);

        var result =
                repository.findPublishableNew(
                        BlockchainOutboxStatus.NEW,
                        BlockchainTxStatus.SUCCESS,
                        PageRequest.of(0, 10));

        assertThat(result).extracting(BlockchainOutbox::getSeqNo).containsExactly(10L);
    }

    @Test
    @DisplayName("선행 seq가 SUCCESS면 다음 NEW seq를 publish 후보로 조회한다")
    void findPublishableNewReturnsNextSeqAfterPreviousSuccess() {
        Institution institution = persistInstitution();
        BlockchainLedger ledger10 =
                persistLedger(institution, "uuid-10", BlockchainTxStatus.SUCCESS);
        BlockchainLedger ledger11 =
                persistLedger(institution, "uuid-11", BlockchainTxStatus.PENDING);
        // seq=10은 이미 MQ로 발행된 상태(SENT), ledger가 SUCCESS로 완료됨
        persistOutboxWithStatus(
                ledger10.getId(), "uuid-10", "0xuser", 10L, BlockchainOutboxStatus.SENT);
        persistOutbox(ledger11.getId(), "uuid-11", "0xuser", 11L);

        var result =
                repository.findPublishableNew(
                        BlockchainOutboxStatus.NEW,
                        BlockchainTxStatus.SUCCESS,
                        PageRequest.of(0, 10));

        assertThat(result).extracting(BlockchainOutbox::getSeqNo).containsExactly(11L);
    }

    @Test
    @DisplayName("선행 seq ledger가 FAILED이면 후행 seq는 publish 후보가 되지 않는다 (보수적 차단 정책)")
    void findPublishableNewBlocksNextSeqWhenPreviousLedgerFailed() {
        Institution institution = persistInstitution();
        BlockchainLedger ledger10 =
                persistLedger(institution, "uuid-10", BlockchainTxStatus.FAILED);
        BlockchainLedger ledger11 =
                persistLedger(institution, "uuid-11", BlockchainTxStatus.PENDING);
        persistOutboxWithStatus(
                ledger10.getId(), "uuid-10", "0xuser", 10L, BlockchainOutboxStatus.FAILED);
        persistOutbox(ledger11.getId(), "uuid-11", "0xuser", 11L);

        var result =
                repository.findPublishableNew(
                        BlockchainOutboxStatus.NEW,
                        BlockchainTxStatus.SUCCESS,
                        PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 orderingKey는 독립적으로 publish 후보를 가진다")
    void findPublishableNewSelectsHeadPerDistinctOrderingKey() {
        Institution institution = persistInstitution();
        BlockchainLedger ledgerA =
                persistLedger(institution, "uuid-a1", BlockchainTxStatus.PENDING);
        BlockchainLedger ledgerB =
                persistLedger(institution, "uuid-b1", BlockchainTxStatus.PENDING);
        persistOutbox(ledgerA.getId(), "uuid-a1", "0xuser-a", 1L);
        persistOutbox(ledgerB.getId(), "uuid-b1", "0xuser-b", 1L);

        var result =
                repository.findPublishableNew(
                        BlockchainOutboxStatus.NEW,
                        BlockchainTxStatus.SUCCESS,
                        PageRequest.of(0, 10));

        assertThat(result)
                .extracting(BlockchainOutbox::getTransactionUuid)
                .containsExactlyInAnyOrder("uuid-a1", "uuid-b1");
    }

    private Institution persistInstitution() {
        Institution institution =
                Institution.builder()
                        .institutionCode("BOK")
                        .institutionName("한국은행")
                        .operatorWalletAddress("0xOWNER")
                        .operatorEncryptedPrivateKey("key")
                        .rpcEndpoint("http://localhost:8545")
                        .build();
        return entityManager.persistAndFlush(institution);
    }

    private BlockchainLedger persistLedger(
            Institution institution, String idempotentKey, BlockchainTxStatus status) {
        BlockchainLedger ledger =
                BlockchainLedger.builder()
                        .institution(institution)
                        .idempotentKey(idempotentKey)
                        .status(status)
                        .build();
        return entityManager.persistAndFlush(ledger);
    }

    private BlockchainOutbox persistOutbox(
            Long ledgerId, String transactionUuid, String orderingKey, Long seqNo) {
        return persistOutboxWithStatus(
                ledgerId, transactionUuid, orderingKey, seqNo, BlockchainOutboxStatus.NEW);
    }

    private BlockchainOutbox persistOutboxWithStatus(
            Long ledgerId,
            String transactionUuid,
            String orderingKey,
            Long seqNo,
            BlockchainOutboxStatus status) {
        BlockchainOutbox outbox =
                BlockchainOutbox.builder()
                        .blockchainLedgerId(ledgerId)
                        .transactionUuid(transactionUuid)
                        .orderingKey(orderingKey)
                        .seqNo(seqNo)
                        .type(BlockchainSyncType.PAYMENT)
                        .status(status)
                        .payload("{}")
                        .retryCount(0)
                        .build();
        return entityManager.persistAndFlush(outbox);
    }
}
