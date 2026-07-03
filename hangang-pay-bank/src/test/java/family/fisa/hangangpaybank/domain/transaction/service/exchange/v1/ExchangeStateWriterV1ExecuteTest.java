package family.fisa.hangangpaybank.domain.transaction.service.exchange.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.account.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.account.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.account.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeStateWriterV1ExecuteTest {

    private static final String UUID = "11111111-1111-1111-1111-111111111111";
    private static final Long INSTITUTION_ID = 1L;
    private static final String WALLET_ADDRESS = "0x0000000000000000000000000000000000000001";
    private static final String ACCOUNT_NUMBER = "1002-123-456789";
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock private InstitutionRepository institutionRepository;
    @Mock private BankWalletRepository bankWalletRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private AccountLedgerRepository accountLedgerRepository;
    @Mock private BlockchainSyncRequester syncRequester;

    @InjectMocks private ExchangeStateWriterV1 service;

    @Test
    @DisplayName("환전 성공: 토큰 차감 + 현금 입금 + account_ledger 저장 + syncRequester 호출, status=SUCCESS")
    void executeExchange_success_debitsTokenCreditsCashAndRequestsSync() {
        BankWallet wallet = wallet(new BigDecimal("500"));
        BankAccount account = account(new BigDecimal("100000"));

        given(accountLedgerRepository.findByIdempotentKey(UUID)).willReturn(Optional.empty());
        given(institutionRepository.findById(INSTITUTION_ID))
                .willReturn(Optional.of(institution()));
        given(bankWalletRepository.findByWalletAddressWithLock(WALLET_ADDRESS))
                .willReturn(Optional.of(wallet));
        given(
                        bankAccountRepository.findByInstitution_IdAndAccountNumberWithLock(
                                INSTITUTION_ID, ACCOUNT_NUMBER))
                .willReturn(Optional.of(account));
        given(accountLedgerRepository.save(any(AccountLedger.class)))
                .willAnswer(inv -> inv.getArgument(0));

        ExchangeResponse response = service.executeExchange(request());

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.accountBalance()).isEqualByComparingTo(new BigDecimal("100100"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("400")); // 토큰 차감
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("100100")); // 현금 입금
        verify(syncRequester).request(any(BlockchainSyncRequest.class));
    }

    @Test
    @DisplayName("토큰 잔액 부족: 예외, 잔액 미변경, syncRequester 미호출")
    void executeExchange_insufficientToken_throwsAndKeepsBalances() {
        BankWallet wallet = wallet(new BigDecimal("50"));
        BankAccount account = account(new BigDecimal("100000"));

        given(accountLedgerRepository.findByIdempotentKey(UUID)).willReturn(Optional.empty());
        given(institutionRepository.findById(INSTITUTION_ID))
                .willReturn(Optional.of(institution()));
        given(bankWalletRepository.findByWalletAddressWithLock(WALLET_ADDRESS))
                .willReturn(Optional.of(wallet));
        given(
                        bankAccountRepository.findByInstitution_IdAndAccountNumberWithLock(
                                INSTITUTION_ID, ACCOUNT_NUMBER))
                .willReturn(Optional.of(account));

        assertThatThrownBy(() -> service.executeExchange(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("100000"));
        verify(syncRequester, never()).request(any());
    }

    @Test
    @DisplayName("SUCCESS 멱등 재요청: syncRequester 미호출, status=SUCCESS 기존 응답")
    void executeExchange_idempotentSuccess_returnsExistingWithoutSync() {
        given(accountLedgerRepository.findByIdempotentKey(UUID))
                .willReturn(
                        Optional.of(
                                AccountLedger.builder()
                                        .id(7L)
                                        .idempotentKey(UUID)
                                        .status(LedgerStatus.SUCCESS)
                                        .build()));
        given(
                        bankAccountRepository.findByInstitution_IdAndAccountNumber(
                                INSTITUTION_ID, ACCOUNT_NUMBER))
                .willReturn(Optional.of(account(new BigDecimal("100100"))));

        ExchangeResponse response = service.executeExchange(request());

        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(syncRequester, never()).request(any());
    }

    @Test
    @DisplayName("PENDING 멱등 재요청: TRANSACTION_DUPLICATE_PROCESSING")
    void executeExchange_idempotentPending_throwsDuplicate() {
        given(accountLedgerRepository.findByIdempotentKey(UUID))
                .willReturn(
                        Optional.of(
                                AccountLedger.builder()
                                        .idempotentKey(UUID)
                                        .status(LedgerStatus.PENDING)
                                        .build()));

        assertThatThrownBy(() -> service.executeExchange(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);

        verify(syncRequester, never()).request(any());
    }

    @Test
    @DisplayName("FAILED 멱등 재요청: TRANSACTION_ALREADY_FAILED")
    void executeExchange_idempotentFailed_throwsAlreadyFailed() {
        given(accountLedgerRepository.findByIdempotentKey(UUID))
                .willReturn(
                        Optional.of(
                                AccountLedger.builder()
                                        .idempotentKey(UUID)
                                        .status(LedgerStatus.FAILED)
                                        .build()));

        assertThatThrownBy(() -> service.executeExchange(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.TRANSACTION_ALREADY_FAILED);
    }

    private ExchangeRequest request() {
        return new ExchangeRequest(UUID, INSTITUTION_ID, WALLET_ADDRESS, ACCOUNT_NUMBER, AMOUNT);
    }

    private static Institution institution() {
        return Institution.builder().id(INSTITUTION_ID).build();
    }

    private static BankWallet wallet(BigDecimal balance) {
        BankWallet wallet =
                BankWallet.builder()
                        .id(1L)
                        .institution(institution())
                        .walletAddress(WALLET_ADDRESS)
                        .encryptedPrivateKey("encrypted")
                        .build();
        wallet.updateBalance(balance);
        return wallet;
    }

    private static BankAccount account(BigDecimal balance) {
        return BankAccount.builder()
                .id(1L)
                .institution(institution())
                .accountNumber(ACCOUNT_NUMBER)
                .ownerName("tester")
                .balance(balance)
                .build();
    }
}
