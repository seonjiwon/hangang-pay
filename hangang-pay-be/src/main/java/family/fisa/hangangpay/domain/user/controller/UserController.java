package family.fisa.hangangpay.domain.user.controller;

import static family.fisa.hangangpay.domain.user.dto.UserHistoryType.*;

import family.fisa.hangangpay.domain.transaction.dto.user.response.AllHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeQueryService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeQueryService;
import family.fisa.hangangpay.domain.transaction.service.history.HistoryQueryService;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentQueryService;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.domain.user.dto.response.UserHistoryDetailResponse;
import family.fisa.hangangpay.domain.user.dto.response.UserHistoryResponse;
import family.fisa.hangangpay.domain.user.dto.response.UserProfileResponse;
import family.fisa.hangangpay.domain.user.service.UserQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Tag(name = "마이페이지", description = "소비자 프로필 및 결제 내역 조회")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;
    private final PaymentQueryService paymentQueryService;
    private final ChargeQueryService chargeQueryService;
    private final ExchangeQueryService exchangeQueryService;
    private final HistoryQueryService historyQueryService;

    @Operation(summary = "프로필 조회 (MY-001)", description = "로그인한 소비자의 닉네임, 지역, 가입일을 반환한다.")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        UserProfileResponse response = userQueryService.getProfile(partyId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(summary = "소비자 내역 조회 (MY-002)", description = "결제, 충전, 환전에 대한 모든 조회를 한번에 처리한다.")
    @GetMapping("/histories")
    public ResponseEntity<ApiResponse<UserHistoryResponse<?>>> getHistories(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @RequestParam UserHistoryType type,
            @RequestParam(defaultValue = "20") int size,
            CursorPageRequest cursor) {
        UserHistoryResponse<?> result =
                switch (type) {
                    case ALL -> {
                        CursorPageResponse<AllHistoryItem> page =
                                historyQueryService.getAllHistories(partyId, cursor, size);
                        yield UserHistoryResponse.of(ALL, page);
                    }
                    case PAYMENT, CANCEL -> {
                        CursorPageResponse<PaymentHistoryItem> page =
                                paymentQueryService.getUserPaymentHistory(partyId, cursor, size);
                        yield UserHistoryResponse.of(PAYMENT, page);
                    }
                    case CHARGE -> {
                        CursorPageResponse<ChargeHistoryItem> page =
                                chargeQueryService.getChargeHistories(partyId, cursor, size);
                        yield UserHistoryResponse.of(CHARGE, page);
                    }
                    case EXCHANGE -> {
                        CursorPageResponse<ExchangeHistoryItem> page =
                                exchangeQueryService.getExchangeHistories(partyId, cursor, size);
                        yield UserHistoryResponse.of(EXCHANGE, page);
                    }
                };
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, result));
    }

    @Operation(
            summary = "소비자 내역 상세 조회 (MY-003)",
            description = "결제(PAYMENT)/충전(CHARGE)/환전(EXCHANGE) 내역의 상세 정보를 조회한다.")
    @GetMapping("/histories/{historyId}")
    public ResponseEntity<ApiResponse<UserHistoryDetailResponse<?>>> getDetailHistory(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @PathVariable Long historyId,
            @RequestParam UserHistoryType type) {
        UserHistoryDetailResponse<?> result =
                switch (type) {
                    case PAYMENT, CANCEL ->
                            UserHistoryDetailResponse.of(
                                    type,
                                    paymentQueryService.getUserPaymentHistoryDetail(
                                            partyId, historyId));
                    case CHARGE ->
                            UserHistoryDetailResponse.of(
                                    type,
                                    chargeQueryService.getUserChargeHistoryDetail(
                                            partyId, historyId));
                    case EXCHANGE ->
                            UserHistoryDetailResponse.of(
                                    type,
                                    exchangeQueryService.getUserExchangeHistoryDetail(
                                            partyId, historyId));
                    case ALL -> throw new BusinessException(UserErrorCode.INVALID_HISTORY_TYPE);
                };

        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, result));
    }
}
