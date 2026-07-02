package family.fisa.hangangpay.domain.transaction.service.cancel.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelLockManager;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelReconcileService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelReconcileServiceV1 implements CancelReconcileService {

    private final TransactionRepository transactionRepository;
    private final BankClient bankClient;
    private final CancelLockManager cancelLockManager;
    private final CancelStateWriter cancelStateWriter;
    private final CancelIdempotencyStore cancelIdempotencyStore;

    /** 배치: CANCEL 거래로 자족한다(원본 PAYMENT 조회 불필요). 락 키는 원본 uuid, core는 cancel uuid 사용. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcile(Transaction cancelTx) {
        String originalTransactionUuid = cancelTx.getOriginalTransactionUuid();
        String cancelUuid = cancelTx.getTransactionUuid();
        cancelLockManager.withCancelLock(
                originalTransactionUuid, () -> core(cancelUuid, originalTransactionUuid));
    }

    /** 수동: 원본 결제 소유권 검증 후 reconcile. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentCancelResponse reconcileCancel(Long merchantPartyId, Long transactionId) {
        String originalTransactionUuid = getOriginalTransactionUuid(transactionId);

        return cancelLockManager.withCancelLock(
                originalTransactionUuid,
                () -> {
                    // 소유권(가맹점=수취자) + recoverable 검증
                    CancelExecutionPrepared prepared =
                            cancelStateWriter.prepareReconcile(merchantPartyId, transactionId);
                    return core(
                            prepared.cancelTransactionUuid(), prepared.originalTransactionUuid());
                });
    }

    /** 공통 core: bank 재조회 → 상태 반영 → 종단이면 Redis 멱등 record 정리, PROCESSING이면 시도 횟수만 증가. */
    private PaymentCancelResponse core(String cancelUuid, String originalTransactionUuid) {
        PaymentCancelResponse response = reconcileFromBank(cancelUuid);

        if (response.status() == TransactionStatus.SUCCESS) {
            cancelIdempotencyStore.completeCancel(originalTransactionUuid, response);
        } else if (response.status() == TransactionStatus.FAILED) {
            cancelIdempotencyStore.failCancel(originalTransactionUuid);
        } else {
            // 은행이 아직 처리 중 → 시도 횟수만 올림
            cancelStateWriter.incrementReconcileAttempt(cancelUuid);
        }

        return response;
    }

    /**
     * bankClient 호출 전 종료된 요청들은 PROCESSING 레코드가 저장되고 고아상태에 빠진다. 이런 경우는 은행쪽에 조회 응답이 404 - NOT FOUND로
     * 반환 된다.
     */
    private PaymentCancelResponse reconcileFromBank(String cancelUuid) {
        try {
            // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyReconcileResult가 반영한다.
            BankTransactionStatusResponse bankStatus = bankClient.getTransactionStatus(cancelUuid);
            return cancelStateWriter.applyReconcileResult(cancelUuid, bankStatus);
        } catch (BankException e) {
            // 2. 404가 아니면 (5xx 등) 일시적 오류 → 다시 던져서 다음 sweep에 재시도한다.
            if (!e.getError().isStatus(HttpStatus.NOT_FOUND)) {
                throw e;
            }
            // 3. 404는 은행 원장에 기록 자체가 없다 → 은행 도달 전 사망으로 간주해 FAILED 확정(플랫폼 책임).
            BankTransactionStatusResponse asFailed =
                    BankTransactionStatusResponse.failed(cancelUuid);
            return cancelStateWriter.applyReconcileResult(cancelUuid, asFailed);
        }
    }

    private String getOriginalTransactionUuid(Long transactionId) {
        return transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND))
                .getTransactionUuid();
    }
}
