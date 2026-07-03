package family.fisa.hangangpaybank.domain.transaction.service.payment.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequestResult;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.wallet.repository.WalletLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentStateWriterV1Test {

    private static final String FROM_ADDRESS = "0x000000000000000000000000000000000000aaaa";
    private static final String TO_ADDRESS = "0x000000000000000000000000000000000000bbbb";
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock private BankWalletRepository bankWalletRepository;
    @Mock private WalletLedgerRepository walletLedgerRepository;
    @Mock private BlockchainSyncRequester syncRequester;
    @Mock private ContractCallService contractCallService;

    private PaymentStateWriterV1 service;

    @BeforeEach
    void setUp() {
        service =
                new PaymentStateWriterV1(
                        bankWalletRepository,
                        walletLedgerRepository,
                        syncRequester,
                        contractCallService);
    }

    @Test
    @DisplayName("결제 성공: DB 잔액 차감/증가, WalletLedger(SUCCESS), syncRequester 호출")
    void executePayment_success_updatesDbBalancesAndRequestsSync() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(walletLedgerRepository.findFirstByTransactionUuid("uuid-1"))
                .willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        PaymentResponse response = service.executePayment(paymentRequest("uuid-1"));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("400"));
        assertThat(to.getBalance()).isEqualByComparingTo(new BigDecimal("100"));
        verify(syncRequester).request(any(BlockchainSyncRequest.class));
    }

    @Test
    @DisplayName("결제 성공: 동기 체인 submit 미호출 확인 (DB-First, outbox 비동기)")
    void executePayment_success_doesNotSubmitSynchronously() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(walletLedgerRepository.findFirstByTransactionUuid("uuid-2"))
                .willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        service.executePayment(paymentRequest("uuid-2"));

        verify(contractCallService, never()).submitPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("비가맹점 수신자: 비즈니스 예외 발생, 잔액 미변경")
    void executePayment_nonMerchant_throwsAndKeepsBalance() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));

        given(walletLedgerRepository.findFirstByTransactionUuid("uuid-3"))
                .willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(false);

        assertThatThrownBy(() -> service.executePayment(paymentRequest("uuid-3")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("500"));
        verify(syncRequester, never()).request(any());
    }

    @Test
    @DisplayName("DB 잔액 부족: 비즈니스 예외 발생, 잔액 미변경")
    void executePayment_insufficientBalance_throwsAndKeepsBalance() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("50"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(walletLedgerRepository.findFirstByTransactionUuid("uuid-4"))
                .willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);

        assertThatThrownBy(() -> service.executePayment(paymentRequest("uuid-4")))
                .isInstanceOf(BusinessException.class);

        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("50"));
        verify(syncRequester, never()).request(any());
    }

    @Test
    @DisplayName("SUCCESS 멱등성 재요청: syncRequester 재호출 없이 기존 응답 반환")
    void executePayment_idempotentSuccess_returnsExistingResultWithoutSync() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("400"));
        BankWallet to = wallet(TO_ADDRESS, new BigDecimal("100"));
        WalletLedger existing = successWalletLedger("uuid-5");

        given(walletLedgerRepository.findFirstByTransactionUuid("uuid-5"))
                .willReturn(Optional.of(existing));
        given(bankWalletRepository.findByWalletAddress(FROM_ADDRESS)).willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddress(TO_ADDRESS)).willReturn(Optional.of(to));

        PaymentResponse response = service.executePayment(paymentRequest("uuid-5"));

        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(syncRequester, never()).request(any());
        verify(contractCallService, never()).isMerchant(any());
    }

    @Test
    @DisplayName("취소 성공: WalletLedger(SUCCESS) 저장, syncRequester 호출")
    void executeCancel_success_savesLedgerAndRequestsSync() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(walletLedgerRepository.findFirstByTransactionUuid("uuid-6"))
                .willReturn(Optional.empty());
        given(contractCallService.isMerchant(FROM_ADDRESS)).willReturn(true);
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        CancelResponse response = service.executeCancel(cancelRequest("uuid-6", "orig-1"));

        assertThat(response.transactionUuid()).isEqualTo("uuid-6");
        assertThat(response.originalTransactionUuid()).isEqualTo("orig-1");
        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(syncRequester).request(any(BlockchainSyncRequest.class));
    }

    @Test
    @DisplayName("취소 비가맹점: 비즈니스 예외 발생, 잔액 미변경")
    void executeCancel_nonMerchant_throwsAndKeepsBalance() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));

        given(walletLedgerRepository.findFirstByTransactionUuid("uuid-7"))
                .willReturn(Optional.empty());
        given(contractCallService.isMerchant(FROM_ADDRESS)).willReturn(false);

        assertThatThrownBy(() -> service.executeCancel(cancelRequest("uuid-7", "orig-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("500"));
    }

    private PaymentRequest paymentRequest(String uuid) {
        return new PaymentRequest(uuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }

    private CancelRequest cancelRequest(String uuid, String originalUuid) {
        return new CancelRequest(uuid, originalUuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }

    private static BankWallet wallet(String address, BigDecimal balance) {
        BankWallet wallet =
                BankWallet.builder()
                        .id(1L)
                        .institution(Institution.builder().id(1L).build())
                        .walletAddress(address)
                        .encryptedPrivateKey("encrypted")
                        .build();
        wallet.updateBalance(balance);
        return wallet;
    }

    private static WalletLedger successWalletLedger(String uuid) {
        return WalletLedger.builder()
                .transactionUuid(uuid)
                .status(WalletLedgerStatus.SUCCESS)
                .confirmedAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }
}
