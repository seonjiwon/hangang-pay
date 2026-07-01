package family.fisa.hangangpay.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.account.dto.request.AccountCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/** Lombok 클래스에서 record로 전환한 요청 DTO의 JSON 역직렬화(계약) 회귀 방지 */
@JsonTest
class RequestDtoDeserializationTest {

    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("SMS/계좌 인증 요청 DTO가 JSON에서 정상 바인딩된다")
    void bindsAuthRequests() throws Exception {
        SmsSendRequest sms =
                objectMapper.readValue("{\"phoneNumber\":\"010-1234-5678\"}", SmsSendRequest.class);
        assertThat(sms.phoneNumber()).isEqualTo("010-1234-5678");

        SmsVerifyRequest smsVerify =
                objectMapper.readValue(
                        "{\"phoneNumber\":\"010-1234-5678\",\"code\":\"123456\"}",
                        SmsVerifyRequest.class);
        assertThat(smsVerify.phoneNumber()).isEqualTo("010-1234-5678");
        assertThat(smsVerify.code()).isEqualTo("123456");

        AccountSendRequest accountSend =
                objectMapper.readValue(
                        "{\"accountNumber\":\"110111222333\",\"institutionId\":20}",
                        AccountSendRequest.class);
        assertThat(accountSend.accountNumber()).isEqualTo("110111222333");
        assertThat(accountSend.institutionId()).isEqualTo(20L);

        AccountVerifyRequest accountVerify =
                objectMapper.readValue(
                        "{\"accountNumber\":\"110111222333\",\"institutionId\":20,\"code\":\"123456\"}",
                        AccountVerifyRequest.class);
        assertThat(accountVerify.accountNumber()).isEqualTo("110111222333");
        assertThat(accountVerify.institutionId()).isEqualTo(20L);
        assertThat(accountVerify.code()).isEqualTo("123456");
    }

    @Test
    @DisplayName("계좌 등록 요청 DTO가 JSON에서 정상 바인딩된다")
    void bindsAccountCreateRequest() throws Exception {
        AccountCreateRequest request =
                objectMapper.readValue(
                        "{\"institutionCode\":\"WR\",\"accountNumber\":\"1002123456789\"}",
                        AccountCreateRequest.class);
        assertThat(request.institutionCode()).isEqualTo("WR");
        assertThat(request.accountNumber()).isEqualTo("1002123456789");
    }
}
