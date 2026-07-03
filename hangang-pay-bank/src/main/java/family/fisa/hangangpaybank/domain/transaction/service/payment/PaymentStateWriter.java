package family.fisa.hangangpaybank.domain.transaction.service.payment;

import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 결제/취소 흐름의 메인 트랜잭션 및 상태 전환 포트. 현재 구현은 {@code v1.PaymentStateWriterV1}.
 *
 * <p>payment/cancel이 공용으로 사용한다.
 */
public interface PaymentStateWriter {

    PaymentResponse executePayment(PaymentRequest request);

    CancelResponse executeCancel(CancelRequest request);

    Optional<WalletLedger> findExisting(String transactionUuid);

    LocalDateTime saveSuccessWalletLedgers(
            BankWallet fromWallet, BankWallet toWallet, String transactionUuid, BigDecimal amount);

    void saveFailedWalletLedgersByAddress(
            String fromWalletAddress,
            String toWalletAddress,
            String transactionUuid,
            BigDecimal amount);

    void throwDuplicateProcessing(String transactionUuid);

    void throwAlreadyFailed(String transactionUuid);
}
