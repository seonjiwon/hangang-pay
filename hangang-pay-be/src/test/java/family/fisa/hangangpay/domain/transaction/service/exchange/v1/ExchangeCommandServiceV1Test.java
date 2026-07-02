package family.fisa.hangangpay.domain.transaction.service.exchange.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.request.ExchangeRequest;
import family.fisa.hangangpay.client.bank.dto.response.ExchangeResponse;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.IntentCreationGuard;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeQueryService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpay.domain.transaction.service.support.BankCallExecutor;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ExchangeCommandServiceV1Test {

    @Mock ExchangeQueryService exchangeQueryService;
    @Mock ExchangeStateWriter stateWriter;
    @Mock BankClient bankClient;
    @Mock BankCallExecutor bankCallExecutor;
    @Mock UserRepository userRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ExchangeIdempotencyStore idempotencyStore;
    @Mock ExchangeRequestHashGenerator requestHashGenerator;
    @Mock IntentCreationGuard intentCreationGuard;

    @InjectMocks ExchangeCommandServiceV1 exchangeCommandService;

    private static final Long PARTY_ID = 10L;
    private static final Long TRANSACTION_ID = 100L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TX_HASH = "0xabc123";
    private static final Long BANK_TX_ID = 999L;
    private static final String BANK_TX_ID_STR = "999";
    private static final String PIN = "123456";

    private ExchangeIntentCreateRequest intentRequest() {
        return new ExchangeIntentCreateRequest(new BigDecimal("50000"));
    }

    private ExchangeExecuteRequest executeRequest() {
        return new ExchangeExecuteRequest(PIN);
    }

    private ExchangeIntentResponse intentResponse() {
        return new ExchangeIntentResponse(
                UUID,
                TransactionStatus.PENDING,
                new BigDecimal("50000"),
                "110-1234-5678",
                "우리은행",
                LocalDateTime.now());
    }

    private ExchangeExecuteResponse response(TransactionStatus status) {
        return ExchangeExecuteResponse.builder()
                .transactionId(TRANSACTION_ID)
                .transactionUuid(UUID)
                .amount(new BigDecimal("50000"))
                .accountNumber("110-1234-5678")
                .bankName("우리은행")
                .txHash(status == TransactionStatus.SUCCESS ? TX_HASH : null)
                .status(status)
                .exchangedAt(LocalDateTime.now())
                .build();
    }

    private ExchangeRequest bankRequest() {
        return new ExchangeRequest(UUID, 1L, "0xabc", "110-1234", new BigDecimal("50000"));
    }

    private ExchangeResponse bankResponse() {
        return new ExchangeResponse(UUID, BANK_TX_ID, "SUCCESS", new BigDecimal("50000"));
    }

    /** executor가 특정 BankOutcome을 반환하도록 스텁 (실제 bank 호출/재시도는 executor 단위테스트가 담당) */
    private void stubBankOutcome(BankOutcome<ExchangeResponse> outcome) {
        when(bankCallExecutor.<ExchangeResponse>callBankWithRetry(any(), any(), any()))
                .thenReturn(outcome);
    }

    private void stubUserPinPass() {
        User user = mock(User.class);
        when(userRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
    }

    private void stubMerchantPinPass() {
        Merchant merchant = mock(Merchant.class);
        when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(merchant));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
    }

    private void stubGate(ExchangeIdempotencyDecision decision) {
        when(idempotencyStore.beginExecution(eq(UUID), any())).thenReturn(decision);
    }

    // ── intent 생성 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("intent 생성")
    class CreateIntent {

        @Test
        @DisplayName("user: 자격 통과 -> createIntent(PRIMARY) 호출")
        void user_정상() {
            when(exchangeQueryService.checkEligibility(PARTY_ID)).thenReturn(true);
            ExchangeIntentResponse expected = intentResponse();
            when(stateWriter.createIntent(
                            eq(PARTY_ID),
                            any(ExchangeIntentCreateRequest.class),
                            eq(AccountType.PRIMARY),
                            any(LocalDateTime.class)))
                    .thenReturn(expected);

            ExchangeIntentResponse out =
                    exchangeCommandService.createUserIntent(PARTY_ID, intentRequest());

            assertThat(out).isSameAs(expected);
        }

        @Test
        @DisplayName("user: 자격 미달 -> EXCHANGE_NOT_ELIGIBLE, createIntent 호출 안 함")
        void user_자격미달() {
            when(exchangeQueryService.checkEligibility(PARTY_ID)).thenReturn(false);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.createUserIntent(
                                            PARTY_ID, intentRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_ELIGIBLE);

            verify(stateWriter, never()).createIntent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("merchant: 자격 검증 없이 createIntent(SETTLEMENT)")
        void merchant_정상() {
            ExchangeIntentResponse expected = intentResponse();
            when(stateWriter.createIntent(
                            eq(PARTY_ID),
                            any(ExchangeIntentCreateRequest.class),
                            eq(AccountType.SETTLEMENT),
                            any(LocalDateTime.class)))
                    .thenReturn(expected);

            ExchangeIntentResponse out =
                    exchangeCommandService.createMerchantIntent(PARTY_ID, intentRequest());

            assertThat(out).isSameAs(expected);
            verify(exchangeQueryService, never()).checkEligibility(any());
        }
    }

    // ── 실행: 게이트 분기 ───────────────────────────────────────────────────

    @Nested
    @DisplayName("실행 - 멱등 게이트")
    class Gate {

        @Test
        @DisplayName("RETURN_SNAPSHOT -> 스냅샷 반환, claim/bank 호출 안 함")
        void 스냅샷() {
            stubUserPinPass();
            ExchangeExecuteResponse snapshot = response(TransactionStatus.SUCCESS);
            stubGate(ExchangeIdempotencyDecision.returnSnapshot(snapshot));

            ExchangeExecuteResponse out =
                    exchangeCommandService.executeUserExchange(PARTY_ID, UUID, executeRequest());

            assertThat(out).isSameAs(snapshot);
            verify(stateWriter, never()).claimForExecution(any());
            verify(bankCallExecutor, never()).callBankWithRetry(any(), any(), any());
        }

        @Test
        @DisplayName("ALREADY_FAILED -> EXCHANGE_ALREADY_FAILED")
        void 이미_실패() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.alreadyFailed());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, UUID, executeRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_ALREADY_FAILED);
        }

        @Test
        @DisplayName("PROCESSING -> EXCHANGE_IN_PROGRESS")
        void 진행중() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.processing());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, UUID, executeRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_IN_PROGRESS);
        }
    }

    // ── 실행: 본 흐름 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("실행 - 본 흐름")
    class Execute {

        @Test
        @DisplayName("PENDING 선점 + bank SUCCESS -> markSuccess + completeExecution")
        void 정상_성공() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(stateWriter.claimForExecution(UUID)).thenReturn(TransactionStatus.PROCESSING);
            when(stateWriter.getBankRequest(UUID)).thenReturn(bankRequest());
            stubBankOutcome(BankOutcome.success(bankResponse()));
            ExchangeExecuteResponse resp = response(TransactionStatus.SUCCESS);
            when(stateWriter.markSuccess(UUID, null, BANK_TX_ID_STR)).thenReturn(resp);

            ExchangeExecuteResponse out =
                    exchangeCommandService.executeUserExchange(PARTY_ID, UUID, executeRequest());

            assertThat(out).isSameAs(resp);
            verify(idempotencyStore).completeExecution(UUID, resp);
            verify(idempotencyStore, never()).failExecution(any());
        }

        @Test
        @DisplayName("bank TERMINAL_FAILED -> markFailed + failExecution")
        void 실패() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(stateWriter.claimForExecution(UUID)).thenReturn(TransactionStatus.PROCESSING);
            when(stateWriter.getBankRequest(UUID)).thenReturn(bankRequest());
            stubBankOutcome(BankOutcome.failed(TransactionErrorCode.EXCHANGE_CONTRACT_FAILED));
            ExchangeExecuteResponse resp = response(TransactionStatus.FAILED);
            when(stateWriter.markFailed(UUID)).thenReturn(resp);

            ExchangeExecuteResponse out =
                    exchangeCommandService.executeUserExchange(PARTY_ID, UUID, executeRequest());

            assertThat(out).isSameAs(resp);
            verify(idempotencyStore).failExecution(UUID);
        }

        @Test
        @DisplayName("bank UNKNOWN -> markUnknown, Redis 동기화 안 함")
        void 불확실() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(stateWriter.claimForExecution(UUID)).thenReturn(TransactionStatus.PROCESSING);
            when(stateWriter.getBankRequest(UUID)).thenReturn(bankRequest());
            stubBankOutcome(BankOutcome.unknown());
            ExchangeExecuteResponse resp = response(TransactionStatus.UNKNOWN);
            when(stateWriter.markUnknown(UUID)).thenReturn(resp);

            ExchangeExecuteResponse out =
                    exchangeCommandService.executeUserExchange(PARTY_ID, UUID, executeRequest());

            assertThat(out).isSameAs(resp);
            verify(idempotencyStore, never()).completeExecution(any(), any());
            verify(idempotencyStore, never()).failExecution(any());
        }

        @Test
        @DisplayName("claim이 종단(SUCCESS) 반환 -> getResponse + Redis 동기화, bank 미호출")
        void 이미_종단() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(stateWriter.claimForExecution(UUID)).thenReturn(TransactionStatus.SUCCESS);
            ExchangeExecuteResponse resp = response(TransactionStatus.SUCCESS);
            when(stateWriter.getResponse(UUID)).thenReturn(resp);

            ExchangeExecuteResponse out =
                    exchangeCommandService.executeUserExchange(PARTY_ID, UUID, executeRequest());

            assertThat(out).isSameAs(resp);
            verify(bankCallExecutor, never()).callBankWithRetry(any(), any(), any());
            verify(idempotencyStore).completeExecution(UUID, resp);
        }

        @Test
        @DisplayName("merchant 실행: merchant PIN 검증 후 동일 흐름")
        void merchant_실행() {
            stubMerchantPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(stateWriter.claimForExecution(UUID)).thenReturn(TransactionStatus.PROCESSING);
            when(stateWriter.getBankRequest(UUID)).thenReturn(bankRequest());
            stubBankOutcome(BankOutcome.success(bankResponse()));
            ExchangeExecuteResponse resp = response(TransactionStatus.SUCCESS);
            when(stateWriter.markSuccess(UUID, null, BANK_TX_ID_STR)).thenReturn(resp);

            ExchangeExecuteResponse out =
                    exchangeCommandService.executeMerchantExchange(
                            PARTY_ID, UUID, executeRequest());

            assertThat(out).isSameAs(resp);
        }

        @Test
        @DisplayName("타인 거래 실행 -> validateOwner 거절(NOT_OWNER), claim 미진입")
        void 소유자_불일치() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            doThrow(new BusinessException(UserErrorCode.NOT_OWNER))
                    .when(stateWriter)
                    .validateOwner(UUID, PARTY_ID);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, UUID, executeRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(UserErrorCode.NOT_OWNER);

            verify(stateWriter, never()).claimForExecution(any());
        }
    }

    // ── PIN ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PIN 검증")
    class Pin {

        @Test
        @DisplayName("user PIN 불일치 -> INVALID_PAYMENT_PIN, 게이트 미진입")
        void user_불일치() {
            User user = mock(User.class);
            when(userRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, UUID, executeRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.INVALID_PAYMENT_PIN);

            verify(idempotencyStore, never()).beginExecution(any(), any());
        }

        @Test
        @DisplayName("user 없음 -> USER_NOT_FOUND")
        void user_없음() {
            when(userRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, UUID, executeRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }
}
