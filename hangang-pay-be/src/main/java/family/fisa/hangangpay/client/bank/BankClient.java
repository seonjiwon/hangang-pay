package family.fisa.hangangpay.client.bank;

import family.fisa.hangangpay.client.bank.dto.*;
import family.fisa.hangangpay.client.bank.dto.request.*;
import family.fisa.hangangpay.client.bank.dto.response.*;

public interface BankClient {

    // bank_account 관련
    BankAccountResponse createBankAccount(BankAccountCreateRequest request);

    BankAccountResponse getBankAccount(Long institutionId, String accountNumber);

    // bank_wallet 관련 (Custodial)
    BankWalletResponse createBankWallet(BankWalletCreateRequest request);

    BankWalletResponse getBankWalletByAddress(String address);

    // 거래 시, Bank 관련
    BankTransactionStatusResponse getTransactionStatus(String transactionUuid);

    // 거래
    ChargeResponse charge(ChargeRequest request);

    /** 환전 실행. 결과를 SUCCESS/FAILURE/UNKNOWN으로 분류해 반환한다. */
    BankActResult exchange(ExchangeRequest request);

    /** 환전 상태 조회. SUCCESS/FAILED/PENDING/NOT_FOUND reconcile에서 사용 */
    BankExchangeStatus getStatus(String transactionUuid);

    PaymentResponse payment(PaymentRequest request);

    CancelResponse cancel(CancelRequest request);

    // 로컬 seed 데이터 온체인 동기화
    void localMint(Long institutionId, String walletAddress, java.math.BigDecimal amount);
}
