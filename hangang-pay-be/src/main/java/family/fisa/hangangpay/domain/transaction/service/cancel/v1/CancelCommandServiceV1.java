package family.fisa.hangangpay.domain.transaction.service.cancel.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.CancelResponse;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.internal.cancel.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelCommandService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.domain.transaction.service.support.BankCallExecutor;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    private String getTransactionUuid(Long transactionId) {
        return transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND))
                .getTransactionUuid();
    }
}
