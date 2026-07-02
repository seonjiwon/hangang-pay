package family.fisa.hangangpay.domain.merchant.controller;

import family.fisa.hangangpay.client.bank.dto.response.MerchantRedeemInitResponse;
import family.fisa.hangangpay.domain.account.dto.request.MerchantAccountUpdateRequest;
import family.fisa.hangangpay.domain.account.dto.response.MerchantAccountUpdateResponse;
import family.fisa.hangangpay.domain.account.service.AccountCommandService;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantDashboardResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantInfoResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantMyPageResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantPaymentDetailResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantQrResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantSettlementHistoryItem;
import family.fisa.hangangpay.domain.merchant.service.MerchantQrService;
import family.fisa.hangangpay.domain.merchant.service.MerchantQueryService;
import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeIntentResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.MerchantPaymentDetail;
import family.fisa.hangangpay.domain.transaction.dto.user.response.MerchantPaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelCommandService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelReconcileService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeCommandService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeQueryService;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
@Tag(name = "Merchant", description = "가맹점 API")
public class MerchantController {

    private final MerchantQrService qrService;
    private final MerchantQueryService merchantQueryService;
    private final AccountCommandService accountCommandService;
    private final PaymentQueryService paymentQueryService;
    private final ExchangeQueryService exchangeQueryService;
    private final ExchangeCommandService exchangeCommandService;
    private final CancelCommandService cancelCommandService;
    private final CancelReconcileService cancelReconcileService;

    /** QR에서 추출한 merchantId로 결제 진입에 필요한 가맹점 정보를 조회한다. */
    @Operation(
            summary = "QR 가맹점 정보 조회 (PAY-001)",
            description = "QR에서 추출한 merchantId로 서버 DB의 신뢰된 가맹점 정보(이름, 주소, 지갑 주소)를 조회한다.")
    @GetMapping("/{merchantId}")
    public ResponseEntity<ApiResponse<MerchantInfoResponse>> getMerchantInfo(
            @PathVariable Long merchantId) {
        MerchantInfoResponse response = merchantQueryService.getMerchantInfo(merchantId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    /** 가맹점의 정산 내역을 조회한다. */
    @Operation(summary = "가맹점 정산 내역 조회 (MERCHANT-005)")
    @GetMapping("/settlements")
    public ResponseEntity<ApiResponse<CursorPageResponse<MerchantSettlementHistoryItem>>>
            getMerchantSettlements(
                    @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
                    CursorPageRequest cursor,
                    @RequestParam(defaultValue = "20") int size) {

        CursorPageResponse<MerchantSettlementHistoryItem> page =
                exchangeQueryService.getMerchantSettlementHistory(partyId, cursor, size);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, page));
    }

    /** 가맹점 대시보드용 매출 요약을 조회한다. */
    @Operation(summary = "가맹점 매출 요약 조회 (MERCHANT-001)")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<MerchantDashboardResponse>> getDashboard(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        MerchantDashboardResponse response = merchantQueryService.getDashboard(partyId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    /** 가맹점의 결제 내역을 조회한다. */
    @Operation(summary = "가맹점 결제 내역 조회 (MERCHANT-002)")
    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<CursorPageResponse<MerchantPaymentHistoryItem>>>
            getMerchantPayments(
                    @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
                    CursorPageRequest cursor,
                    @RequestParam(defaultValue = "20") int size) {

        CursorPageResponse<MerchantPaymentHistoryItem> page =
                paymentQueryService.getMerchantPaymentHistory(partyId, cursor, size);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, page));
    }

    /** 가맹점의 결제 상세 내역을 조회한다. */
    @Operation(summary = "가맹점 결제 상세 조회 (MERCHANT-003)")
    @GetMapping("/payments/{transactionId}")
    public ResponseEntity<ApiResponse<MerchantPaymentDetailResponse<MerchantPaymentDetail>>>
            getMerchantPaymentDetail(
                    @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
                    @PathVariable Long transactionId) {

        MerchantPaymentDetailResponse<MerchantPaymentDetail> response =
                paymentQueryService.getMerchantPaymentDetail(partyId, transactionId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    /** 현재 세션 가맹점의 보유 토큰 잔액과 SETTLEMENT 계좌 정보를 조회한다. */
    @Operation(
            summary = "가맹점 정산 신청 조회 (MERCHANT-006)",
            description = "현재 세션 가맹점의 보유 토큰 잔액(availableAmount)과 SETTLEMENT 계좌 정보를 반환한다.")
    @GetMapping("/redeem")
    public ResponseEntity<ApiResponse<MerchantRedeemInitResponse>> getMerchantRedeemInit(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        MerchantRedeemInitResponse response = merchantQueryService.getRedeemInit(partyId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    /** 가맹점 정산 의도 생성 (PIN 없음) */
    @Operation(
            summary = "가맹점 정산 의도 생성 (MERCHANT-007)",
            description = "보유 토큰을 환전할 PENDING 의도를 생성한다.")
    @PostMapping("/redeem/intents")
    public ResponseEntity<ApiResponse<ExchangeIntentResponse>> createMerchantRedeemIntent(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @Valid @RequestBody ExchangeIntentCreateRequest request) {
        ExchangeIntentResponse response =
                exchangeCommandService.createMerchantIntent(partyId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.EXCHANGE_INTENT_CREATED, response));
    }

    /** 가맹점 정산 실행 (PIN) */
    @Operation(summary = "가맹점 정산 실행 (MERCHANT-008)", description = "PIN 검증 후 정산 의도를 1:1로 계좌 환전한다.")
    @PostMapping("/redeem/{transactionUuid}/execute")
    public ResponseEntity<ApiResponse<ExchangeExecuteResponse>> executeMerchantRedeem(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @PathVariable String transactionUuid,
            @Valid @RequestBody ExchangeExecuteRequest request) {
        ExchangeExecuteResponse response =
                exchangeCommandService.executeMerchantExchange(partyId, transactionUuid, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.EXCHANGE_EXECUTED, response));
    }

    /** 현재 세션 가맹점의 결제용 QR을 조회한다. */
    @Operation(
            summary = "가맹점 QR 조회 (MERCHANT-009)",
            description = "현재 세션의 가맹점 식별값(merchantId, partyId)을 담은 PNG QR을 base64로 반환한다.")
    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<MerchantQrResponse>> getMerchantQr(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        MerchantQrResponse response = qrService.getQrForPartyId(partyId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(summary = "가맹점 마이페이지 조회 (MERCHANT-010)")
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponse<MerchantMyPageResponse>> getMyPage(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {

        MerchantMyPageResponse merchantMyPageResponse = merchantQueryService.getMyPage(partyId);

        return ResponseEntity.ok(
                ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, merchantMyPageResponse));
    }

    @Operation(summary = "가맹점 계좌 변경 (MERCHANT-011)")
    @PatchMapping("/accounts")
    public ResponseEntity<ApiResponse<MerchantAccountUpdateResponse>> updateAccount(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @Valid @RequestBody MerchantAccountUpdateRequest request) {

        MerchantAccountUpdateResponse response =
                accountCommandService.updateMerchantSettlementAccount(partyId, request);

        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(
            summary = "결제 취소 (MERCHANT-004)",
            description = "가맹점이 본인 결제 건을 PIN 인증 후 취소한다. 시간 제한 없음.")
    @PostMapping("/payments/{transactionId}/cancel")
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> executeCancel(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @PathVariable Long transactionId,
            @Valid @RequestBody PaymentCancelRequest request) {
        PaymentCancelResponse response =
                cancelCommandService.executeCancel(partyId, transactionId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.PAYMENT_CANCELLED, response));
    }

    @Operation(
            summary = "결제 취소 복구 (MERCHANT-004-R)",
            description = "UNKNOWN 상태의 취소 건을 Bank 상태 조회로 복구한다.")
    @PostMapping("/payments/{transactionId}/cancel/recover")
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> reconcileCancel(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @PathVariable Long transactionId) {
        PaymentCancelResponse response =
                cancelReconcileService.reconcileCancel(partyId, transactionId);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.CANCEL_RECOVERED, response));
    }
}
