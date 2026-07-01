package family.fisa.hangangpay.domain.user.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import family.fisa.hangangpay.domain.transaction.service.charge.ChargeQueryService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeQueryService;
import family.fisa.hangangpay.domain.transaction.service.history.HistoryQueryService;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentQueryService;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.dto.response.UserProfileResponse;
import family.fisa.hangangpay.domain.user.service.UserQueryService;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.exception.handler.GlobalExceptionHandler;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserQueryService userQueryService;
    @MockitoBean private PaymentQueryService paymentQueryService;
    @MockitoBean private ChargeQueryService chargeQueryService;
    @MockitoBean private ExchangeQueryService exchangeQueryService;
    @MockitoBean private HistoryQueryService historyQueryService;

    @Test
    @DisplayName("마이페이지 프로필을 조회한다")
    void getProfile() throws Exception {
        given(userQueryService.getProfile(1L))
                .willReturn(
                        new UserProfileResponse(
                                1L, 1L, "유승준", "01041301904", LocalDate.now(), "서대문구"));

        mockMvc.perform(
                        get("/api/v1/users/profile")
                                .sessionAttr(SessionAttributeNames.PARTY_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.code").value("COMMON_OK"))
                .andExpect(jsonPath("$.result.userId").value(1))
                .andExpect(jsonPath("$.result.partyId").value(1))
                .andExpect(jsonPath("$.result.username").value("유승준"))
                .andExpect(jsonPath("$.result.phoneNumber").value("01041301904"))
                .andExpect(jsonPath("$.result.region").value("서대문구"));
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 공통 실패 응답을 반환한다")
    void getProfileUserNotFound() throws Exception {
        given(userQueryService.getProfile(999L))
                .willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(
                        get("/api/v1/users/profile")
                                .sessionAttr(SessionAttributeNames.PARTY_ID, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다"));
    }
}
