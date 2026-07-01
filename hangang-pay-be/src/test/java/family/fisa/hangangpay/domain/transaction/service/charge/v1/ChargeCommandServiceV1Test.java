package family.fisa.hangangpay.domain.transaction.service.charge.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.ChargeResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.IntentCreationGuard;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeStateWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ChargeCommandServiceV1Test {

    @Mock BankClient bankClient;
    @Mock ChargeIdempotencyStore chargeIdempotencyStore;
    @Mock ChargeStateWriter chargeStateWriter;
    @Mock IntentCreationGuard intentCreationGuard;

    @InjectMocks ChargeCommandServiceV1 service;

    private static final Long PARTY_ID = 10L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";

    private ChargeExecutionPrepared prepared() {
        return new ChargeExecutionPrepared(
                UUID,
                "hash",
                1L,
                "110-1234-5678",
                "0xabc",
                new BigDecimal("45000"),
                new BigDecimal("50000"));
    }

    private ChargeExecuteResponse response() {
        return new ChargeExecuteResponse(
                PARTY_ID,
                100L,
                new BigDecimal("50000"),
                new BigDecimal("45000"),
                new BigDecimal("45000"),
                LocalDateTime.now());
    }

    @Test
    @DisplayName("createIntent -> writer 위임")
    void createIntent_위임() {
        ChargeIntentCreateRequest request =
                new ChargeIntentCreateRequest(1L, 1L, new BigDecimal("50000"));
        ChargeIntentResponse expected =
                new ChargeIntentResponse(
                        UUID,
                        TransactionStatus.PENDING,
                        new BigDecimal("50000"),
                        new BigDecimal("45000"),
                        "110-1234-5678",
                        "우리은행",
                        LocalDateTime.now());
        when(chargeStateWriter.createIntent(eq(PARTY_ID), eq(request), any())).thenReturn(expected);

        ChargeIntentResponse result = service.createIntent(PARTY_ID, request);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute 성공 -> completeSuccess + 멱등성 완료")
    void execute_성공() {
        when(chargeStateWriter.prepareProcessing(PARTY_ID, UUID, "1234"))
                .thenReturn(ChargeExecutionPreparationResult.prepared(prepared()));
        ChargeResponse bankResponse =
                new ChargeResponse(
                        UUID, 999L, "0xhash", 1L, LocalDateTime.now(), new BigDecimal("45000"));
        when(bankClient.charge(any())).thenReturn(bankResponse);
        ChargeExecuteResponse expected = response();
        when(chargeStateWriter.completeSuccess(
                        eq(UUID), eq("0xhash"), eq("999"), any(), eq(new BigDecimal("45000"))))
                .thenReturn(expected);

        ChargeExecuteResponse result =
                service.execute(PARTY_ID, UUID, new ChargeExecuteRequest("1234"));

        assertThat(result).isEqualTo(expected);
        verify(chargeIdempotencyStore).completeExecution(UUID, expected);
    }

    @Test
    @DisplayName("execute 멱등 재요청(snapshot) -> 은행 호출 없이 snapshot 반환")
    void execute_멱등_snapshot() {
        ChargeExecuteResponse snapshot = response();
        when(chargeStateWriter.prepareProcessing(PARTY_ID, UUID, "1234"))
                .thenReturn(ChargeExecutionPreparationResult.snapshot(snapshot));

        ChargeExecuteResponse result =
                service.execute(PARTY_ID, UUID, new ChargeExecuteRequest("1234"));

        assertThat(result).isEqualTo(snapshot);
        verify(bankClient, org.mockito.Mockito.never()).charge(any());
    }

    @Test
    @DisplayName("execute 은행 네트워크 오류 -> markUnknown + 멱등 상태 UNKNOWN")
    void execute_네트워크오류() {
        when(chargeStateWriter.prepareProcessing(PARTY_ID, UUID, "1234"))
                .thenReturn(ChargeExecutionPreparationResult.prepared(prepared()));
        when(bankClient.charge(any())).thenThrow(new ResourceAccessException("timeout"));
        ChargeExecuteResponse unknown = response();
        when(chargeStateWriter.markUnknown(UUID)).thenReturn(unknown);

        ChargeExecuteResponse result =
                service.execute(PARTY_ID, UUID, new ChargeExecuteRequest("1234"));

        assertThat(result).isEqualTo(unknown);
        verify(chargeIdempotencyStore).markExecutionStatus(UUID, TransactionStatus.UNKNOWN);
    }
}
