package family.fisa.hangangpaybank.domain.transaction.service.exchange.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.account.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.account.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.account.entity.LedgerType;
import family.fisa.hangangpaybank.domain.account.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeStateWriterV1Test {

    private static final String UUID = "11111111-1111-1111-1111-111111111111";
    private static final Long INSTITUTION_ID = 1L;
    private static final String ACCOUNT_NUMBER = "1002-123-456789";
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private AccountLedgerRepository accountLedgerRepository;

    @InjectMocks private ExchangeStateWriterV1 exchangeStateWriter;

    @Captor private ArgumentCaptor<AccountLedger> ledgerCaptor;

    @Test
    @DisplayName("기존 account_ledger 없으면 DEPOSIT/FAILED 보상 기록을 저장한다")
    void saveFailed_whenNoExistingLedger_savesFailed() {
        given(accountLedgerRepository.findByIdempotentKey(UUID)).willReturn(Optional.empty());
        given(
                        bankAccountRepository.findByInstitution_IdAndAccountNumber(
                                INSTITUTION_ID, ACCOUNT_NUMBER))
                .willReturn(Optional.of(account(new BigDecimal("100000"))));

        exchangeStateWriter.saveFailedAccountLedger(request());

        verify(accountLedgerRepository).save(ledgerCaptor.capture());
        AccountLedger saved = ledgerCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LedgerStatus.FAILED);
        assertThat(saved.getLedgerType()).isEqualTo(LedgerType.DEPOSIT);
        assertThat(saved.getIdempotentKey()).isEqualTo(UUID);
        assertThat(saved.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("이미 account_ledger 기록이 있으면 중복 저장하지 않는다 (unique 충돌 방지)")
    void saveFailed_whenLedgerExists_skips() {
        given(accountLedgerRepository.findByIdempotentKey(UUID))
                .willReturn(
                        Optional.of(AccountLedger.builder().id(1L).idempotentKey(UUID).build()));

        exchangeStateWriter.saveFailedAccountLedger(request());

        verify(accountLedgerRepository, never()).save(any());
        verify(bankAccountRepository, never()).findByInstitution_IdAndAccountNumber(any(), any());
    }

    private ExchangeRequest request() {
        return new ExchangeRequest(
                UUID,
                INSTITUTION_ID,
                "0x0000000000000000000000000000000000000001",
                ACCOUNT_NUMBER,
                AMOUNT);
    }

    private static BankAccount account(BigDecimal balance) {
        return BankAccount.builder()
                .id(1L)
                .institution(Institution.builder().id(INSTITUTION_ID).build())
                .accountNumber(ACCOUNT_NUMBER)
                .ownerName("tester")
                .balance(balance)
                .build();
    }
}
