package family.fisa.hangangpay.domain.merchant.dto.response;

import lombok.Builder;

/** 가맹점 QR 조회 응답 */
@Builder
public record MerchantQrResponse(String qrImageBase64) {

    public static MerchantQrResponse of(String qrImageBase64) {
        return MerchantQrResponse.builder().qrImageBase64(qrImageBase64).build();
    }
}
