package family.fisa.hangangpaybank.domain.transaction.service.payment.v1;

import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.wallet.repository.WalletLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentStatusResponse;
import family.fisa.hangangpaybank.domain.transaction.service.payment.PaymentQueryService;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryServiceV1 implements PaymentQueryService {

    private final WalletLedgerRepository walletLedgerRepository;

    /** 플랫폼의 transactionUuid로 결제 상태를 조회한다 */
    @Override
    public PaymentStatusResponse getStatus(String transactionUuid) {
        log.info("[bank] 결제 상태 조회 시작. transactionUuid={}", transactionUuid);

        // 1. wallet_ledger 조회 — DB 레벨 결제 결과가 기준 (blockchain은 async)
        WalletLedger ledger = getWalletLedger(transactionUuid);

        // 2. 상태 매핑 후 응답 반환
        PaymentStatusResponse response = PaymentStatusResponse.of(transactionUuid, ledger);

        log.info(
                "[bank] 결제 상태 조회 완료. transactionUuid={}, status={}, bankTransactionId={}",
                transactionUuid,
                response.status(),
                response.bankTransactionId());

        return response;
    }

    private @NonNull WalletLedger getWalletLedger(String transactionUuid) {
        return walletLedgerRepository
                .findFirstByTransactionUuid(transactionUuid)
                .orElseThrow(
                        () -> new BusinessException(TransactionErrorCode.TRANSACTION_NOT_FOUND));
    }
}
