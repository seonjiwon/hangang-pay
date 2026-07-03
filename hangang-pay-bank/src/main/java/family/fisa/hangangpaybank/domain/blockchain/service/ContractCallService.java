package family.fisa.hangangpaybank.domain.blockchain.service;

import family.fisa.hangangpaybank.domain.blockchain.dto.SubmittedBlockchainTx;
import java.math.BigInteger;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * 컨트랙트 호출 포트 (Web3j). 현재 구현은 {@code v1.ContractCallServiceV1}.
 *
 * <p>외부(다른 도메인 서비스)가 호출하는 메서드만 노출한다. pay/refund/getBalance 등 내부·테스트 전용 메서드는 구현체에만 둔다.
 */
public interface ContractCallService {

    TransactionReceipt charge(Long institutionId, String userAddress, BigInteger amount);

    boolean isMerchant(String walletAddress);

    TransactionReceipt setMerchant(String merchantAddress);

    SubmittedBlockchainTx submitPayment(
            String transactionUuid, String from, String to, BigInteger amount);

    SubmittedBlockchainTx submitCancelPayment(
            String transactionUuid, String from, String to, BigInteger amount);

    SubmittedBlockchainTx submitRefund(Long institutionId, String userAddress, BigInteger amount);

    TransactionReceipt waitForReceiptByHash(String txHash);
}
