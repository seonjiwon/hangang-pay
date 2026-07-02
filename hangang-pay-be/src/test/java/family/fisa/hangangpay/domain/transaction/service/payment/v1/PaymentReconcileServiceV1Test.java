package family.fisa.hangangpay.domain.transaction.service.payment.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentLockManager;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReconcileServiceV1Test {

    private static final Long USER_PARTY_ID = 10L;
    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";

    @Mock private BankClient bankClient;
    @Mock private PaymentLockManager paymentLockManager;
    @Mock private PaymentStateWriter paymentStateWriter;
    @Mock private PaymentIdempotencyStore paymentIdempotencyStore;

    @InjectMocks private PaymentReconcileServiceV1 reconcileService;

    // ===== 수동 reconcile (/recover 엔드포인트) =====

    @Test
    @DisplayName("UNKNOWN 복구 시 Bank SUCCESS 결과로 상태를 SUCCESS로 갱신한다")
    void reconcilePayment_updatesStatusFromBankSuccess() {
        BankTransactionStatusResponse bankStatus = reconcileBankStatus(TransactionStatus.SUCCESS);
        PaymentExecuteResponse expected = paymentReconcileResponse(TransactionStatus.SUCCESS);

        givenReconcileBase(bankStatus, expected);

        PaymentExecuteResponse response =
                reconcileService.reconcilePayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response).isSameAs(expected);

        InOrder inOrder = inOrder(paymentStateWriter, bankClient);
        inOrder.verify(paymentStateWriter).prepareReconcile(USER_PARTY_ID, TRANSACTION_UUID);
        inOrder.verify(bankClient).getTransactionStatus(TRANSACTION_UUID);
        inOrder.verify(paymentStateWriter).applyReconcileResult(TRANSACTION_UUID, bankStatus);
    }

    @Test
    @DisplayName("UNKNOWN 복구 시 Bank FAILED 결과로 상태를 FAILED로 갱신한다")
    void reconcilePayment_updatesStatusFromBankFailed() {
        BankTransactionStatusResponse bankStatus = reconcileBankStatus(TransactionStatus.FAILED);
        PaymentExecuteResponse expected = paymentReconcileResponse(TransactionStatus.FAILED);

        givenReconcileBase(bankStatus, expected);

        PaymentExecuteResponse response =
                reconcileService.reconcilePayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response).isSameAs(expected);
        verify(paymentStateWriter).applyReconcileResult(TRANSACTION_UUID, bankStatus);
    }

    @Test
    @DisplayName("Bank가 아직 PROCESSING이면 복구 가능한 상태로 남긴다")
    void reconcilePayment_keepsRecoverableWhenBankStillProcessing() {
        BankTransactionStatusResponse bankStatus =
                reconcileBankStatus(TransactionStatus.PROCESSING);
        PaymentExecuteResponse expected = paymentReconcileResponse(TransactionStatus.UNKNOWN);

        givenReconcileBase(bankStatus, expected);

        PaymentExecuteResponse response =
                reconcileService.reconcilePayment(USER_PARTY_ID, TRANSACTION_UUID);

        assertThat(response).isSameAs(expected);
        verify(paymentStateWriter).applyReconcileResult(TRANSACTION_UUID, bankStatus);
    }

    @Test
    @DisplayName("Bank SUCCESS 조회 결과에 txHash가 없으면 복구 결과 오류가 발생한다")
    void reconcilePayment_bankSuccessWithoutTxHashThrowsInvalidRecoveryResult() {
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        TRANSACTION_UUID,
                        101L,
                        TransactionStatus.SUCCESS,
                        null,
                        LocalDateTime.of(2026, 5, 25, 10, 5));
        givenReconcileThrows(bankStatus, TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        assertThatThrownBy(() -> reconcileService.reconcilePayment(USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
    }

    @Test
    @DisplayName("Bank SUCCESS 조회 결과에 bankTransactionId가 없으면 복구 결과 오류가 발생한다")
    void reconcilePayment_bankSuccessWithoutBankTransactionIdThrowsInvalidRecoveryResult() {
        BankTransactionStatusResponse bankStatus =
                new BankTransactionStatusResponse(
                        TRANSACTION_UUID,
                        null,
                        TransactionStatus.SUCCESS,
                        "0x-recovered",
                        LocalDateTime.of(2026, 5, 25, 10, 5));
        givenReconcileThrows(bankStatus, TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);

        assertThatThrownBy(() -> reconcileService.reconcilePayment(USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
    }

    @Test
    @DisplayName("SUCCESS 같은 최종 상태는 복구 대상이 아니다")
    void reconcilePayment_rejectsNonRecoverableStatus() {
        givenPrepareThrows(TransactionErrorCode.PAYMENT_NOT_RECOVERABLE);

        assertThatThrownBy(() -> reconcileService.reconcilePayment(USER_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_RECOVERABLE);

        verify(bankClient, never()).getTransactionStatus(any());
    }

    @Test
    @DisplayName("복구도 transactionUuid Redis lock 안에서 실행한다")
    void reconcilePayment_usesTransactionLock() {
        BankTransactionStatusResponse bankStatus = reconcileBankStatus(TransactionStatus.SUCCESS);
        PaymentExecuteResponse expected = paymentReconcileResponse(TransactionStatus.SUCCESS);
        givenReconcileBase(bankStatus, expected);

        reconcileService.reconcilePayment(USER_PARTY_ID, TRANSACTION_UUID);

        verify(paymentLockManager).withTransactionLock(eq(TRANSACTION_UUID), any());
    }

    @Test
    @DisplayName("복구해도 은행이 아직 PROCESSING이면 시도 횟수만 올린다(cap 진행)")
    void reconcilePayment_stillProcessing_incrementsAttempt() {
        BankTransactionStatusResponse bankStatus =
                reconcileBankStatus(TransactionStatus.PROCESSING);
        PaymentExecuteResponse stillProcessing =
                paymentReconcileResponse(TransactionStatus.PROCESSING);
        givenReconcileBase(bankStatus, stillProcessing);

        reconcileService.reconcilePayment(USER_PARTY_ID, TRANSACTION_UUID);

        verify(paymentStateWriter).incrementReconcileAttempt(TRANSACTION_UUID);
        verify(paymentIdempotencyStore, never()).completeExecution(anyString(), any());
        verify(paymentIdempotencyStore, never()).failExecution(anyString());
    }

    // ===== 배치 reconcile (스케줄러) =====

    @Test
    @DisplayName("배치 reconcile: 락만 잡고 소유권/rate-limit 검증 없이 재조회 확정한다")
    void reconcile_batch_confirmsWithoutOwnershipCheck() {
        Transaction tx = Transaction.builder().transactionUuid(TRANSACTION_UUID).build();
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        BankTransactionStatusResponse bankStatus = reconcileBankStatus(TransactionStatus.SUCCESS);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID)).willReturn(bankStatus);
        PaymentExecuteResponse resp = paymentReconcileResponse(TransactionStatus.SUCCESS);
        given(paymentStateWriter.applyReconcileResult(TRANSACTION_UUID, bankStatus))
                .willReturn(resp);

        reconcileService.reconcile(tx);

        // 배치는 prepareReconcile(소유권/rate-limit)을 거치지 않는다
        verify(paymentStateWriter, never()).prepareReconcile(any(), any());
        verify(paymentIdempotencyStore).completeExecution(TRANSACTION_UUID, resp);
    }

    // ===== 헬퍼 =====

    private void givenReconcileBase(
            BankTransactionStatusResponse bankStatus, PaymentExecuteResponse response) {
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        given(paymentStateWriter.prepareReconcile(USER_PARTY_ID, TRANSACTION_UUID))
                .willReturn(TRANSACTION_UUID);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID)).willReturn(bankStatus);
        given(paymentStateWriter.applyReconcileResult(TRANSACTION_UUID, bankStatus))
                .willReturn(response);
    }

    private void givenReconcileThrows(
            BankTransactionStatusResponse bankStatus, TransactionErrorCode errorCode) {
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(paymentStateWriter.prepareReconcile(USER_PARTY_ID, TRANSACTION_UUID))
                .willReturn(TRANSACTION_UUID);
        given(bankClient.getTransactionStatus(TRANSACTION_UUID)).willReturn(bankStatus);
        given(paymentStateWriter.applyReconcileResult(TRANSACTION_UUID, bankStatus))
                .willThrow(new BusinessException(errorCode));
    }

    private void givenPrepareThrows(TransactionErrorCode errorCode) {
        given(paymentLockManager.withTransactionLock(eq(TRANSACTION_UUID), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        given(paymentStateWriter.prepareReconcile(USER_PARTY_ID, TRANSACTION_UUID))
                .willThrow(new BusinessException(errorCode));
    }

    private BankTransactionStatusResponse reconcileBankStatus(TransactionStatus status) {
        return new BankTransactionStatusResponse(
                TRANSACTION_UUID,
                status == TransactionStatus.SUCCESS ? 101L : null,
                status,
                status == TransactionStatus.SUCCESS ? "0x-recovered" : null,
                LocalDateTime.of(2026, 5, 25, 10, 5));
    }

    private PaymentExecuteResponse paymentReconcileResponse(TransactionStatus status) {
        return new PaymentExecuteResponse(
                TRANSACTION_UUID,
                status,
                "APV-2026-00000123",
                new BigDecimal("10000"),
                "성수 한강카페",
                LocalDateTime.of(2026, 5, 25, 10, 5));
    }
}
