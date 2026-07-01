package family.fisa.hangangpay.domain.transaction.service.cancel.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.dto.response.CancelResponse;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.cancel.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelCommandService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.domain.transaction.service.support.BankCallExecutor;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class CancelCommandServiceV1 implements CancelCommandService {

    private static final Map<String, BaseErrorCode> CANCEL_BANK_FAIL_CODE_MAP =
            Map.of("TRANSACTION_ALREADY_FAILED", TransactionErrorCode.CANCEL_ALREADY_FAILED);

    private final TransactionRepository transactionRepository;
    private final BankClient bankClient;
    private final CancelIdempotencyStore cancelIdempotencyStore;
    private final CancelLockManager cancelLockManager;
    private final CancelStateWriter cancelStateWriter;
    private final CancelRequestHashGenerator cancelRequestHashGenerator;
    private final BankCallExecutor bankCallExecutor;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentCancelResponse executeCancel(
            Long merchantPartyId, Long transactionId, PaymentCancelRequest request) {

        /** 1. Lock 확보 - cancelUuid는 prepareCancel 내부에서 생성됨, originalTrnasacitonUuid 사용 */
        String originalTransactionUuid = getTransactionUuid(transactionId);

        /** 2. 분산 락 - 같은 originalPaymentUuid 취소가 동시에 두 건 진입하지 못하게 차단 */
        return cancelLockManager.withCancelLock(
                originalTransactionUuid,
                () ->
                        doExecuteCancel(
                                merchantPartyId, transactionId, originalTransactionUuid, request));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentCancelResponse recoverCancel(Long merchantPartyId, Long transactionId) {
        String originalTransactionUuid = getTransactionUuid(transactionId);

        return cancelLockManager.withCancelLock(
                originalTransactionUuid, () -> doRecoverCancel(merchantPartyId, transactionId));
    }

    /** 내부 메소드 */
    private PaymentCancelResponse doExecuteCancel(
            Long merchantPartyId,
            Long transactionId,
            String originalTransactionUuid,
            PaymentCancelRequest request) {
        /** 1. 멱등성 판정 */
        String requestHash =
                cancelRequestHashGenerator.generate(originalTransactionUuid, merchantPartyId);

        CancelIdempotencyDecision decision =
                cancelIdempotencyStore.beginCancel(originalTransactionUuid, requestHash);

        if (decision.type() == CancelIdempotencyDecisionType.RETURN_SNAPSHOT) {
            return decision.responseSnapshot();
        }

        if (decision.type() == CancelIdempotencyDecisionType.ALREADY_FAILED) {
            throw new BusinessException(TransactionErrorCode.CANCEL_ALREADY_FAILED);
        }

        if (decision.type() == CancelIdempotencyDecisionType.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.CANCEL_ALREADY_PROCESSING);
        }
        if (decision.type() == CancelIdempotencyDecisionType.CONFLICT) {
            throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        }

        /** 2. 검증 + CANCEL 저장 + Processing - REQUIRES_NEW 트랜잭션으로 커밋 */
        CancelExecutionPrepared prepared =
                cancelStateWriter.prepareCancel(
                        merchantPartyId, transactionId, request.paymentPin());

        /** 3. BANK 취소 호출 - DB 트랜잭션 밖에서 실행 */
        BankOutcome<CancelResponse> outcome =
                bankCallExecutor.callBankWithRetry(
                        () -> bankClient.cancel(prepared.toBankCancelRequest()),
                        CANCEL_BANK_FAIL_CODE_MAP,
                        TransactionErrorCode.CANCEL_FAILED);

        /** 4. 결과 분류 및 상태 반영 */
        PaymentCancelResponse response =
                switch (outcome.type()) {
                    case SUCCESS ->
                            cancelStateWriter.completeSuccess(
                                    prepared.cancelTransactionUuid(),
                                    null,
                                    null,
                                    outcome.value().confirmedAt());
                    case UNKNOWN -> cancelStateWriter.markUnknown(prepared.cancelTransactionUuid());
                    case TERMINAL_FAILED -> {
                        cancelStateWriter.completeFailed(prepared.cancelTransactionUuid());
                        throw new BusinessException(outcome.errorCode());
                    }
                };

        /** 5. 멱등성 snapshot 저장 */
        cancelIdempotencyStore.completeCancel(originalTransactionUuid, response);

        return response;
    }

    private PaymentCancelResponse doRecoverCancel(Long merchantPartyId, Long transactionId) {
        // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyRecoveryResult가 반영한다.
        CancelExecutionPrepared prepared =
                cancelStateWriter.prepareRecovery(merchantPartyId, transactionId);
        String cancelUuid = prepared.cancelTransactionUuid();

        // Bank 조회로 결과 확정 (404 → FAILED)
        PaymentCancelResponse response = resolveCancelRecovery(cancelUuid);

        // 종단으로 끝났으면 Redis 멱등 record 정리
        if (response.status() == TransactionStatus.SUCCESS) {
            cancelIdempotencyStore.completeCancel(prepared.originalTransactionUuid(), response);
        } else if (response.status() == TransactionStatus.FAILED) {
            cancelIdempotencyStore.failCancel(prepared.originalTransactionUuid());
        } else {
            // 은행이 아직 처리 중 → 시도 횟수만 올림
            cancelStateWriter.incrementRecoveryAttempt(cancelUuid);
        }

        return response;
    }

    /**
     * bankClient 호출 전 종료된 요청들은 PROCESSING 레코드가 저장되고 고아상태에 빠진다. 이런 경우는 은행쪽에 조회 응답이 404 - NOT FOUND로
     * 반환 된다.
     */
    private PaymentCancelResponse resolveCancelRecovery(String cancelUuid) {
        try {
            // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyRecoveryResult가 반영한다.
            BankTransactionStatusResponse bankStatus = bankClient.getTransactionStatus(cancelUuid);
            return cancelStateWriter.applyRecoveryResult(cancelUuid, bankStatus);
        } catch (RestClientResponseException e) {
            // 2. 404가 아니면 (5xx 등) 일시적 오류 같은 경우 다시 던져서 다음 sweep에 재시도한다.
            if (!e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw e;
            }
            // 3. 404는 은행 DB 원장에 기록자체가 없다. -> 은행 도달전 사망했다는 의미로 FAILED 확정 (플랫폼의 책임)
            // applyRecoveryResult의 FAILED 처리를 그대로 재사용하기 위해 FAILED status 합성
            BankTransactionStatusResponse asFailed =
                    BankTransactionStatusResponse.failed(cancelUuid);
            return cancelStateWriter.applyRecoveryResult(cancelUuid, asFailed);
        }
    }

    private String getTransactionUuid(Long transactionId) {
        return transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND))
                .getTransactionUuid();
    }
}
