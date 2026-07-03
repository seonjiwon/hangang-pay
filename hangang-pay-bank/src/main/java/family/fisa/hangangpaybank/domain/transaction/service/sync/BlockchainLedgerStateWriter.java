package family.fisa.hangangpaybank.domain.transaction.service.sync;

import org.web3j.protocol.core.methods.response.TransactionReceipt;

/** 비동기 컨슈머 경로의 blockchain_ledger 상태 전환 포트. 현재 구현은 {@code v1.BlockchainLedgerStateWriterV1}. */
public interface BlockchainLedgerStateWriter {

    void markSubmitted(Long ledgerId, String transactionUuid, String txHash);

    void markSuccess(Long ledgerId, String transactionUuid, TransactionReceipt receipt);

    void markAlreadyProcessed(Long ledgerId, String transactionUuid);

    void markFailed(Long ledgerId, String transactionUuid);
}
