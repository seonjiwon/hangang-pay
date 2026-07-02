package family.fisa.hangangpay.domain.transaction.service.cancel.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelLockManager;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelReconcileServiceV1Test {

    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long TRANSACTION_ID = 123L;
    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String CANCEL_UUID = "22222222-2222-2222-2222-222222222222";

    @Mock private TransactionRepository transactionRepository;
    @Mock private BankClient bankClient;
    @Mock private CancelLockManager cancelLockManager;
    @Mock private CancelStateWriter cancelStateWriter;
    @Mock private CancelIdempotencyStore cancelIdempotencyStore;

    @InjectMocks private CancelReconcileServiceV1 reconcileService;

    // ===== 수동 reconcile (/cancel/recover 엔드포인트) =====

    @Test
    @DisplayName("UNKNOWN CANCEL이 Bank SUCCESS이면 SUCCESS로 복구된다")
    void reconcileCancel_successFromUnknown() {
        CancelExecutionPrepared prepared = cancelPrepared();
        BankTransactionStatusResponse bankStatus = cancelBankStatus(TransactionStatus.SUCCESS);
        PaymentCancelResponse expected = cancelResponse(TransactionStatus.SUCCESS);

        givenReconcileBase(prepared, bankStatus, expected);

        PaymentCancelResponse response =
                reconcileService.reconcileCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        assertThat(response).isSameAs(expected);

        InOrder inOrder = inOrder(cancelStateWriter, bankClient);
        inOrder.verify(cancelStateWriter).prepareReconcile(MERCHANT_PARTY_ID, TRANSACTION_ID);
        inOrder.verify(bankClient).getTransactionStatus(CANCEL_UUID);
        inOrder.verify(cancelStateWriter).applyReconcileResult(CANCEL_UUID, bankStatus);
        verify(cancelIdempotencyStore).completeCancel(TRANSACTION_UUID, expected);
    }

    @Test
    @DisplayName("UNKNOWN CANCEL이 Bank FAILED이면 FAILED로 확정된다")
    void reconcileCancel_failedFromUnknown() {
        CancelExecutionPrepared prepared = cancelPrepared();
        BankTransactionStatusResponse bankStatus = cancelBankStatus(TransactionStatus.FAILED);
        PaymentCancelResponse expected = cancelResponse(TransactionStatus.FAILED);

        givenReconcileBase(prepared, bankStatus, expected);

        PaymentCancelResponse response =
                reconcileService.reconcileCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        assertThat(response).isSameAs(expected);
        verify(cancelIdempotencyStore, never()).completeCancel(any(), any());
    }

    @Test
    @DisplayName("Bank가 아직 PROCESSING이면 CANCEL 상태를 UNKNOWN으로 유지한다")
    void reconcileCancel_keepsUnknownWhenBankStillProcessing() {
        CancelExecutionPrepared prepared = cancelPrepared();
        BankTransactionStatusResponse bankStatus = cancelBankStatus(TransactionStatus.PROCESSING);
        PaymentCancelResponse expected = cancelResponse(TransactionStatus.UNKNOWN);

        givenReconcileBase(prepared, bankStatus, expected);

        PaymentCancelResponse response =
                reconcileService.reconcileCancel(MERCHANT_PARTY_ID, TRANSACTION_ID);

        assertThat(response).isSameAs(expected);
        verify(cancelStateWriter).incrementReconcileAttempt(CANCEL_UUID);
        verify(cancelIdempotencyStore, never()).completeCancel(any(), any());
    }

    @Test
    @DisplayName("복구 가능한 CANCEL이 없으면 예외가 발생하고 Bank는 호출되지 않는다")
    void reconcileCancel_failsWhenNoRecoverableCancel() {
        givenPrepareThrows(TransactionErrorCode.CANCEL_NOT_RECOVERABLE);

        assertThatThrownBy(
                        () -> reconcileService.reconcileCancel(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.CANCEL_NOT_RECOVERABLE);

        verify(bankClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("세션 가맹점이 원본 결제 수신자가 아니면 복구가 거부된다")
    void reconcileCancel_failsWhenMerchantIsNotReceiver() {
        givenPrepareThrows(TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);

        assertThatThrownBy(
                        () -> reconcileService.reconcileCancel(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);

        verify(bankClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("취소 복구 시 Bank SUCCESS인데 bankTransactionId가 없으면 복구 결과 오류가 발생한다")
    void reconcileCancel_bankSuccessWithNullBankTransactionId_throwsRecoveryResultInvalid() {
        CancelExecutionPrepared prepared = cancelPrepared();
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        CANCEL_UUID,
                        null,
                        TransactionStatus.SUCCESS,
                        null,
                        LocalDateTime.of(2026, 5, 27, 14, 30));

        givenReconcileThrows(
                prepared, bankStatus, TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        assertThatThrownBy(
                        () -> reconcileService.reconcileCancel(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
    }

    // ===== 배치 reconcile (스케줄러) =====

    @Test
    @DisplayName("배치 reconcile: CANCEL 거래로 자족(원본 조회 없이) 락 잡고 재조회 확정한다")
    void reconcile_batch_selfSufficientFromCancelTransaction() {
        Transaction cancelTx =
                Transaction.builder()
                        .transactionUuid(CANCEL_UUID)
                        .originalTransactionUuid(TRANSACTION_UUID)
                        .transactionType(TransactionType.CANCEL)
                        .status(TransactionStatus.UNKNOWN)
                        .build();
        given(cancelLockManager.withCancelLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        BankTransactionStatusResponse bankStatus = cancelBankStatus(TransactionStatus.SUCCESS);
        given(bankClient.getTransactionStatus(CANCEL_UUID)).willReturn(bankStatus);
        PaymentCancelResponse resp = cancelResponse(TransactionStatus.SUCCESS);
        given(cancelStateWriter.applyReconcileResult(CANCEL_UUID, bankStatus)).willReturn(resp);

        reconcileService.reconcile(cancelTx);

        // 배치는 원본 PAYMENT 조회도, prepareReconcile(소유권)도 거치지 않는다
        verify(transactionRepository, never()).findById(any());
        verify(cancelStateWriter, never()).prepareReconcile(any(), any());
        verify(cancelIdempotencyStore).completeCancel(TRANSACTION_UUID, resp);
    }

    // ===== 헬퍼 =====

    private void givenReconcileBase(
            CancelExecutionPrepared prepared,
            BankTransactionStatusResponse bankStatus,
            PaymentCancelResponse response) {
        givenLock();
        given(cancelStateWriter.prepareReconcile(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .willReturn(prepared);
        given(bankClient.getTransactionStatus(prepared.cancelTransactionUuid()))
                .willReturn(bankStatus);
        given(cancelStateWriter.applyReconcileResult(prepared.cancelTransactionUuid(), bankStatus))
                .willReturn(response);
    }

    private void givenReconcileThrows(
            CancelExecutionPrepared prepared,
            BankTransactionStatusResponse bankStatus,
            TransactionErrorCode errorCode) {
        givenLock();
        given(cancelStateWriter.prepareReconcile(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .willReturn(prepared);
        given(bankClient.getTransactionStatus(prepared.cancelTransactionUuid()))
                .willReturn(bankStatus);
        given(cancelStateWriter.applyReconcileResult(prepared.cancelTransactionUuid(), bankStatus))
                .willThrow(new BusinessException(errorCode));
    }

    private void givenPrepareThrows(TransactionErrorCode errorCode) {
        givenLock();
        given(cancelStateWriter.prepareReconcile(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .willThrow(new BusinessException(errorCode));
    }

    private void givenLock() {
        Transaction originalPayment =
                Transaction.builder()
                        .id(TRANSACTION_ID)
                        .transactionUuid(TRANSACTION_UUID)
                        .transactionType(TransactionType.PAYMENT)
                        .status(TransactionStatus.SUCCESS)
                        .build();
        given(transactionRepository.findById(TRANSACTION_ID))
                .willReturn(Optional.of(originalPayment));
        given(cancelLockManager.withCancelLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
    }

    private CancelExecutionPrepared cancelPrepared() {
        return new CancelExecutionPrepared(
                CANCEL_UUID, TRANSACTION_UUID, "0x-merchant", "0x-user", new BigDecimal("10000"));
    }

    private BankTransactionStatusResponse cancelBankStatus(TransactionStatus status) {
        return new BankTransactionStatusResponse(
                CANCEL_UUID,
                status == TransactionStatus.SUCCESS ? 888L : null,
                status,
                status == TransactionStatus.SUCCESS ? "0x-recovered-cancel" : null,
                LocalDateTime.of(2026, 5, 27, 14, 30));
    }

    private PaymentCancelResponse cancelResponse(TransactionStatus status) {
        return new PaymentCancelResponse(
                CANCEL_UUID,
                status,
                status == TransactionStatus.SUCCESS ? "APV-2026-00000456" : null,
                new BigDecimal("10000"),
                LocalDateTime.of(2026, 5, 27, 14, 30));
    }
}
