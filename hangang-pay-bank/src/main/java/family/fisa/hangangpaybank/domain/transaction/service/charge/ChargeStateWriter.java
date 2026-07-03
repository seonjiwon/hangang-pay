package family.fisa.hangangpaybank.domain.transaction.service.charge;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import java.util.Optional;

/** 충전 상태 전환/보상 포트. 현재 구현은 {@code v1.ChargeStateWriterV1}. */
public interface ChargeStateWriter {

    Optional<BlockchainLedger> findExistingCharge(String transactionUuid);

    ChargeResponse getSuccessResponse(ChargeRequest request, BlockchainLedger existingLedger);

    void throwDuplicateProcessing(String transactionUuid);

    void throwAlreadyFailed(String transactionUuid);

    void validateChargeRequest(ChargeRequest request);

    Long claimPendingCharge(ChargeRequest request);

    ChargeResponse executeCharge(ChargeRequest request, Long ledgerId);

    void markChargeFailed(Long ledgerId, String transactionUuid);

    void saveFailedChargeLedgers(ChargeRequest request);
}
