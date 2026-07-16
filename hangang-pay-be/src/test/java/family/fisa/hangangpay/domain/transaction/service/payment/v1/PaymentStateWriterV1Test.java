package family.fisa.hangangpay.domain.transaction.service.payment.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentRateLimiter;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PaymentStateWriterV1Test {

    private static final Long USER_ID = 1L;
    private static final Long USER_PARTY_ID = 10L;
    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long TRANSACTION_ID = 123L;

    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PaymentIdempotencyStore paymentIdempotencyStore;
    @Mock private PaymentRateLimiter paymentRateLimiter;
    @Mock private PaymentRequestHashGenerator paymentRequestHashGenerator;

    private PaymentStateWriter paymentStateWriter;

    @BeforeEach
    void setUp() {
        paymentStateWriter =
                new PaymentStateWriterV1(
                        transactionRepository,
                        userRepository,
                        merchantRepository,
                        passwordEncoder,
                        paymentIdempotencyStore,
                        paymentRateLimiter,
                        paymentRequestHashGenerator);
    }

    @Test
    @DisplayName("동일 요청 재시도 시 기존 snapshot을 반환한다")
    void prepareExecution_sameRequestReturnsSnapshot() {
        Transaction transaction = paymentTransaction(TransactionStatus.SUCCESS);
        PaymentExecuteResponse snapshot =
                new PaymentExecuteResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.SUCCESS,
                        "APV-2026-00000123",
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        LocalDateTime.of(2026, 5, 25, 10, 0));

        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));
        given(userRepository.findByIdWithParty(USER_ID)).willReturn(Optional.of(user()));
        given(passwordEncoder.matches("123456", "pin-hash")).willReturn(true);
        given(
                        paymentIdempotencyStore.beginExecution(
                                new IdempotencyKey(TRANSACTION_UUID, null), TRANSACTION_ID))
                .willReturn(IdempotencyDecision.returnSnapshot(snapshot));

        PaymentExecutionPreparationResult result =
                paymentStateWriter.prepareExecution(
                        USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456");

        assertThat(result.hasSnapshot()).isTrue();
        assertThat(result.responseSnapshot()).isSameAs(snapshot);
        assertThat(result.prepared()).isNull();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        verify(paymentRateLimiter, never()).checkExecutionRateLimit(any(), any(), any());
        verify(paymentRateLimiter, never()).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("다른 requestHash면 IDEMPOTENCY_CONFLICT 예외가 발생한다")
    void prepareExecution_differentRequestHashThrowsConflict() {
        Transaction transaction = paymentTransaction(TransactionStatus.PENDING);

        givenExecutionBase(transaction);
        given(
                        paymentIdempotencyStore.beginExecution(
                                new IdempotencyKey(TRANSACTION_UUID, null), TRANSACTION_ID))
                .willReturn(IdempotencyDecision.conflict());

        assertThatThrownBy(
                        () ->
                                paymentStateWriter.prepareExecution(
                                        USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.IDEMPOTENCY_CONFLICT);

        verify(paymentRateLimiter, never()).checkExecutionRateLimit(any(), any(), any());
        verify(paymentRateLimiter, never()).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("처리 중인 중복 요청은 PAYMENT_ALREADY_PROCESSING 예외가 발생한다")
    void prepareExecution_processingDuplicateThrowsAlreadyProcessing() {
        Transaction transaction = paymentTransaction(TransactionStatus.PENDING);

        givenExecutionBase(transaction);
        given(
                        paymentIdempotencyStore.beginExecution(
                                new IdempotencyKey(TRANSACTION_UUID, null), TRANSACTION_ID))
                .willReturn(IdempotencyDecision.processing());

        assertThatThrownBy(
                        () ->
                                paymentStateWriter.prepareExecution(
                                        USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);

        verify(paymentRateLimiter, never()).checkExecutionRateLimit(any(), any(), any());
        verify(paymentRateLimiter, never()).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("복구 준비 시 소유권과 복구 가능 상태를 검증하고 Bank 조회용 UUID를 반환한다")
    void prepareRecovery_validatesAndReturnsTransactionUuid() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);
        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));

        String result = paymentStateWriter.prepareReconcile(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(result).isEqualTo(TRANSACTION_UUID);
        verify(paymentRateLimiter).checkReconcileRateLimit(USER_PARTY_ID, TRANSACTION_UUID);
        verify(paymentRateLimiter).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("복구 준비 시 최종 상태 거래는 복구 대상이 아니다")
    void prepareRecovery_rejectsFinalStatus() {
        Transaction transaction = paymentTransaction(TransactionStatus.SUCCESS);
        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));

        assertThatThrownBy(
                        () -> paymentStateWriter.prepareReconcile(USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_RECOVERABLE);

        verify(paymentRateLimiter, never()).checkReconcileRateLimit(any(), any());
        verify(paymentRateLimiter, never()).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("Bank SUCCESS 복구 결과를 로컬 SUCCESS로 반영한다")
    void applyRecoveryResult_success() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);
        BankTransactionStatusResponse bankStatus =
                bankStatus(TransactionStatus.SUCCESS, 101L, "0x-recovered");

        givenRecoveryApplyBase(transaction);

        PaymentExecuteResponse response =
                paymentStateWriter.applyReconcileResult(TRANSACTION_UUID, bankStatus);

        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.merchantName()).isEqualTo("성수 한강카페");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getTxHash()).isNull();
        assertThat(transaction.getBankTransactionId()).isEqualTo("101");
    }

    @Test
    @DisplayName("Bank FAILED 복구 결과를 로컬 FAILED로 반영한다")
    void applyRecoveryResult_failed() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);
        BankTransactionStatusResponse bankStatus = bankStatus(TransactionStatus.FAILED, null, null);

        givenRecoveryApplyBase(transaction);

        PaymentExecuteResponse response =
                paymentStateWriter.applyReconcileResult(TRANSACTION_UUID, bankStatus);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getTxHash()).isNull();
    }

    @Test
    @DisplayName("Bank PROCESSING 복구 결과는 로컬 UNKNOWN을 유지한다")
    void applyRecoveryResult_processingKeepsUnknown() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);
        BankTransactionStatusResponse bankStatus =
                bankStatus(TransactionStatus.PROCESSING, null, null);

        givenRecoveryApplyBase(transaction);

        PaymentExecuteResponse response =
                paymentStateWriter.applyReconcileResult(TRANSACTION_UUID, bankStatus);

        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(transaction.getTxHash()).isNull();
    }

    @Test
    @DisplayName("Bank SUCCESS 복구 결과에 txHash나 bankTransactionId가 없으면 오류가 발생한다")
    void applyRecoveryResult_successRequiresBankProof() {
        Transaction transaction = paymentTransaction(TransactionStatus.UNKNOWN);
        BankTransactionStatusResponse bankStatus =
                bankStatus(TransactionStatus.SUCCESS, null, "0x-recovered");

        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));

        assertThatThrownBy(
                        () -> paymentStateWriter.applyReconcileResult(TRANSACTION_UUID, bankStatus))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        verify(merchantRepository, never()).findByParty_Id(any());
    }

    private void givenExecutionBase(Transaction transaction) {
        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));
        given(userRepository.findByIdWithParty(USER_ID)).willReturn(Optional.of(user()));
        given(passwordEncoder.matches("123456", "pin-hash")).willReturn(true);
    }

    private void givenRecoveryApplyBase(Transaction transaction) {
        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant(transaction.getToParty())));
    }

    private BankTransactionStatusResponse bankStatus(
            TransactionStatus status, Long bankTransactionId, String txHash) {
        return new BankTransactionStatusResponse(
                TRANSACTION_UUID,
                bankTransactionId,
                status,
                txHash,
                LocalDateTime.of(2026, 5, 25, 10, 5));
    }

    private Merchant merchant(Party party) {
        return Merchant.builder()
                .id(1L)
                .party(party)
                .merchantName("성수 한강카페")
                .username("merchant")
                .passwordHash("password-hash")
                .paymentPinHash("pin-hash")
                .businessNumber("123-45-67890")
                .ownerName("김한강")
                .build();
    }

    private Transaction paymentTransaction(TransactionStatus status) {
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);

        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(TRANSACTION_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(status)
                .fromParty(userParty)
                .toParty(merchantParty)
                .fromWallet(wallet(1L, userParty, "0x-user"))
                .toWallet(wallet(2L, merchantParty, "0x-merchant"))
                .amount(new BigDecimal("10000"))
                .approvalNumber("APV-2026-00000123")
                .itemName("아메리카노")
                .build();
    }

    private User user() {
        return User.builder()
                .id(USER_ID)
                .party(party(USER_PARTY_ID, PartyType.USER))
                .username("홍길동")
                .passwordHash("password-hash")
                .paymentPinHash("pin-hash")
                .phoneNumber("01012345678")
                .build();
    }

    private Wallet wallet(Long id, Party party, String address) {
        return Wallet.builder().id(id).party(party).address(address).build();
    }

    private Party party(Long id, PartyType type) {
        return Party.builder().id(id).partyType(type).build();
    }
}
