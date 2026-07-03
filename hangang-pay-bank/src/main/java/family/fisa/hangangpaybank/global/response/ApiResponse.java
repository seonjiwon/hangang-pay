package family.fisa.hangangpaybank.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import family.fisa.hangangpaybank.global.code.success.BaseSuccessCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@JsonPropertyOrder({"isSuccess", "status", "code", "message", "result"})
@JsonInclude(Include.NON_NULL)
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private Boolean isSuccess;

    @JsonProperty("status")
    private HttpStatus status;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private T result;

    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode code, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(true)
                .status(code.getStatus())
                .code(code.getCode())
                .message(code.getMessage())
                .result(result)
                .build();
    }

    public static ApiResponse<?> onFailure(BaseErrorCode code) {
        return ApiResponse.builder()
                .isSuccess(false)
                .status(code.getStatus())
                .code(code.getCode())
                .message(code.getMessage())
                .build();
    }
}
