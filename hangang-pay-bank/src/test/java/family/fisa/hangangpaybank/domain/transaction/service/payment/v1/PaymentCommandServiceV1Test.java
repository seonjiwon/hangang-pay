package family.fisa.hangangpaybank.domain.transaction.service.payment.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceV1Test {

    private static final String FROM_ADDRESS = "0x000000000000000000000000000000000000aaaa";
    private static final String TO_ADDRESS = "0x000000000000000000000000000000000000bbbb";
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock private PaymentStateWriter paymentStateWriter;

    private PaymentCommandServiceV1 service;

    @BeforeEach
    void setUp() {
        service = new PaymentCommandServiceV1(paymentStateWriter);
    }

    @Test
    @DisplayName("결제 성공: state writer 실행 결과를 그대로 반환한다")
    void payment_success_returnsExecutionResult() {
        PaymentResponse executionResponse =
                PaymentResponse.from(
                        "uuid-1",
                        WalletLedgerStatus.SUCCESS,
                        LocalDateTime.now(),
                        new BigDecimal("400"),
                        new BigDecimal("100"));
        given(paymentStateWriter.executePayment(paymentRequest("uuid-1")))
                .willReturn(executionResponse);

        PaymentResponse response = service.payment(paymentRequest("uuid-1"));

        assertThat(response).isEqualTo(executionResponse);
    }

    @Test
    @DisplayName("결제 비즈니스 실패: rollback 이후 주소 기반 FAILED ledger 저장")
    void payment_businessFailure_savesFailedLedgerByAddress() {
        BusinessException failure =
                new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        given(paymentStateWriter.executePayment(paymentRequest("uuid-2"))).willThrow(failure);

        assertThatThrownBy(() -> service.payment(paymentRequest("uuid-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        verify(paymentStateWriter)
                .saveFailedWalletLedgersByAddress(
                        eq(FROM_ADDRESS), eq(TO_ADDRESS), eq("uuid-2"), eq(AMOUNT));
    }

    private PaymentRequest paymentRequest(String uuid) {
        return new PaymentRequest(uuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }
}
