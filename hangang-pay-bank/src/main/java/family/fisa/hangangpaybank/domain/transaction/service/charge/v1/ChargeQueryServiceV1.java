package family.fisa.hangangpaybank.domain.transaction.service.charge.v1;

import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.wallet.repository.WalletLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeStatusResponse;
import family.fisa.hangangpaybank.domain.transaction.service.charge.ChargeQueryService;
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
public class ChargeQueryServiceV1 implements ChargeQueryService {

    private final WalletLedgerRepository walletLedgerRepository;

    /** 플랫폼의 transactionUuid로 충전 wallet_ledger 상태를 조회한다 */
    @Override
    public ChargeStatusResponse getStatus(String transactionUuid) {
        log.info("[bank] 충전 상태 조회 시작. transactionUuid={}", transactionUuid);

        // wallet_ledger 조회 — 충전 시 CREDIT 원장이 uuid로 기록된다(blockchain은 async).
        WalletLedger ledger = getWalletLedger(transactionUuid);

        ChargeStatusResponse response = ChargeStatusResponse.of(transactionUuid, ledger);

        log.info(
                "[bank] 충전 상태 조회 완료. transactionUuid={}, status={}, bankTransactionId={}",
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
