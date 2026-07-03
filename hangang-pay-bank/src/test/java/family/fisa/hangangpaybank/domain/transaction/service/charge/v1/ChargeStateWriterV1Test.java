package family.fisa.hangangpaybank.domain.transaction.service.charge.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.account.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.account.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.account.entity.LedgerType;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.account.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.wallet.repository.WalletLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@ExtendWith(MockitoExtension.class)
class ChargeStateWriterV1Test {

    private static final String WALLET_ADDRESS = "0x000000000000000000000000000000000000bbbb";
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private BankWalletRepository bankWalletRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private BlockchainLedgerRepository blockchainLedgerRepository;
    @Mock private AccountLedgerRepository accountLedgerRepository;
    @Mock private WalletLedgerRepository walletLedgerRepository;
    @Mock private ContractCallService contractCallService;

    private ChargeStateWriterV1 writer;

    @BeforeEach
    void setUp() {
        writer =
                new ChargeStateWriterV1(
                        bankAccountRepository,
                        bankWalletRepository,
                        institutionRepository,
                        blockchainLedgerRepository,
                        accountLedgerRepository,
                        walletLedgerRepository,
                        contractCallService);
    }

    @Test
    @DisplayName("충전 성공 시 선점한 blockchain ledger를 SUCCESS로 전환하고 account/wallet ledger를 저장한다")
    void executeCharge_savesSuccessLedgers() {
        Institution institution = institution();
        BankAccount bankAccount = bankAccount(institution, new BigDecimal("100000"));
        BankWallet bankWallet = wallet(WALLET_ADDRESS, BigDecimal.ZERO);
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-charge",
                        1L,
                        "1002-123-456789",
                        WALLET_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        TransactionReceipt receipt = receipt("0x-charge", 10L);
        BlockchainLedger pendingLedger =
                BlockchainLedger.of(institution, BlockchainTxStatus.PENDING, "uuid-charge");

        given(
                        bankAccountRepository.findByInstitution_IdAndAccountNumberWithLock(
                                1L, "1002-123-456789"))
                .willReturn(Optional.of(bankAccount));
        given(bankWalletRepository.findByWalletAddressWithLock(WALLET_ADDRESS))
                .willReturn(Optional.of(bankWallet));
        given(accountLedgerRepository.save(any(AccountLedger.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(walletLedgerRepository.save(any(WalletLedger.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(contractCallService.charge(eq(1L), eq(WALLET_ADDRESS), any())).willReturn(receipt);
        given(blockchainLedgerRepository.findById(99L)).willReturn(Optional.of(pendingLedger));

        ChargeResponse response = writer.executeCharge(request, 99L);

        assertThat(response.walletBalance()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(response.txHash()).isEqualTo("0x-charge");
        assertThat(bankAccount.getBalance()).isEqualByComparingTo(new BigDecimal("91000"));
        assertThat(bankWallet.getBalance()).isEqualByComparingTo(new BigDecimal("10000"));
        verify(walletLedgerRepository).save(any(WalletLedger.class));
    }

    @Test
    @DisplayName("충전 성공 재요청 시 저장된 성공 응답을 재구성한다")
    void getSuccessResponse_returnsStoredResponse() {
        Institution institution = institution();
        BankWallet bankWallet = wallet(WALLET_ADDRESS, new BigDecimal("25000"));
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-existing-success",
                        1L,
                        "1002-123-456789",
                        WALLET_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        BlockchainLedger successLedger =
                BlockchainLedger.builder()
                        .id(10L)
                        .institution(institution)
                        .idempotentKey("uuid-existing-success")
                        .status(BlockchainTxStatus.SUCCESS)
                        .txHash("0x-existing")
                        .blockNumber(123L)
                        .confirmedAt(LocalDateTime.now())
                        .build();
        AccountLedger accountLedger =
                AccountLedger.builder()
                        .id(15L)
                        .bankAccount(bankAccount(institution, new BigDecimal("91000")))
                        .ledgerType(LedgerType.WITHDRAWAL)
                        .status(LedgerStatus.SUCCESS)
                        .amount(new BigDecimal("9000"))
                        .balanceAfter(new BigDecimal("91000"))
                        .idempotentKey("uuid-existing-success")
                        .build();

        given(accountLedgerRepository.findByIdempotentKey("uuid-existing-success"))
                .willReturn(Optional.of(accountLedger));
        given(bankWalletRepository.findByWalletAddress(WALLET_ADDRESS))
                .willReturn(Optional.of(bankWallet));

        ChargeResponse response = writer.getSuccessResponse(request, successLedger);

        assertThat(response.bankTransactionId()).isEqualTo(15L);
        assertThat(response.walletBalance()).isEqualByComparingTo(new BigDecimal("25000"));
    }

    @Test
    @DisplayName("충전 선점 시 blockchain ledger PENDING을 저장하고 ledger id를 반환한다")
    void claimPendingCharge_returnsLedgerId() {
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-pending",
                        1L,
                        "1002-123-456789",
                        WALLET_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        Institution institution = institution();
        BlockchainLedger pendingLedger =
                BlockchainLedger.builder()
                        .id(20L)
                        .institution(institution)
                        .idempotentKey("uuid-pending")
                        .status(BlockchainTxStatus.PENDING)
                        .build();
        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));
        given(blockchainLedgerRepository.save(any(BlockchainLedger.class)))
                .willReturn(pendingLedger);

        Long ledgerId = writer.claimPendingCharge(request);

        assertThat(ledgerId).isEqualTo(20L);
    }

    @Test
    @DisplayName("충전 선점 시 unique 제약 충돌이 나면 duplicate 예외를 던진다")
    void claimPendingCharge_duplicate_throwsDuplicate() {
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-pending",
                        1L,
                        "1002-123-456789",
                        WALLET_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution()));
        given(blockchainLedgerRepository.save(any(BlockchainLedger.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> writer.claimPendingCharge(request))
                .isInstanceOf(family.fisa.hangangpaybank.global.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
    }

    @Test
    @DisplayName("충전 실패 기록 시 조회 가능한 account와 wallet에 FAILED ledger를 저장한다")
    void saveFailedChargeLedgers_savesFailedLedgers() {
        BankAccount bankAccount = bankAccount(institution(), new BigDecimal("100000"));
        BankWallet bankWallet = wallet(WALLET_ADDRESS, BigDecimal.ZERO);
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-charge-failed",
                        1L,
                        "1002-123-456789",
                        WALLET_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));

        given(bankAccountRepository.findByInstitution_IdAndAccountNumber(1L, "1002-123-456789"))
                .willReturn(Optional.of(bankAccount));
        given(bankWalletRepository.findByWalletAddress(WALLET_ADDRESS))
                .willReturn(Optional.of(bankWallet));
        given(accountLedgerRepository.save(any(AccountLedger.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(walletLedgerRepository.save(any(WalletLedger.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        writer.saveFailedChargeLedgers(request);

        verify(accountLedgerRepository).save(any(AccountLedger.class));
        verify(walletLedgerRepository).save(any(WalletLedger.class));
    }

    private static Institution institution() {
        return Institution.builder()
                .id(1L)
                .institutionCode("WOORI")
                .institutionName("Woori Bank")
                .build();
    }

    private static BankAccount bankAccount(Institution institution, BigDecimal balance) {
        return BankAccount.builder()
                .id(1L)
                .institution(institution)
                .accountNumber("1002-123-456789")
                .ownerName("tester")
                .balance(balance)
                .build();
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

    private static TransactionReceipt receipt(String txHash, Long blockNumber) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(txHash);
        receipt.setBlockNumber("0x" + BigInteger.valueOf(blockNumber).toString(16));
        receipt.setStatus("0x1");
        return receipt;
    }

    private static BigInteger toTokenUnit(BigDecimal amount) {
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }
}
