package family.fisa.hangangpaybank.domain.transaction.service.charge.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.wallet.repository.WalletLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeStatusResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeQueryServiceV1Test {

    private static final String UUID = "11111111-1111-1111-1111-111111111111";

    @Mock WalletLedgerRepository walletLedgerRepository;
    @InjectMocks ChargeQueryServiceV1 chargeQueryService;

    @Test
    @DisplayName("wallet_ledger SUCCESS -> status SUCCESS 매핑")
    void getStatus_success() {
        given(walletLedgerRepository.findFirstByTransactionUuid(UUID))
                .willReturn(Optional.of(ledger(WalletLedgerStatus.SUCCESS)));

        ChargeStatusResponse response = chargeQueryService.getStatus(UUID);

        assertThat(response.transactionUuid()).isEqualTo(UUID);
        assertThat(response.status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("wallet_ledger PENDING -> status PROCESSING 매핑")
    void getStatus_pending() {
        given(walletLedgerRepository.findFirstByTransactionUuid(UUID))
                .willReturn(Optional.of(ledger(WalletLedgerStatus.PENDING)));

        assertThat(chargeQueryService.getStatus(UUID).status()).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("wallet_ledger FAILED -> status FAILED 매핑")
    void getStatus_failed() {
        given(walletLedgerRepository.findFirstByTransactionUuid(UUID))
                .willReturn(Optional.of(ledger(WalletLedgerStatus.FAILED)));

        assertThat(chargeQueryService.getStatus(UUID).status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("wallet_ledger가 없으면 TRANSACTION_NOT_FOUND")
    void getStatus_notFound() {
        given(walletLedgerRepository.findFirstByTransactionUuid(UUID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chargeQueryService.getStatus(UUID))
                .isInstanceOf(BusinessException.class);
    }

    private WalletLedger ledger(WalletLedgerStatus status) {
        return WalletLedger.builder().transactionUuid(UUID).status(status).build();
    }
}
