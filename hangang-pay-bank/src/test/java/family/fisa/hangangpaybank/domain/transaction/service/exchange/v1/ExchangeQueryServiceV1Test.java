package family.fisa.hangangpaybank.domain.transaction.service.exchange.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.account.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.account.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeStatusResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeQueryServiceV1Test {

    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Long BANK_TX_ID = 999L;

    @Mock AccountLedgerRepository accountLedgerRepository;

    @InjectMocks ExchangeQueryServiceV1 exchangeQueryService;

    private AccountLedger accountLedger(LedgerStatus status) {
        return AccountLedger.builder().id(BANK_TX_ID).idempotentKey(UUID).status(status).build();
    }

    @Test
    @DisplayName("account_ledger SUCCESS → status=SUCCESS 응답")
    void status_success() {
        when(accountLedgerRepository.findByIdempotentKey(UUID))
                .thenReturn(Optional.of(accountLedger(LedgerStatus.SUCCESS)));

        ExchangeStatusResponse response = exchangeQueryService.getStatus(UUID);

        assertThat(response.transactionUuid()).isEqualTo(UUID);
        assertThat(response.bankTransactionId()).isEqualTo(BANK_TX_ID);
        assertThat(response.status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("account_ledger PENDING → status=PROCESSING 응답")
    void status_processing() {
        when(accountLedgerRepository.findByIdempotentKey(UUID))
                .thenReturn(Optional.of(accountLedger(LedgerStatus.PENDING)));

        ExchangeStatusResponse response = exchangeQueryService.getStatus(UUID);

        assertThat(response.status()).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("account_ledger FAILED → status=FAILED 응답")
    void status_failed() {
        when(accountLedgerRepository.findByIdempotentKey(UUID))
                .thenReturn(Optional.of(accountLedger(LedgerStatus.FAILED)));

        ExchangeStatusResponse response = exchangeQueryService.getStatus(UUID);

        assertThat(response.status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("account_ledger 없으면 TRANSACTION_NOT_FOUND")
    void status_accountLedgerMissing() {
        when(accountLedgerRepository.findByIdempotentKey(UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exchangeQueryService.getStatus(UUID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.TRANSACTION_NOT_FOUND);
    }
}
