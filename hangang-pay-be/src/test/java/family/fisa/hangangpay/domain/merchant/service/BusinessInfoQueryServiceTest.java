package family.fisa.hangangpay.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import family.fisa.hangangpay.auth.service.BusinessInfoQueryService;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.response.BusinessInfoResponse;
import family.fisa.hangangpay.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessInfoQueryServiceTest {

    private final BusinessInfoQueryService businessInfoQueryService =
            new BusinessInfoQueryService();

    @Test
    @DisplayName("mock 사업자 정보에서 사업자번호로 정보를 조회한다")
    void getBusinessInfo() {
        BusinessInfoResponse response = businessInfoQueryService.getBusinessInfo("123-45-67890");

        assertThat(response.businessNumber()).isEqualTo("1234567890");
        assertThat(response.merchantName()).isEqualTo("성수 한강카페");
        assertThat(response.ownerName()).isEqualTo("김한강");
        assertThat(response.address()).isEqualTo("서울 성동구 왕십리로 125");
        assertThat(response.businessType()).isEqualTo("카페");
    }

    @Test
    @DisplayName("mock 사업자 정보에 없으면 예외를 던진다")
    void getBusinessInfoNotFound() {
        assertThatThrownBy(() -> businessInfoQueryService.getBusinessInfo("000-00-00000"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(MerchantErrorCode.BUSINESS_INFO_NOT_FOUND);
    }
}
