package family.fisa.hangangpaybank.domain.transaction.service.exchange.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
import family.fisa.hangangpaybank.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeCommandServiceV1Test {

    private static final ExchangeRequest REQUEST =
            new ExchangeRequest(
                    "11111111-1111-1111-1111-111111111111",
                    1L,
                    "0x0000000000000000000000000000000000000001",
                    "1002-123-456789",
                    new BigDecimal("100"));

    @Mock private ExchangeStateWriter exchangeStateWriter;

    @InjectMocks private ExchangeCommandServiceV1 service;

    @Test
    @DisplayName("환전 성공: 실행 결과를 그대로 반환하고 보상 기록은 하지 않는다")
    void exchange_success_returnsResultWithoutCompensation() {
        ExchangeResponse executionResponse =
                ExchangeResponse.accepted(
                        REQUEST,
                        AccountLedger.builder()
                                .id(7L)
                                .idempotentKey(REQUEST.transactionUuid())
                                .build(),
                        new BigDecimal("100100"));
        given(exchangeStateWriter.executeExchange(REQUEST)).willReturn(executionResponse);

        ExchangeResponse response = service.exchange(REQUEST);

        assertThat(response).isEqualTo(executionResponse);
        verify(exchangeStateWriter, never()).saveFailedAccountLedger(REQUEST);
    }

    @Test
    @DisplayName("환전 비즈니스 실패: account_ledger FAILED 보상 저장 후 원 예외 전파")
    void exchange_businessFailure_savesFailedLedgerAndRethrows() {
        BusinessException failure =
                new BusinessException(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
        given(exchangeStateWriter.executeExchange(REQUEST)).willThrow(failure);

        assertThatThrownBy(() -> service.exchange(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);

        verify(exchangeStateWriter).saveFailedAccountLedger(REQUEST);
    }
}
