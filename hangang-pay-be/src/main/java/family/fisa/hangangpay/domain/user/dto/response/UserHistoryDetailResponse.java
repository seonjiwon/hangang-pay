package family.fisa.hangangpay.domain.user.dto.response;

import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import lombok.Builder;

@Builder
public record UserHistoryDetailResponse<T>(UserHistoryType historyType, T detail) {

    public static <T> UserHistoryDetailResponse<T> of(UserHistoryType historyType, T detail) {
        return new UserHistoryDetailResponse<>(historyType, detail);
    }
}
