package family.fisa.hangangpay.client.bank.dto.request;

import java.math.BigDecimal;

public record ChargeRequest(
        String transactionUuid,
        Long institutionId,
        String accountNumber,
        String walletAddress,
        BigDecimal amount, // 계좌 차감 금액 (실 결제 금액)
        BigDecimal mintAmount) // 지갑 mint 금액 (충전가)
{}
