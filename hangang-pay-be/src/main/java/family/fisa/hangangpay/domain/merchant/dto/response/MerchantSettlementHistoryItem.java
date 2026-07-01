package family.fisa.hangangpay.domain.merchant.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantSettlementHistoryItem(
        Long settlementId,
        BigDecimal amount,
        TransactionStatus settlementStatus,
        String settlementStatusText,
        LocalDateTime requestedAt,
        LocalDateTime completedAt)
        implements CursorItem {

    public static MerchantSettlementHistoryItem from(Transaction t) {
        return new MerchantSettlementHistoryItem(
                t.getId(),
                t.getAmount(),
                t.getStatus(),
                toStatusText(t.getStatus()),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    private static String toStatusText(TransactionStatus status) {
        return switch (status) {
            case SUCCESS -> "정산 완료";
            case PENDING -> "정산 대기";
            case PROCESSING -> "정산 처리 중";
            case FAILED -> "정산 실패";
            case UNKNOWN -> "정산 확인 중";
            case EXPIRED -> "정산 만료";
        };
    }

    @Override
    public LocalDateTime getCursorCreatedAt() {
        return requestedAt;
    }

    @Override
    public Long getCursorId() {
        return settlementId;
    }
}
