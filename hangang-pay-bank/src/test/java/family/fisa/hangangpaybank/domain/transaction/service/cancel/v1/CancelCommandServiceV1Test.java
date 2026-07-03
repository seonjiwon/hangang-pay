package family.fisa.hangangpaybank.domain.transaction.service.cancel.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
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
class CancelCommandServiceV1Test {

    private static final String FROM_ADDRESS = "0x000000000000000000000000000000000000aaaa";
    private static final String TO_ADDRESS = "0x000000000000000000000000000000000000bbbb";
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock private PaymentStateWriter paymentStateWriter;

    private CancelCommandServiceV1 service;

    @BeforeEach
    void setUp() {
        service = new CancelCommandServiceV1(paymentStateWriter);
    }

    @Test
    @DisplayName("취소 성공: state writer 실행 결과를 그대로 반환한다")
    void cancel_success_returnsExecutionResult() {
        CancelResponse executionResponse =
                CancelResponse.from(
                        "uuid-6",
                        "orig-1",
                        WalletLedgerStatus.SUCCESS,
                        LocalDateTime.now(),
                        new BigDecimal("400"),
                        new BigDecimal("100"));
        given(paymentStateWriter.executeCancel(cancelRequest("uuid-6", "orig-1")))
                .willReturn(executionResponse);

        CancelResponse response = service.cancel(cancelRequest("uuid-6", "orig-1"));

        assertThat(response).isEqualTo(executionResponse);
    }

    @Test
    @DisplayName("취소 비즈니스 실패: rollback 이후 주소 기반 FAILED ledger 저장")
    void cancel_businessFailure_savesFailedLedgerByAddress() {
        BusinessException failure =
                new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        given(paymentStateWriter.executeCancel(cancelRequest("uuid-7", "orig-2")))
                .willThrow(failure);

        assertThatThrownBy(() -> service.cancel(cancelRequest("uuid-7", "orig-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        verify(paymentStateWriter)
                .saveFailedWalletLedgersByAddress(
                        eq(FROM_ADDRESS), eq(TO_ADDRESS), eq("uuid-7"), eq(AMOUNT));
    }

    private CancelRequest cancelRequest(String uuid, String originalUuid) {
        return new CancelRequest(uuid, originalUuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }
}
