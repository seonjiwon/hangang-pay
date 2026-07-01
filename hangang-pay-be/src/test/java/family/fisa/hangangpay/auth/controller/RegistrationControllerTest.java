package family.fisa.hangangpay.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import family.fisa.hangangpay.auth.dto.response.UserRegisterResponse;
import family.fisa.hangangpay.auth.service.BusinessInfoQueryService;
import family.fisa.hangangpay.auth.service.MerchantRegistrationService;
import family.fisa.hangangpay.auth.service.UserRegistrationService;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.response.BusinessInfoResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantRegisterResponse;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.exception.handler.GlobalExceptionHandler;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RegistrationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserRegistrationService userRegistrationService;
    @MockitoBean private MerchantRegistrationService merchantRegistrationService;
    @MockitoBean private BusinessInfoQueryService businessInfoQueryService;

    @Test
    @DisplayName("소비자 회원가입을 완료한다")
    void registerUser() throws Exception {
        given(userRegistrationService.register(any(), any(HttpSession.class)))
                .willReturn(new UserRegisterResponse(10L, 20L));

        mockMvc.perform(
                        post("/api/v1/auth/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {
                      "name": "홍길동",
                      "birthDate": "1990-07-30",
                      "phoneNumber": "010-1234-5678",
                      "password": "abc123!@",
                      "paymentPin": "123456",
                      "institutionId": 1,
                      "accountNumber": "1002123456789",
                      "termsAgreed": {
                        "serviceTerms": true,
                        "privacyTerms": true,
                        "electronicFinanceTerms": true,
                        "localCurrencyTerms": true
                      }
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.code").value("USER_REGISTERED"))
                .andExpect(jsonPath("$.result.partyId").value(10))
                .andExpect(jsonPath("$.result.userId").value(20));
    }

    @Test
    @DisplayName("사업자 정보를 조회한다")
    void getBusinessInfo() throws Exception {
        given(businessInfoQueryService.getBusinessInfo("123-45-67890"))
                .willReturn(
                        new BusinessInfoResponse(
                                "123-45-67890", "성수 한강카페", "김한강", "서울 성동구 왕십리로 125", "카페"));

        mockMvc.perform(
                        get("/api/v1/auth/merchants/business-info")
                                .param("businessNumber", "123-45-67890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.businessNumber").value("123-45-67890"))
                .andExpect(jsonPath("$.result.merchantName").value("성수 한강카페"))
                .andExpect(jsonPath("$.result.ownerName").value("김한강"));
    }

    @Test
    @DisplayName("사업자 정보를 찾을 수 없으면 실패 응답을 반환한다")
    void getBusinessInfoNotFound() throws Exception {
        given(businessInfoQueryService.getBusinessInfo("000-00-00000"))
                .willThrow(new BusinessException(MerchantErrorCode.BUSINESS_INFO_NOT_FOUND));

        mockMvc.perform(
                        get("/api/v1/auth/merchants/business-info")
                                .param("businessNumber", "000-00-00000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("BUSINESS_INFO_NOT_FOUND"));
    }

    @Test
    @DisplayName("가맹점 회원가입을 완료한다")
    void registerMerchant() throws Exception {
        given(merchantRegistrationService.register(any(), any(HttpSession.class)))
                .willReturn(new MerchantRegisterResponse(1L, 2L, "성수 한강카페"));

        mockMvc.perform(
                        post("/api/v1/auth/merchants/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {
                      "businessNumber": "123-45-67890",
                      "username": "hangangcafe",
                      "password": "abc123!@",
                      "paymentPin": "123456",
                      "institutionId": 1,
                      "accountNumber": "1002123456789",
                      "phoneNumber": "01012345678",
                      "termsAgreed": {
                        "serviceTerms": true,
                        "privacyTerms": true,
                        "electronicFinanceTerms": true,
                        "localCurrencyTerms": true
                      }
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("MERCHANT_REGISTERED"))
                .andExpect(jsonPath("$.result.partyId").value(1))
                .andExpect(jsonPath("$.result.merchantId").value(2))
                .andExpect(jsonPath("$.result.merchantName").value("성수 한강카페"));
    }
}
