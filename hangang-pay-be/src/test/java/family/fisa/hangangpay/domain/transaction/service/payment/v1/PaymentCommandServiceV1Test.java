package family.fisa.hangangpay.domain.transaction.service.payment.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.BankErrorInterpreter;
import family.fisa.hangangpay.client.bank.dto.response.PaymentResponse;
import family.fisa.hangangpay.client.bank.exception.BankError;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.payment.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.domain.transaction.service.support.v1.BankCallExecutorV1;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceV1Test {

    private static final Long USER_ID = 1L;
    private static final Long USER_PARTY_ID = 10L;
    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long TRANSACTION_ID = 123L;

    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_HASH = "server-generated-request-hash";

    @Mock private TransactionRepository transactionRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private PartyRepository partyRepository;
    @Mock private BankClient bankClient;

    @Mock private PaymentIdempotencyStore paymentIdempotencyStore;
    @Mock private PaymentIntentDedupStore paymentIntentDedupStore;
    @Mock private PaymentLockManager paymentLockManager;
    @Mock private PaymentRateLimiter paymentRateLimiter;
    @Mock private PaymentRequestHashGenerator paymentRequestHashGenerator;
    @Mock private PaymentStateWriter paymentStateWriter;

    private PaymentCommandServiceV1 paymentCommandService;

    @BeforeEach
    void setUP() {
        // Bank 재시도/분류 엔진은 실제 구현을 주입해 재시도 동작을 그대로 검증한다.
        paymentCommandService =
                new PaymentCommandServiceV1(
                        transactionRepository,
                        merchantRepository,
                        walletRepository,
                        partyRepository,
                        bankClient,
                        paymentIdempotencyStore,
                        paymentIntentDedupStore,
                        paymentLockManager,
                        paymentRateLimiter,
                        paymentRequestHashGenerator,
                        paymentStateWriter,
                        new BankCallExecutorV1(new BankErrorInterpreter()));
    }

    @Test
    @DisplayName("결제 의도 생성 시 PENDING PAYMENT 거래가 저장된다")
    void createPaymentIntent_savesPendingPaymentTransaction() {
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);
        Merchant merchant = merchant(merchantParty);
        Wallet userWallet = wallet(1L, userParty, "0x-user");
        Wallet merchantWallet = wallet(2L, merchantParty, "0x-merchant");

        PaymentIntentCreateRequest request =
                new PaymentIntentCreateRequest(MERCHANT_PARTY_ID, new BigDecimal("10000"), "아메리카노");

        given(partyRepository.findById(USER_PARTY_ID)).willReturn(Optional.of(userParty));
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant));
        given(walletRepository.findByParty_Id(USER_PARTY_ID)).willReturn(Optional.of(userWallet));
        given(walletRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchantWallet));
        given(
                        paymentRequestHashGenerator.generateIntentExecutionHash(
                                USER_PARTY_ID, MERCHANT_PARTY_ID, request.amount()))
                .willReturn(REQUEST_HASH);
        given(paymentIntentDedupStore.reserve(eq(REQUEST_HASH), anyString()))
                .willReturn(Optional.empty());
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentIntentResponse response =
                paymentCommandService.createPaymentIntent(USER_PARTY_ID, request);

        assertThat(response.transactionUuid()).isNotBlank();
        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.merchantPartyId()).isEqualTo(MERCHANT_PARTY_ID);
        assertThat(response.amount()).isEqualByComparingTo("10000");
        assertThat(response.itemName()).isEqualTo("아메리카노");

        verify(paymentRateLimiter).checkIntentRateLimit(USER_PARTY_ID, MERCHANT_PARTY_ID);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        Transaction saved = captor.getValue();
        assertThat(saved.getTransactionUuid()).isNotBlank();
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(saved.getFromParty()).isSameAs(userParty);
        assertThat(saved.getToParty()).isSameAs(merchantParty);
        assertThat(saved.getFromWallet()).isSameAs(userWallet);
        assertThat(saved.getToWallet()).isSameAs(merchantWallet);
        assertThat(saved.getAmount()).isEqualByComparingTo("10000");
        assertThat(saved.getItemName()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("30초 내 같은 결제 의도 요청은 새 거래를 저장하지 않고 기존 UUID를 반환한다")
    void createPaymentIntent_duplicateFingerprintReturnsExistingTransactionUuid() {
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);
        Merchant merchant = merchant(merchantParty);
        Wallet userWallet = wallet(1L, userParty, "0x-user");
        Wallet merchantWallet = wallet(2L, merchantParty, "0x-merchant");
        AtomicReference<String> firstTransactionUuid = new AtomicReference<>();
        AtomicReference<Transaction> savedTransaction = new AtomicReference<>();

        PaymentIntentCreateRequest request =
                new PaymentIntentCreateRequest(
                        MERCHANT_PARTY_ID, new BigDecimal("10000.00"), "아메리카노");

        given(partyRepository.findById(USER_PARTY_ID)).willReturn(Optional.of(userParty));
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant));
        given(walletRepository.findByParty_Id(USER_PARTY_ID)).willReturn(Optional.of(userWallet));
        given(walletRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchantWallet));
        given(
                        paymentRequestHashGenerator.generateIntentExecutionHash(
                                USER_PARTY_ID, MERCHANT_PARTY_ID, request.amount()))
                .willReturn(REQUEST_HASH);
        given(paymentIntentDedupStore.reserve(eq(REQUEST_HASH), anyString()))
                .willAnswer(
                        invocation -> {
                            String newUuid = invocation.getArgument(1);
                            if (firstTransactionUuid.compareAndSet(null, newUuid)) {
                                return Optional.empty();
                            }
                            return Optional.of(firstTransactionUuid.get());
                        });
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(
                        invocation -> {
                            Transaction transaction = invocation.getArgument(0);
                            ReflectionTestUtils.setField(
                                    transaction, "createdAt", LocalDateTime.of(2026, 6, 12, 10, 0));
                            savedTransaction.set(transaction);
                            return transaction;
                        });
        given(transactionRepository.findByTransactionUuid(anyString()))
                .willAnswer(
                        invocation -> {
                            String transactionUuid = invocation.getArgument(0);
                            Transaction transaction = savedTransaction.get();
                            if (transaction != null
                                    && transaction.getTransactionUuid().equals(transactionUuid)) {
                                return Optional.of(transaction);
                            }
                            return Optional.empty();
                        });

        PaymentIntentResponse first =
                paymentCommandService.createPaymentIntent(USER_PARTY_ID, request);
        PaymentIntentResponse second =
                paymentCommandService.createPaymentIntent(USER_PARTY_ID, request);

        assertThat(second.transactionUuid()).isEqualTo(first.transactionUuid());
        assertThat(second.expiresAt()).isEqualTo(LocalDateTime.of(2026, 6, 12, 10, 10));
        verify(transactionRepository).save(any(Transaction.class));
        verify(transactionRepository).findByTransactionUuid(first.transactionUuid());
    }

    @Test
    @DisplayName("10분 내 살아있는 PENDING이 DB에 있으면 save 없이 기존 거래를 재사용해 반환한다")
    void createPaymentIntent_reusesLivePendingPaymentFromDb() {
        // 1. 준비 - 만료 전(살아있는) PENDING 결제 의도 1건이 DB에 존재
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);
        Merchant merchant = merchant(merchantParty);
        Wallet userWallet = wallet(1L, userParty, "0x-user");
        Wallet merchantWallet = wallet(2L, merchantParty, "0x-merchant");

        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 14, 10, 0);
        Transaction livePending =
                Transaction.builder()
                        .id(TRANSACTION_ID)
                        .transactionUuid(TRANSACTION_UUID)
                        .transactionType(TransactionType.PAYMENT)
                        .status(TransactionStatus.PENDING)
                        .fromParty(userParty)
                        .toParty(merchantParty)
                        .fromWallet(userWallet)
                        .toWallet(merchantWallet)
                        .amount(new BigDecimal("10000"))
                        .itemName("아메리카노")
                        .build();
        ReflectionTestUtils.setField(livePending, "createdAt", createdAt);

        PaymentIntentCreateRequest request =
                new PaymentIntentCreateRequest(MERCHANT_PARTY_ID, new BigDecimal("10000"), "아메리카노");

        given(partyRepository.findById(USER_PARTY_ID)).willReturn(Optional.of(userParty));
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant));
        given(walletRepository.findByParty_Id(USER_PARTY_ID)).willReturn(Optional.of(userWallet));
        given(walletRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchantWallet));
        given(
                        transactionRepository.findLivePendingPayment(
                                eq(USER_PARTY_ID),
                                eq(MERCHANT_PARTY_ID),
                                eq(request.amount()),
                                any(LocalDateTime.class)))
                .willReturn(Optional.of(livePending));

        // 2. 실행
        PaymentIntentResponse response =
                paymentCommandService.createPaymentIntent(USER_PARTY_ID, request);

        // 3. 검증 - 기존 거래 재사용, 새 저장/선점 없음
        assertThat(response.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.expiresAt()).isEqualTo(createdAt.plusMinutes(10));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(paymentIntentDedupStore, never()).reserve(anyString(), anyString());
    }

    @Test
    @DisplayName("정상 실행 시 PENDING -> PROCESSING -> SUCCESS 상태로 끝난다")
    void executePayment_success() {
        // given
        // REQUIRES_NEW 구간에서 결제 실행 검증과 PROCESSING 전환이 끝났다고 가정
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));

        // Bank 서버 결제 결과 정상 반환
        PaymentResponse bankResponse = successBankPaymentResponse();

        // SUCCESS 저장 후 command service가 최종 반환할 응답
        PaymentExecuteResponse expected =
                new PaymentExecuteResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.SUCCESS,
                        "APV-2026-00000123",
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        LocalDateTime.of(2026, 5, 25, 10, 0));

        given(
                        paymentStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));

        given(bankClient.payment(prepared.toBankPaymentRequest())).willReturn(bankResponse);

        given(
                        paymentStateWriter.completeSuccess(
                                TRANSACTION_UUID, null, null, bankResponse.confirmedAt()))
                .willReturn(expected);

        // Redis lock mock은 락 획득 성공 후 콜백을 바로 실행하도록 만든다.
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(
                        invocation -> {
                            Supplier<?> supplier = invocation.getArgument(1);
                            return supplier.get();
                        });

        // when
        PaymentExecuteResponse response =
                paymentCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        // then
        assertThat(response).isSameAs(expected);

        verify(paymentStateWriter)
                .prepareExecution(USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456");

        verify(bankClient).payment(prepared.toBankPaymentRequest());

        verify(paymentStateWriter)
                .completeSuccess(TRANSACTION_UUID, null, null, bankResponse.confirmedAt());

        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, expected);
    }

    @Test
    @DisplayName("Bank 타임아웃 시 PENDING -> PROCESSING -> UNKNOWN 상태로 끝난다")
    void executePayment_timeoutMarksUnknown() {
        // given
        // REQUIRES_NEW 구간에서 검증과 PROCESSING 전환이 끝났다고 가정한다.
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));

        PaymentExecuteResponse unknownResponse =
                new PaymentExecuteResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.UNKNOWN,
                        "APV-2026-00000123",
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        LocalDateTime.of(2026, 5, 25, 10, 0));

        given(
                        paymentStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));

        given(bankClient.payment(prepared.toBankPaymentRequest())).willThrow(timeoutException());

        given(paymentStateWriter.markUnknown(TRANSACTION_UUID)).willReturn(unknownResponse);

        // Redis lock mock은 락 획득 성공 후 콜백을 바로 실행하도록 만든다.
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(
                        invocation -> {
                            Supplier<?> supplier = invocation.getArgument(1);
                            return supplier.get();
                        });

        // when
        PaymentExecuteResponse response =
                paymentCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        // then
        assertThat(response).isSameAs(unknownResponse);

        verify(paymentStateWriter)
                .prepareExecution(USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456");

        // 타임아웃은 재시도 대상 → bank 호출 2회(원본+재시도) 후에도 미해결이면 UNKNOWN
        verify(bankClient, times(2)).payment(prepared.toBankPaymentRequest());
        verify(paymentStateWriter).markUnknown(TRANSACTION_UUID);
        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, unknownResponse);
        verify(paymentStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Bank가 422(ALREADY_FAILED)면 재시도 없이 FAILED로 확정하고 예외를 던진다")
    void executePayment_terminalFailure_throwsAndCompletesFailed() {
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));

        given(
                        paymentStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));
        given(bankClient.payment(prepared.toBankPaymentRequest()))
                .willThrow(bankException(422, "TRANSACTION_ALREADY_FAILED"));
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        assertThatThrownBy(
                        () ->
                                paymentCommandService.executePayment(
                                        USER_ID,
                                        USER_PARTY_ID,
                                        TRANSACTION_UUID,
                                        new PaymentExecuteRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_ALREADY_FAILED);

        // 종단 실패 → 재시도 없음(1회), FAILED 확정, snapshot 미적재
        verify(bankClient).payment(prepared.toBankPaymentRequest());
        verify(paymentStateWriter).completeFailed(TRANSACTION_UUID);
        verify(paymentStateWriter, never()).completeSuccess(any(), any(), any(), any());
        verify(paymentIdempotencyStore, never()).completeExecution(any(), any());
    }

    @Test
    @DisplayName("일시적 오류(timeout) 후 재시도에서 성공하면 SUCCESS로 확정된다")
    void executePayment_retrySucceeds() {
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));
        PaymentResponse bankResponse = successBankPaymentResponse();
        PaymentExecuteResponse expected =
                new PaymentExecuteResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.SUCCESS,
                        "APV-2026-00000123",
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        LocalDateTime.of(2026, 5, 25, 10, 0));

        given(
                        paymentStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));
        // 1차 timeout → 2차 성공
        given(bankClient.payment(prepared.toBankPaymentRequest()))
                .willThrow(timeoutException())
                .willReturn(bankResponse);
        given(
                        paymentStateWriter.completeSuccess(
                                TRANSACTION_UUID, null, null, bankResponse.confirmedAt()))
                .willReturn(expected);
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        PaymentExecuteResponse response =
                paymentCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        assertThat(response).isSameAs(expected);
        verify(bankClient, times(2)).payment(prepared.toBankPaymentRequest());
        verify(paymentStateWriter)
                .completeSuccess(TRANSACTION_UUID, null, null, bankResponse.confirmedAt());
        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, expected);
    }

    @Test
    @DisplayName("409(DUPLICATE_PROCESSING)는 재시도 후에도 미해결이면 UNKNOWN으로 끝난다")
    void executePayment_duplicateProcessing_retriesThenUnknown() {
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));
        PaymentExecuteResponse unknownResponse =
                new PaymentExecuteResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.UNKNOWN,
                        null,
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        null);

        given(
                        paymentStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));
        given(bankClient.payment(prepared.toBankPaymentRequest()))
                .willThrow(bankException(409, "TRANSACTION_DUPLICATE_PROCESSING"));
        given(paymentStateWriter.markUnknown(TRANSACTION_UUID)).willReturn(unknownResponse);
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        PaymentExecuteResponse response =
                paymentCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        assertThat(response).isSameAs(unknownResponse);
        // 409는 재시도 대상 → 2회 호출 후 UNKNOWN, FAILED 아님
        verify(bankClient, times(2)).payment(prepared.toBankPaymentRequest());
        verify(paymentStateWriter).markUnknown(TRANSACTION_UUID);
        verify(paymentStateWriter, never()).completeFailed(any());
    }

    @Test
    @DisplayName("Bank 서버 오류(RestClientResponseException) 시 PAYMENT가 UNKNOWN으로 저장되고 snapshot이 적재된다")
    void executePayment_bankServerError_marksUnknownAndStoresSnapshot() {
        // 1. prepareExecution 정상 완료
        PaymentExecutionPrepared prepared =
                new PaymentExecutionPrepared(
                        TRANSACTION_UUID,
                        REQUEST_HASH,
                        "0x-user",
                        "0x-merchant",
                        new BigDecimal("10000"));

        PaymentExecuteResponse unknownResponse =
                new PaymentExecuteResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.UNKNOWN,
                        null,
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        null);

        given(
                        paymentStateWriter.prepareExecution(
                                USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .willReturn(PaymentExecutionPreparationResult.prepared(prepared));
        // 2. Bank 5xx
        given(bankClient.payment(prepared.toBankPaymentRequest())).willThrow(bankServerException());
        given(paymentStateWriter.markUnknown(TRANSACTION_UUID)).willReturn(unknownResponse);
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        PaymentExecuteResponse response =
                paymentCommandService.executePayment(
                        USER_ID,
                        USER_PARTY_ID,
                        TRANSACTION_UUID,
                        new PaymentExecuteRequest("123456"));

        // 3. UNKNOWN 응답 반환 검증
        assertThat(response).isSameAs(unknownResponse);
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);

        // 4. snapshot 적재 검증 — markExecutionStatus가 아니라 completeExecution
        verify(paymentStateWriter).markUnknown(TRANSACTION_UUID);
        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, unknownResponse);
        verify(paymentStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    /** bank HTTP 에러 응답(status + code)을 표현하는 BankException (BankClientImpl이 변환한 형태) */
    private BankException bankException(int status, String bankCode) {
        return new BankException(
                BankError.of(HttpStatusCode.valueOf(status), bankCode, "bank error"));
    }

    /** 응답을 못 받은 타임아웃/IO 실패 */
    private BankException timeoutException() {
        return new BankException(BankError.noResponse("timeout"));
    }

    /** 코드 없는 5xx 서버 오류 */
    private BankException bankServerException() {
        return new BankException(BankError.of(HttpStatusCode.valueOf(500), null, "500"));
    }

    private PaymentResponse successBankPaymentResponse() {
        return new PaymentResponse(
                TRANSACTION_UUID,
                "SUCCESS",
                LocalDateTime.of(2026, 5, 25, 10, 0),
                new BigDecimal("90000"),
                new BigDecimal("110000"));
    }

    private Merchant merchant(Party party) {
        return Merchant.builder()
                .id(1L)
                .party(party)
                .merchantName("성수 한강카페")
                .username("merchant")
                .passwordHash("password-hash")
                .businessNumber("123-45-67890")
                .ownerName("김한강")
                .build();
    }

    private Wallet wallet(Long id, Party party, String address) {
        return Wallet.builder().id(id).party(party).address(address).build();
    }

    private Party party(Long id, PartyType type) {
        return Party.builder().id(id).partyType(type).build();
    }
}
