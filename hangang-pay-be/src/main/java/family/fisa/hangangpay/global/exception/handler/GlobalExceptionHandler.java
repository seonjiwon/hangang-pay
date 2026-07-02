package family.fisa.hangangpay.global.exception.handler;

import family.fisa.hangangpay.client.bank.BankErrorInterpreter;
import family.fisa.hangangpay.client.bank.exception.BankError;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.response.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final BankErrorInterpreter bankErrorInterpreter;

    /** Business Exception 처리 */
    @ExceptionHandler(BusinessException.class)
    private ResponseEntity<ApiResponse<?>> handleCustomException(BusinessException ex) {
        // 1. 에러 로그 찍기
        log.warn("[ CustomException ]: {}", ex.getCode().getMessage());

        // 2. errorCode 추출
        BaseErrorCode errorCode = ex.getCode();

        // 3. Response 생성
        ApiResponse<?> errorResponse = ApiResponse.onFailure(errorCode);

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }

    /** 은행 연동 실패를 정규화된 에러 코드로 변환 (BankErrorInterpreter가 status·bank code로 판정) */
    @ExceptionHandler(BankException.class)
    public ResponseEntity<ApiResponse<?>> handleBankException(BankException ex) {
        BankError error = ex.getError();
        log.warn("Bank call failed. status={}, code={}", error.status(), error.code());

        BaseErrorCode errorCode = bankErrorInterpreter.toResponseCode(error);
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.onFailure(errorCode));
    }

    /**
     * @Valid 유효성 검사 실패시 발생하는 예외
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    private ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        // 1. 각 에러 담기
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        // 2. Response 생성
        BaseErrorCode errorCode = GeneralErrorCode.COMMON_VALIDATION_FAILED;
        ApiResponse<Map<String, String>> errorResponse = ApiResponse.onFailure(errorCode, errors);

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }

    /** 예상치 못한 예외 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneralException(Exception ex) {
        log.error("Internal Server Error", ex);

        BaseErrorCode errorCode = GeneralErrorCode.COMMON_INTERNAL_SERVER_ERROR;

        // 1. errorResponse 생성
        ApiResponse<String> errorResponse = ApiResponse.onFailure(errorCode, null);

        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }

    /** 내역조회 시 유효하지 않은 타입에 대한 예외 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure(GeneralErrorCode.COMMON_INVALID_HISTORY_TYPE));
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.onFailure(GeneralErrorCode.COMMON_BAD_REQUEST));
    }
}
