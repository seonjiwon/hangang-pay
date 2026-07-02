package family.fisa.hangangpay.domain.transaction.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelReconcileService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentReconcileService;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconcileSchedulerTest {

    private static final String PAYMENT_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String CANCEL_UUID = "22222222-2222-2222-2222-222222222222";
    private static final String EXCHANGE_UUID = "33333333-3333-3333-3333-333333333333";

    @Mock private TransactionRepository transactionRepository;
    @Mock private PaymentReconcileService paymentReconcileService;
    @Mock private CancelReconcileService cancelReconcileService;
    @Mock private ExchangeReconcileService exchangeReconcileService;
    @Mock private PaymentStateWriter paymentStateWriter;
    @Mock private CancelStateWriter cancelStateWriter;
    @Mock private ExchangeStateWriter exchangeStateWriter;

    @InjectMocks private ReconcileScheduler scheduler;

    // ===== 결제/취소 reconcile =====

    @Test
    @DisplayName("대상 CANCEL마다 cancelReconcileService.reconcile을 호출한다")
    void reconcileCancels_callsReconcileForEachTarget() {
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN, 0);
        given(
                        transactionRepository.findReconcileTargets(
                                eq(TransactionType.CANCEL), anyInt(), any()))
                .willReturn(List.of(cancelTx));

        scheduler.reconcileCancels();

        verify(cancelReconcileService).reconcile(cancelTx);
    }

    @Test
    @DisplayName("한 건 reconcile이 실패해도 나머지 건은 계속 처리된다")
    void reconcileCancels_continuesAfterSingleFailure() {
        Transaction firstCancel = cancelTransaction(TransactionStatus.UNKNOWN, 0);
        Transaction secondCancel = cancelTransaction(TransactionStatus.UNKNOWN, 0);
        given(
                        transactionRepository.findReconcileTargets(
                                eq(TransactionType.CANCEL), anyInt(), any()))
                .willReturn(List.of(firstCancel, secondCancel));
        doThrow(new RuntimeException("bank timeout"))
                .doNothing()
                .when(cancelReconcileService)
                .reconcile(any());

        scheduler.reconcileCancels();

        verify(cancelReconcileService, times(2)).reconcile(any());
    }

    @Test
    @DisplayName("대상 CANCEL이 없으면 reconcile을 호출하지 않는다")
    void reconcileCancels_skipsWhenNoTargets() {
        given(
                        transactionRepository.findReconcileTargets(
                                eq(TransactionType.CANCEL), anyInt(), any()))
                .willReturn(List.of());

        scheduler.reconcileCancels();

        verify(cancelReconcileService, never()).reconcile(any());
    }

    @Test
    @DisplayName("결제: 대상은 reconcile하고, 포기 대상은 EXPIRED로 닫는다")
    void reconcilePayments_reconcilesTargetsAndExpiresAbandoned() {
        Transaction target = paymentTransaction(TransactionStatus.PROCESSING, 0);
        given(
                        transactionRepository.findReconcileTargets(
                                eq(TransactionType.PAYMENT), anyInt(), any()))
                .willReturn(List.of(target));
        given(transactionRepository.findAbandonedTargets(eq(TransactionType.PAYMENT), anyInt()))
                .willReturn(List.of(paymentTransaction(TransactionStatus.PROCESSING, 10)));

        scheduler.reconcilePayments();

        verify(paymentReconcileService).reconcile(target);
        verify(paymentStateWriter).markExpired(PAYMENT_UUID);
    }

    @Test
    @DisplayName("취소: 포기 대상은 EXPIRED로 닫는다")
    void reconcileCancels_expiresAbandoned() {
        given(
                        transactionRepository.findReconcileTargets(
                                eq(TransactionType.CANCEL), anyInt(), any()))
                .willReturn(List.of());
        given(transactionRepository.findAbandonedTargets(eq(TransactionType.CANCEL), anyInt()))
                .willReturn(List.of(cancelTransaction(TransactionStatus.PROCESSING, 10)));

        scheduler.reconcileCancels();

        verify(cancelStateWriter).markExpired(CANCEL_UUID);
    }

    // ===== 환전 reconcile =====

    @Test
    @DisplayName("reconcile이 일시 오류(비즈니스 예외 아님)로 실패하면 retry 횟수를 1 올린다")
    void reconcileExchanges_incrementsRetryOnTransientFailure() {
        Transaction tx = exchangeProcessing();
        given(
                        transactionRepository.findReconcileTargets(
                                eq(TransactionType.EXCHANGE), anyInt(), any()))
                .willReturn(List.of(tx));
        willThrow(new RuntimeException("bank 5xx")).given(exchangeReconcileService).reconcile(tx);

        scheduler.reconcileExchanges();

        verify(exchangeStateWriter).incrementRetry(EXCHANGE_UUID);
    }

    @Test
    @DisplayName("reconcile이 비즈니스 예외로 실패하면 retry 횟수를 올리지 않는다")
    void reconcileExchanges_doesNotIncrementRetryOnBusinessException() {
        Transaction tx = exchangeProcessing();
        given(
                        transactionRepository.findReconcileTargets(
                                eq(TransactionType.EXCHANGE), anyInt(), any()))
                .willReturn(List.of(tx));
        willThrow(new BusinessException(TransactionErrorCode.EXCHANGE_NOT_FOUND))
                .given(exchangeReconcileService)
                .reconcile(tx);

        scheduler.reconcileExchanges();

        verify(exchangeStateWriter, never()).incrementRetry(any());
    }

    // ===== 픽스처 =====

    private Transaction cancelTransaction(TransactionStatus status, int attemptCount) {
        return Transaction.builder()
                .transactionUuid(CANCEL_UUID)
                .originalTransactionUuid(PAYMENT_UUID)
                .transactionType(TransactionType.CANCEL)
                .status(status)
                .amount(new BigDecimal("10000"))
                .reconcileAttemptCount(attemptCount)
                .build();
    }

    private Transaction paymentTransaction(TransactionStatus status, int attemptCount) {
        return Transaction.builder()
                .transactionUuid(PAYMENT_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(status)
                .amount(new BigDecimal("10000"))
                .reconcileAttemptCount(attemptCount)
                .build();
    }

    private Transaction exchangeProcessing() {
        return Transaction.builder()
                .transactionUuid(EXCHANGE_UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(TransactionStatus.PROCESSING)
                .amount(new BigDecimal("10000"))
                .reconcileAttemptCount(0)
                .build();
    }
}
