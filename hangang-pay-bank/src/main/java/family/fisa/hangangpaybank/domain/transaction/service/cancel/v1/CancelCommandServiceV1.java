package family.fisa.hangangpaybank.domain.transaction.service.cancel.v1;

import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.service.cancel.CancelCommandService;
import family.fisa.hangangpaybank.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 결제 취소 트랜잭션을 처리하는 커맨드(오케스트레이터) 서비스.
 *
 * <p>취소는 결제의 역방향 transfer이므로 payment 흐름의 {@link PaymentStateWriter}를 공용으로 사용한다. 메인 DB 트랜잭션은
 * PaymentStateWriter가 수행하고, 실패 ledger 저장은 rollback 이후 REQUIRES_NEW로 남긴다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CancelCommandServiceV1 implements CancelCommandService {

    private final PaymentStateWriter paymentStateWriter;

    @Override
    public CancelResponse cancel(CancelRequest request) {
        try {
            return paymentStateWriter.executeCancel(request);
        } catch (BusinessException e) {
            log.warn(
                    "[bank] cancel 비즈니스 실패. transactionUuid={}, originalTransactionUuid={}, message={}",
                    request.transactionUuid(),
                    request.originalTransactionUuid(),
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
