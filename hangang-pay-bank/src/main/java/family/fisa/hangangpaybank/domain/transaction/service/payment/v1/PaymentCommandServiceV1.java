package family.fisa.hangangpaybank.domain.transaction.service.payment.v1;

import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.domain.transaction.service.payment.PaymentCommandService;
import family.fisa.hangangpaybank.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 결제 트랜잭션을 처리하는 커맨드(오케스트레이터) 서비스.
 *
 * <p>메인 DB 트랜잭션은 PaymentStateWriter가 수행하고, 실패 ledger 저장은 rollback 이후 REQUIRES_NEW로 남긴다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandServiceV1 implements PaymentCommandService {

    private final PaymentStateWriter paymentStateWriter;

    @Override
    public PaymentResponse payment(PaymentRequest request) {
        try {
            return paymentStateWriter.executePayment(request);
        } catch (BusinessException e) {
            log.warn(
                    "[bank] payment 비즈니스 실패. transactionUuid={}, message={}",
                    request.transactionUuid(),
                    e.getMessage());
            paymentStateWriter.saveFailedWalletLedgersByAddress(
                    request.fromWalletAddress(),
                    request.toWalletAddress(),
                    request.transactionUuid(),
                    request.amount());
            throw e;
        }
    }
}
