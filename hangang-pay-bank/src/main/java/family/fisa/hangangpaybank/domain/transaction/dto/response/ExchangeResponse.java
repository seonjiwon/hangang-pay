package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import java.math.BigDecimal;
import lombok.Builder;

/** 환전 응답. 블록체인 burn은 비동기 outbox로 처리되므로 온체인 정보는 포함하지 않는다. */
@Builder
public record ExchangeResponse(
        String transactionUuid, Long bankTransactionId, String status, BigDecimal accountBalance) {

    /** 동기 접수 응답: account_ledger 커밋 완료 = DB 레벨 SUCCESS. 블록체인 burn은 비동기. */
    public static ExchangeResponse accepted(
            ExchangeRequest request, AccountLedger accountLedger, BigDecimal accountBalance) {
        return ExchangeResponse.builder()
                .transactionUuid(request.transactionUuid())
                .bankTransactionId(accountLedger.getId())
                .status("SUCCESS")
                .accountBalance(accountBalance)
                .build();
    }

    /** 멱등 재요청 응답: account_ledger 기준으로 재구성 */
    public static ExchangeResponse from(
            String transactionUuid, AccountLedger accountLedger, BigDecimal accountBalance) {
        return ExchangeResponse.builder()
                .transactionUuid(transactionUuid)
                .bankTransactionId(accountLedger.getId())
                .status(accountLedger.getStatus().name())
                .accountBalance(accountBalance)
                .build();
    }
}
