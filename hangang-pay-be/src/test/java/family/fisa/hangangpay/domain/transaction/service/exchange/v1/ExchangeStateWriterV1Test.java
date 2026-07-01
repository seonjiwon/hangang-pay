package family.fisa.hangangpay.domain.transaction.service.exchange.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.client.bank.dto.request.ExchangeRequest;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
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

@ExtendWith(MockitoExtension.class)
class ExchangeStateWriterV1Test {

    @Mock TransactionRepository transactionRepository;
    @Mock WalletRepository walletRepository;
    @Mock AccountRepository accountRepository;

    @InjectMocks ExchangeStateWriterV1 stateWriter;

    private static final Long PARTY_ID = 10L;
    private static final Long INSTITUTION_ID = 1L;
    private static final Long TRANSACTION_ID = 100L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String WALLET_ADDRESS = "0xabc123";
    private static final String ACCOUNT_NUMBER = "110-1234-5678";
    private static final String BANK_NAME = "우리은행";
    private static final String TX_HASH = "0xdef456";
    private static final String BANK_TX_ID_STR = "999";

    private ExchangeIntentCreateRequest intentRequest() {
        return new ExchangeIntentCreateRequest(new BigDecimal("50000"));
    }

    private Party party() {
        return Party.builder().id(PARTY_ID).partyType(PartyType.USER).build();
    }

    private Institution institution() {
        return Institution.builder()
                .id(INSTITUTION_ID)
                .institutionCode("020")
                .institutionName(BANK_NAME)
                .build();
    }

    private Wallet wallet() {
        return Wallet.builder()
                .id(1L)
                .party(party())
                .institution(institution())
                .address(WALLET_ADDRESS)
                .build();
    }

    private Account primaryAccount() {
        return Account.builder()
                .id(1L)
                .party(party())
                .institution(institution())
                .accountType(AccountType.PRIMARY)
                .accountNumber(ACCOUNT_NUMBER)
                .build();
    }

    private Transaction exchange(TransactionStatus status) {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(status)
                .fromParty(party())
                .fromWallet(wallet())
                .toAccount(primaryAccount())
                .amount(new BigDecimal("50000"))
                .reconcileAttemptCount(0)
                .build();
    }

    @Nested
    @DisplayName("createIntent")
    class CreateIntent {

        @Test
        @DisplayName("서버 발급 uuid로 PENDING insert 후 intent 응답(PENDING)")
        void 신규_PENDING() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.PRIMARY))
                    .thenReturn(Optional.of(primaryAccount()));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ExchangeIntentResponse response =
                    stateWriter.createIntent(
                            PARTY_ID, intentRequest(), AccountType.PRIMARY, LocalDateTime.now());

            assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
            assertThat(response.transactionUuid()).isNotBlank();
            assertThat(response.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(response.bankName()).isEqualTo(BANK_NAME);
            verify(transactionRepository).save(any(Transaction.class));
        }

        @Test
        @DisplayName("두 번 호출하면 서로 다른 uuid가 발급된다")
        void 두_번_호출_다른_uuid() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.PRIMARY))
                    .thenReturn(Optional.of(primaryAccount()));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ExchangeIntentResponse first =
                    stateWriter.createIntent(
                            PARTY_ID, intentRequest(), AccountType.PRIMARY, LocalDateTime.now());
            ExchangeIntentResponse second =
                    stateWriter.createIntent(
                            PARTY_ID, intentRequest(), AccountType.PRIMARY, LocalDateTime.now());

            assertThat(first.transactionUuid()).isNotBlank();
            assertThat(first.transactionUuid()).isNotEqualTo(second.transactionUuid());
        }

        @Test
        @DisplayName("wallet 없음 -> WALLET_NOT_FOUND")
        void wallet_없음() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    stateWriter.createIntent(
                                            PARTY_ID,
                                            intentRequest(),
                                            AccountType.PRIMARY,
                                            LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("계좌 없음 -> ACCOUNT_NOT_FOUND")
        void 계좌_없음() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.PRIMARY))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    stateWriter.createIntent(
                                            PARTY_ID,
                                            intentRequest(),
                                            AccountType.PRIMARY,
                                            LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);

            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("claimForExecution (CAS)")
    class ClaimForExecution {

        @Test
        @DisplayName("CAS 1행 선점 성공 -> PROCESSING 반환 (현재 상태 재조회 안 함)")
        void 선점_성공() {
            when(transactionRepository.claimForExecution(UUID)).thenReturn(1);

            TransactionStatus status = stateWriter.claimForExecution(UUID);

            assertThat(status).isEqualTo(TransactionStatus.PROCESSING);
            verify(transactionRepository, never()).findByTransactionUuid(any());
        }

        @Test
        @DisplayName("CAS 0행(이미 SUCCESS) -> 현재 상태 반환")
        void 이미_종단() {
            when(transactionRepository.claimForExecution(UUID)).thenReturn(0);
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(exchange(TransactionStatus.SUCCESS)));

            TransactionStatus status = stateWriter.claimForExecution(UUID);

            assertThat(status).isEqualTo(TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("CAS 0행 + tx 없음 -> EXCHANGE_NOT_FOUND")
        void tx_없음() {
            when(transactionRepository.claimForExecution(UUID)).thenReturn(0);
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stateWriter.claimForExecution(UUID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("validateOwner")
    class ValidateOwner {

        @Test
        @DisplayName("본인 거래 -> 통과")
        void 본인() {
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(exchange(TransactionStatus.PENDING)));

            assertThatCode(() -> stateWriter.validateOwner(UUID, PARTY_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("타인 거래 -> NOT_OWNER")
        void 타인() {
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(exchange(TransactionStatus.PENDING)));

            assertThatThrownBy(() -> stateWriter.validateOwner(UUID, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(UserErrorCode.NOT_OWNER);
        }

        @Test
        @DisplayName("tx 없음 -> EXCHANGE_NOT_FOUND")
        void tx_없음() {
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stateWriter.validateOwner(UUID, PARTY_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getBankRequest / mark* / getResponse / incrementRetry / markExpired")
    class Operations {

        @Test
        @DisplayName("getBankRequest: tx에서 추출")
        void getBankRequest_정상() {
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(exchange(TransactionStatus.PROCESSING)));

            ExchangeRequest result = stateWriter.getBankRequest(UUID);

            assertThat(result.transactionUuid()).isEqualTo(UUID);
            assertThat(result.institutionId()).isEqualTo(INSTITUTION_ID);
            assertThat(result.walletAddress()).isEqualTo(WALLET_ADDRESS);
            assertThat(result.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50000"));
        }

        @Test
        @DisplayName("markSuccess: SUCCESS 전환 + txHash")
        void markSuccess_정상() {
            Transaction tx = exchange(TransactionStatus.PROCESSING);
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.of(tx));

            ExchangeExecuteResponse response =
                    stateWriter.markSuccess(UUID, TX_HASH, BANK_TX_ID_STR);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(tx.getTxHash()).isEqualTo(TX_HASH);
            assertThat(response.txHash()).isEqualTo(TX_HASH);
            assertThat(tx.getApprovalNumber())
                    .isEqualTo(
                            "APV-"
                                    + LocalDateTime.now().getYear()
                                    + "-"
                                    + String.format("%08d", TRANSACTION_ID));
            assertThat(response.approvalNumber()).isEqualTo(tx.getApprovalNumber());
        }

        @Test
        @DisplayName("markFailed: FAILED 전환")
        void markFailed_정상() {
            Transaction tx = exchange(TransactionStatus.PROCESSING);
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.of(tx));

            stateWriter.markFailed(UUID);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        }

        @Test
        @DisplayName("markUnknown: UNKNOWN 전환 + retryCount++")
        void markUnknown_정상() {
            Transaction tx = exchange(TransactionStatus.PROCESSING);
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.of(tx));

            ExchangeExecuteResponse response = stateWriter.markUnknown(UUID);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.UNKNOWN);
            assertThat(tx.getReconcileAttemptCount()).isEqualTo(1);
            assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
        }

        @Test
        @DisplayName("incrementRetry: retryCount++")
        void incrementRetry_정상() {
            Transaction tx = exchange(TransactionStatus.UNKNOWN);
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.of(tx));

            stateWriter.incrementRetry(UUID);

            assertThat(tx.getReconcileAttemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("markExpired: EXPIRED 전환")
        void markExpired_정상() {
            Transaction tx = exchange(TransactionStatus.PENDING);
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.of(tx));

            stateWriter.markExpired(UUID);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.EXPIRED);
        }

        @Test
        @DisplayName("getResponse: 현재 상태 응답 빌드")
        void getResponse_정상() {
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(exchange(TransactionStatus.SUCCESS)));

            ExchangeExecuteResponse response = stateWriter.getResponse(UUID);

            assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("tx 없음 -> EXCHANGE_NOT_FOUND (markSuccess)")
        void tx_없음() {
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stateWriter.markSuccess(UUID, TX_HASH, BANK_TX_ID_STR))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
        }
    }
}
