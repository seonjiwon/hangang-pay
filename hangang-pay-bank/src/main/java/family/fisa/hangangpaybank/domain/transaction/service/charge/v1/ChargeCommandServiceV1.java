package family.fisa.hangangpaybank.domain.transaction.service.charge.v1;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import family.fisa.hangangpaybank.domain.transaction.service.charge.ChargeCommandService;
import family.fisa.hangangpaybank.domain.transaction.service.charge.ChargeStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 충전 트랜잭션을 처리하는 커맨드(오케스트레이터) 서비스.
 *
 * <p>charge는 동기 blockchain 호출을 유지하되 상태 반영 로직은 ChargeStateWriter로 분리한다. 실패 ledger 저장은 메인 트랜잭션
 * rollback 이후 REQUIRES_NEW로 남긴다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeCommandServiceV1 implements ChargeCommandService {

    private final ChargeStateWriter chargeStateWriter;

    /** 충전: 계좌 → 토큰 mint. 발행량 제한 여부를 온체인에서 확인해야 하므로 동기 방식으로 요청해야 한다. */
    @Override
    public ChargeResponse charge(ChargeRequest request) {
        // 1. 멱등성 체크를 먼저 수행해 SUCCESS/PENDING/FAILED 상태를 즉시 분기
        Optional<BlockchainLedger> existingOpt =
                chargeStateWriter.findExistingCharge(request.transactionUuid());
        if (existingOpt.isPresent()) {
            BlockchainLedger existing = existingOpt.get();
            switch (existing.getStatus()) {
                case SUCCESS:
                    return chargeStateWriter.getSuccessResponse(request, existing);
                case PENDING:
                case SUBMITTED:
                    chargeStateWriter.throwDuplicateProcessing(request.transactionUuid());
                    break;
                default:
                    chargeStateWriter.throwAlreadyFailed(request.transactionUuid());
            }
        }

        // 2. 새 요청으로 판단된 경우에만 검증과 선점 수행
        // 2-1. 충전 가능한지 read only 트랜잭션으로 검증
        chargeStateWriter.validateChargeRequest(request);
        // 2-2. blockchain ledger에 Pending 먼저 기록 (requires_new)
        Long claimedLedgerId = chargeStateWriter.claimPendingCharge(request);

        try {
            // 3. 선점한 ledger를 기준으로 실제 charge를 수행하고 성공 시 SUCCESS로 확정
            return chargeStateWriter.executeCharge(request, claimedLedgerId);
        } catch (BusinessException e) {
            log.warn(
                    "[bank] charge 비즈니스 실패. transactionUuid={}, institutionId={}, message={}",
                    request.transactionUuid(),
                    request.institutionId(),
                    e.getMessage());
            if (claimedLedgerId != null) {
                // 4. blockchain ledger - FAILED로 전환 TODO: reconcile scheduler에서 제외 필요
                chargeStateWriter.markChargeFailed(claimedLedgerId, request.transactionUuid());
            }
            // 5. wallet, account ledger - FAILED 기록
            chargeStateWriter.saveFailedChargeLedgers(request);
            throw e;
        }
    }
}
