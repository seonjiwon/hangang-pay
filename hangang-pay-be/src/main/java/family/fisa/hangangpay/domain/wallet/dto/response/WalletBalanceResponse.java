package family.fisa.hangangpay.domain.wallet.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 지갑 잔액 조회 응답 DTO */
public record WalletBalanceResponse(
        /** 지갑 주소 */
        String walletAddress,
        /** 잔액 */
        BigDecimal balance,
        /** 화폐 단위 */
        String unit,
        /** 지갑 마지막 업데이트 시각 */
        LocalDateTime updatedAt) {}
