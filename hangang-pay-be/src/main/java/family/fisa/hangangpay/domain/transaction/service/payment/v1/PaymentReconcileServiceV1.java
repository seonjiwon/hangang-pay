package family.fisa.hangangpay.domain.transaction.service.payment.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentLockManager;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentReconcileService;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconcileServiceV1 implements PaymentReconcileService {

    private final BankClient bankClient;
    private final PaymentLockManager paymentLockManager;
    private final PaymentStateWriter paymentStateWriter;
    private final PaymentIdempotencyStore paymentIdempotencyStore;

    /** 배치: 스케줄러가 고른 대상을 락만 잡고 재조회 확정한다. 소유권/rate-limit 없음. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcile(Transaction tx) {
        String transactionUuid = tx.getTransactionUuid();
        paymentLockManager.withTransactionLock(transactionUuid, () -> core(transactionUuid));
    }

    /** 수동: 소유권+recoverable+rate-limit 검증 후 재조회 확정한다. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentExecuteResponse reconcilePayment(Long partyId, String transactionUuid) {
        return paymentLockManager.withTransactionLock(
                transactionUuid,
                () -> {
                    // 복구 대상 검증(소유권/recoverable) + rate-limit, 검증된 uuid 확보
                    String reconcileUuid =
                            paymentStateWriter.prepareReconcile(partyId, transactionUuid);
                    return core(reconcileUuid);
                });
    }

    /** 공통 core: bank 재조회 → 상태 반영 → 종단이면 Redis 멱등 record 정리, PROCESSING이면 시도 횟수만 증가. */
    private PaymentExecuteResponse core(String transactionUuid) {
        PaymentExecuteResponse response = reconcileFromBank(transactionUuid);

        if (response.status() == TransactionStatus.SUCCESS) {
            paymentIdempotencyStore.completeExecution(transactionUuid, response);
        } else if (response.status() == TransactionStatus.FAILED) {
            paymentIdempotencyStore.failExecution(transactionUuid);
        } else {
            // 은행이 아직 처리 중 → 시도 횟수만 올리고 다음 주기 재시도 (cap 도달 시 sweep 제외)
            paymentStateWriter.incrementReconcileAttempt(transactionUuid);
        }

        return response;
    }

    /**
     * bankClient 호출 전 종료된 요청들은 PROCESSING 레코드가 저장되고 고아상태에 빠진다. 이런 경우는 은행쪽에 조회 응답이 404 - NOT FOUND로
     * 반환 된다.
     */
    private PaymentExecuteResponse reconcileFromBank(String transactionUuid) {
        try {
            // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyReconcileResult가 반영한다.
            BankTransactionStatusResponse bankStatus =
                    bankClient.getTransactionStatus(transactionUuid);
            return paymentStateWriter.applyReconcileResult(transactionUuid, bankStatus);
        } catch (BankException e) {
            // 2. 404가 아니면 (5xx 등) 일시적 오류 → 다시 던져서 다음 sweep에 재시도한다.
            if (!e.getError().isStatus(HttpStatus.NOT_FOUND)) {
                throw e;
            }
            // 3. 404는 은행 원장에 기록 자체가 없다 → 은행 도달 전 사망으로 간주해 FAILED 확정(플랫폼 책임).
            BankTransactionStatusResponse asFailed =
                    BankTransactionStatusResponse.failed(transactionUuid);
            return paymentStateWriter.applyReconcileResult(transactionUuid, asFailed);
        }
    }
}
