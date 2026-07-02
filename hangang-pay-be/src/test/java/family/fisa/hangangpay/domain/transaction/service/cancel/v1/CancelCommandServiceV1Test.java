package family.fisa.hangangpay.domain.transaction.service.cancel.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.BankErrorInterpreter;
import family.fisa.hangangpay.client.bank.dto.response.CancelResponse;
import family.fisa.hangangpay.client.bank.exception.BankError;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelLockManager;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.domain.transaction.service.support.v1.BankCallExecutorV1;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

@ExtendWith(MockitoExtension.class)
class CancelCommandServiceV1Test {

    private static final Long USER_PARTY_ID = 10L;
    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long OTHER_PARTY_ID = 30L;
    private static final Long TRANSACTION_ID = 123L;

    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String CANCEL_UUID = "22222222-2222-2222-2222-222222222222";
    private static final String REQUEST_HASH = "server-generated-request-hash";

    @Mock private TransactionRepository transactionRepository;
    @Mock private BankClient bankClient;
    @Mock private CancelStateWriter cancelStateWriter;
    @Mock private CancelIdempotencyStore cancelIdempotencyStore;
    @Mock private CancelLockManager cancelLockManager;
    @Mock private CancelRequestHashGenerator cancelRequestHashGenerator;

    private CancelCommandServiceV1 cancelCommandService;

    @BeforeEach
    void setUP() {
        // Bank 재시도/분류 엔진은 실제 구현을 주입해 재시도 동작을 그대로 검증한다.
        cancelCommandService =
                new CancelCommandServiceV1(
                        transactionRepository,
                        bankClient,
                        cancelIdempotencyStore,
                        cancelLockManager,
                        cancelStateWriter,
                        cancelRequestHashGenerator,
                        new BankCallExecutorV1(new BankErrorInterpreter()));

        // 취소 멱등 해시 생성기는 비-null 해시를 반환해야 beginCancel(anyString, anyString) 매칭이 성립한다.
        lenient()
                .when(cancelRequestHashGenerator.generate(anyString(), anyLong()))
                .thenReturn(REQUEST_HASH);
    }

    @Test
    @DisplayName("정상 취소 시 Bank를 호출하고 SUCCESS로 확정된다")
    void executeCancel_success() {
        CancelExecutionPrepared prepared =
                new CancelExecutionPrepared(
                        CANCEL_UUID,
                        TRANSACTION_UUID,
                        "0x-merchant",
                        "0x-user",
                        new BigDecimal("10000"));

        CancelResponse bankResponse = successBankCancelResponse();

        PaymentCancelResponse expected =
                new PaymentCancelResponse(
                        CANCEL_UUID,
                        TransactionStatus.SUCCESS,
                        "APV-2026-00000456",
                        new BigDecimal("10000"),
                        bankResponse.confirmedAt());

        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willReturn(prepared);
        given(bankClient.cancel(prepared.toBankCancelRequest())).willReturn(bankResponse);
        given(
                        cancelStateWriter.completeSuccess(
                                CANCEL_UUID, null, null, bankResponse.confirmedAt()))
                .willReturn(expected);

        PaymentCancelResponse response =
                cancelCommandService.executeCancel(
                        MERCHANT_PARTY_ID, TRANSACTION_ID, new PaymentCancelRequest("123456"));

        assertThat(response).isSameAs(expected);
        verify(cancelStateWriter).prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456");
        verify(bankClient).cancel(prepared.toBankCancelRequest());
        verify(cancelStateWriter)
                .completeSuccess(CANCEL_UUID, null, null, bankResponse.confirmedAt());
    }

    @Test
    @DisplayName("세션 가맹점이 결제 수신자(toParty)가 아니면 취소가 거부된다")
    void executeCancel_failsWhenMerchantIsNotReceiver() {
        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelStateWriter.prepareCancel(OTHER_PARTY_ID, TRANSACTION_ID, "123456"))
                .willThrow(new BusinessException(TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN));

        assertThatThrownBy(
                        () ->
                                cancelCommandService.executeCancel(
                                        OTHER_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);

        verify(bankClient, never()).cancel(any());
    }

    @Test
    @DisplayName("원본 결제가 SUCCESS 상태가 아니면 취소가 거부된다")
    void executeCancel_failsWhenPaymentNotSuccess() {
        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willThrow(new BusinessException(TransactionErrorCode.PAYMENT_NOT_CANCELLABLE));

        assertThatThrownBy(
                        () ->
                                cancelCommandService.executeCancel(
                                        MERCHANT_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_CANCELLABLE);

        verify(bankClient, never()).cancel(any());
    }

    @Test
    @DisplayName("동일 원본에 SUCCESS CANCEL이 이미 존재하면 재취소가 거부된다")
    void executeCancel_failsWhenAlreadyCancelled() {
        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willThrow(new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_CANCELLED));

        assertThatThrownBy(
                        () ->
                                cancelCommandService.executeCancel(
                                        MERCHANT_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_ALREADY_CANCELLED);

        verify(bankClient, never()).cancel(any());
    }

    @Test
    @DisplayName("Bank timeout 시 CANCEL이 UNKNOWN으로 저장되고 실패 확정이 아님을 반환한다")
    void executeCancel_marksUnknownWhenBankTimeout() {
        // 1. prepareCancel 정상 완료 — CANCEL 레코드가 PROCESSING으로 DB에 커밋된 상태
        CancelExecutionPrepared prepared =
                new CancelExecutionPrepared(
                        CANCEL_UUID,
                        TRANSACTION_UUID,
                        "0x-merchant",
                        "0x-user",
                        new BigDecimal("10000"));

        // 2. UNKNOWN 상태 응답 — txHash, confirmedAt 없음 (은행 확정 전)
        PaymentCancelResponse unknownResponse =
                new PaymentCancelResponse(
                        CANCEL_UUID,
                        TransactionStatus.UNKNOWN, // 실패 확정이 아니라 "모름"
                        null, // 승인번호 없음
                        new BigDecimal("10000"),
                        null); // confirmedAt 없음

        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willReturn(prepared);
        // 3. Bank 네트워크 오류 — 요청이 도달했는지 알 수 없다
        given(bankClient.cancel(prepared.toBankCancelRequest())).willThrow(timeoutException());
        given(cancelStateWriter.markUnknown(CANCEL_UUID)).willReturn(unknownResponse);

        PaymentCancelResponse response =
                cancelCommandService.executeCancel(
                        MERCHANT_PARTY_ID, TRANSACTION_ID, new PaymentCancelRequest("123456"));

        // 4. UNKNOWN 응답 검증
        assertThat(response).isSameAs(unknownResponse);
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);

        // 5. 호출 흐름 검증 — completeSuccess는 절대 호출되면 안 된다
        verify(cancelStateWriter).prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456");
        // 타임아웃은 재시도 대상 → bank 호출 2회 후에도 미해결이면 UNKNOWN
        verify(bankClient, times(2)).cancel(prepared.toBankCancelRequest());
        verify(cancelStateWriter).markUnknown(CANCEL_UUID);
        verify(cancelIdempotencyStore).completeCancel(TRANSACTION_UUID, unknownResponse);
        verify(cancelStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("취소 Bank가 422(ALREADY_FAILED)면 재시도 없이 FAILED로 확정하고 예외를 던진다")
    void executeCancel_terminalFailure_throwsAndCompletesFailed() {
        CancelExecutionPrepared prepared =
                new CancelExecutionPrepared(
                        CANCEL_UUID,
                        TRANSACTION_UUID,
                        "0x-merchant",
                        "0x-user",
                        new BigDecimal("10000"));

        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willReturn(prepared);
        given(bankClient.cancel(prepared.toBankCancelRequest()))
                .willThrow(bankException(422, "TRANSACTION_ALREADY_FAILED"));

        assertThatThrownBy(
                        () ->
                                cancelCommandService.executeCancel(
                                        MERCHANT_PARTY_ID,
                                        TRANSACTION_ID,
                                        new PaymentCancelRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.CANCEL_ALREADY_FAILED);

        verify(bankClient).cancel(prepared.toBankCancelRequest()); // 재시도 없음(종단)
        verify(cancelStateWriter).completeFailed(CANCEL_UUID);
        verify(cancelStateWriter, never()).completeSuccess(any(), any(), any(), any());
        verify(cancelIdempotencyStore, never()).completeCancel(any(), any());
    }

    @Test
    @DisplayName("Bank 서버 오류(RestClientResponseException) 시 CANCEL이 UNKNOWN으로 저장되고 snapshot이 적재된다")
    void executeCancel_bankServerError_marksUnknownAndStoresSnapshot() {
        // 1. prepareCancel 정상 완료
        CancelExecutionPrepared prepared =
                new CancelExecutionPrepared(
                        CANCEL_UUID,
                        TRANSACTION_UUID,
                        "0x-merchant",
                        "0x-user",
                        new BigDecimal("10000"));

        // 2. UNKNOWN 응답
        PaymentCancelResponse unknownResponse =
                new PaymentCancelResponse(
                        CANCEL_UUID,
                        TransactionStatus.UNKNOWN,
                        null,
                        new BigDecimal("10000"),
                        null);

        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(paymentTransaction(TransactionStatus.SUCCESS)));
        given(cancelLockManager.withCancelLock(anyString(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(cancelIdempotencyStore.beginCancel(anyString(), anyString()))
                .willReturn(CancelIdempotencyDecision.newRequest());
        given(cancelStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .willReturn(prepared);
        // 3. Bank 5xx
        given(bankClient.cancel(prepared.toBankCancelRequest())).willThrow(bankServerException());
        given(cancelStateWriter.markUnknown(CANCEL_UUID)).willReturn(unknownResponse);

        PaymentCancelResponse response =
                cancelCommandService.executeCancel(
                        MERCHANT_PARTY_ID, TRANSACTION_ID, new PaymentCancelRequest("123456"));

        // 4. UNKNOWN 응답 반환 검증
        assertThat(response).isSameAs(unknownResponse);
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);

        // 5. snapshot 적재 검증 — markCancelStatus가 아니라 completeCancel
        verify(cancelStateWriter).markUnknown(CANCEL_UUID);
        verify(cancelIdempotencyStore).completeCancel(TRANSACTION_UUID, unknownResponse);
        verify(cancelStateWriter, never()).completeSuccess(any(), any(), any(), any());
    }

    private CancelResponse successBankCancelResponse() {
        return new CancelResponse(
                CANCEL_UUID,
                TRANSACTION_UUID,
                "SUCCESS",
                LocalDateTime.of(2026, 5, 27, 14, 0),
                new BigDecimal("110000"),
                new BigDecimal("90000"));
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

    private Transaction paymentTransaction(TransactionStatus status) {
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);

        return Transaction.builder()
                .id(123L)
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

    private Wallet wallet(Long id, Party party, String address) {
        return Wallet.builder().id(id).party(party).address(address).build();
    }

    private Party party(Long id, PartyType type) {
        return Party.builder().id(id).partyType(type).build();
    }
}
