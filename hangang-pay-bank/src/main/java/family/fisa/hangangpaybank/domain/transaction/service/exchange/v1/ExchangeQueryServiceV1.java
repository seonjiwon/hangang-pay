package family.fisa.hangangpaybank.domain.transaction.service.exchange.v1;

import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.account.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeStatusResponse;
import family.fisa.hangangpaybank.domain.transaction.service.exchange.ExchangeQueryService;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 환전 상태 조회. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeQueryServiceV1 implements ExchangeQueryService {

    private final AccountLedgerRepository accountLedgerRepository;

    @Override
    public ExchangeStatusResponse getStatus(String transactionUuid) {
        log.info("환전 상태 조회 시작. transactionUuid={}", transactionUuid);

        // account_ledger 기준 조회 — DB 레벨 환전 결과가 기준 (blockchain은 async)
        AccountLedger accountLedger =
                accountLedgerRepository
                        .findByIdempotentKey(transactionUuid)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.TRANSACTION_NOT_FOUND));

        ExchangeStatusResponse response = ExchangeStatusResponse.of(transactionUuid, accountLedger);

        log.info("환전 상태 조회 완료. transactionUuid={}, status={}", transactionUuid, response.status());
        return response;
    }
}
