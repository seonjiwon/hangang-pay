package family.fisa.hangangpay.auth.service;

import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.response.BusinessInfoResponse;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BusinessInfoQueryService {

    private static final Map<String, BusinessInfoResponse> MOCK_BUSINESS_INFOS =
            Map.of(
                    "1234567890",
                    new BusinessInfoResponse(
                            "1234567890", "성수 한강카페", "김한강", "서울 성동구 왕십리로 125", "카페"),
                    "2345678901",
                    new BusinessInfoResponse("2345678901", "뚝섬 분식", "이성수", "서울 성동구 상원길 40", "음식점"),
                    "3456789012",
                    new BusinessInfoResponse(
                            "3456789012", "서울숲 서점", "박서울", "서울 성동구 서울숲2길 32", "소매업"));

    public BusinessInfoResponse getBusinessInfo(String businessNumber) {
        String normalized = businessNumber.replace("-", "");
        BusinessInfoResponse businessInfo = MOCK_BUSINESS_INFOS.get(normalized);

        if (businessInfo == null) {
            throw new BusinessException(MerchantErrorCode.BUSINESS_INFO_NOT_FOUND);
        }

        return businessInfo;
    }
}
