package family.fisa.hangangpay.domain.transaction.infra.db.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.infra.db.AbstractDbIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.repository.IdempotencyRepository;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class DbPaymentIdempotencyStore extends AbstractDbIdempotencyStore<PaymentExecuteResponse>
        implements PaymentIdempotencyStore {

    public DbPaymentIdempotencyStore(
            IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        super(idempotencyRepository, objectMapper);
    }

    @Override
    protected String keyPrefix() {
        return "payment:idempotency:";
    }

    @Override
    protected Class<PaymentExecuteResponse> responseType() {
        return PaymentExecuteResponse.class;
    }

    @Override
    protected BaseErrorCode invalidError() {
        return TransactionErrorCode.PAYMENT_IDEMPOTENCY_RECORD_INVALID;
    }

    @Override
    public IdempotencyDecision<PaymentExecuteResponse> beginExecution(
            IdempotencyKey idempotencyKey, Long transactionId) {
        return begin(idempotencyKey, transactionId);
    }

    @Override
    public void completeExecution(
            String transactionUuid, PaymentExecuteResponse responseSnapshot) {
        complete(transactionUuid, responseSnapshot, responseSnapshot.status());
    }

    @Override
    public void failExecution(String transactionUuid) {
        changeStatus(transactionUuid, TransactionStatus.FAILED);
    }
}
