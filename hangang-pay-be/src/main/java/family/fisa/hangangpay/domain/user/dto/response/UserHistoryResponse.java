package family.fisa.hangangpay.domain.user.dto.response;

import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;

public record UserHistoryResponse<T>(UserHistoryType historyType, CursorPageResponse<T> response) {

    public static <T> UserHistoryResponse<T> of(
            UserHistoryType type, CursorPageResponse<T> response) {
        return new UserHistoryResponse<>(type, response);
    }
}
