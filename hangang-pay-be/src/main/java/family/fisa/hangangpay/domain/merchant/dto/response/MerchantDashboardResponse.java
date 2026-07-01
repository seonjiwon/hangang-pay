package family.fisa.hangangpay.domain.merchant.dto.response;

import java.math.BigDecimal;

public record MerchantDashboardResponse(
        BigDecimal todaySales,
        long todayCount,
        BigDecimal pendingSettlement,
        BigDecimal monthlyTotalSales) {}
