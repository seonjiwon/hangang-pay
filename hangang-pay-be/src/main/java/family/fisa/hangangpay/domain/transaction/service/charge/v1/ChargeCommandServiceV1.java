package family.fisa.hangangpay.domain.transaction.service.charge.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.ChargeResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.IntentCreationGuard;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeCommandService;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeStateWriter;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeCommandServiceV1 implements ChargeCommandService {

    /** intent TTL(분). 만료 스케줄러 기준. */
    private static final long INTENT_TTL_MINUTES = 10L;

    private final BankClient bankClient;
    private final ChargeIdempotencyStore chargeIdempotencyStore;
    private final ChargeStateWriter chargeStateWriter;

    // 의도 중복 생성 가드 (best-effort 부하 제어)
    private final IntentCreationGuard intentCreationGuard;

    /** 충전 intent - 금액·출금 계좌 바인딩 후 PENDING 생성 */
    public ChargeIntentResponse createIntent(Long partyId, ChargeIntentCreateRequest request) {
        intentCreationGuard.check(
                TransactionType.CHARGE, partyId, request.amount(), request.accountId());
        log.info("충전 intent 생성 시작. partyId={}", partyId);
        return chargeStateWriter.createIntent(partyId, request, expiresAt());
    }

    /** 충전 실행 오케스트레이션: 멱등성 판단 → 은행 충전 요청 → 상태 전환 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChargeExecuteResponse execute(
            Long partyId, String transactionUuid, ChargeExecuteRequest request) {

        ChargeExecutionPreparationResult result =
                chargeStateWriter.prepareProcessing(partyId, transactionUuid, request.paymentPin());

        if (result.hasSnapshot()) {
            return result.responseSnapshot();
        }

        ChargeExecutionPrepared prepared = result.prepared();

        // 은행 충전 요청
        ChargeResponse bankResponse;
        try {
            log.info("충전 은행 요청 시작. transactionUuid={}", prepared.transactionUuid());
            bankResponse = bankClient.charge(prepared.toBankChargeRequest());
            log.info("충전 은행 요청 완료. transactionUuid={}", prepared.transactionUuid());
        } catch (ResourceAccessException ex) {
            log.error("충전 은행 연동 실패. transactionUuid={}", prepared.transactionUuid(), ex);
            ChargeExecuteResponse response =
                    chargeStateWriter.markUnknown(prepared.transactionUuid());
            chargeIdempotencyStore.markExecutionStatus(
                    prepared.transactionUuid(), TransactionStatus.UNKNOWN);
            return response;
        }

        // 충전 성공 처리
        ChargeExecuteResponse response =
                chargeStateWriter.completeSuccess(
                        prepared.transactionUuid(),
                        bankResponse.txHash(),
                        String.valueOf(bankResponse.bankTransactionId()),
                        bankResponse.confirmedAt(),
                        bankResponse.walletBalance());

        chargeIdempotencyStore.completeExecution(prepared.transactionUuid(), response);

        return response;
    }

    private LocalDateTime expiresAt() {
        return LocalDateTime.now().plusMinutes(INTENT_TTL_MINUTES);
    }
}
