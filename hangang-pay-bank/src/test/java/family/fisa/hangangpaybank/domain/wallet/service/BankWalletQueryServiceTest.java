package family.fisa.hangangpaybank.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpaybank.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpaybank.domain.wallet.dto.response.BankWalletResponse;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankWalletQueryServiceTest {

    private static final String WALLET_ADDRESS = "0x0000000000000000000000000000000000000001";

    @Mock private BankWalletRepository bankWalletRepository;

    @InjectMocks private BankWalletQueryService bankWalletQueryService;

    @Test
    @DisplayName("지갑 단건 조회 시 DB 잔액을 반환한다")
    void getByWalletAddressReturnsDbBalance() {
        BankWallet bankWallet = bankWallet();
        given(bankWalletRepository.findByWalletAddress(WALLET_ADDRESS))
                .willReturn(Optional.of(bankWallet));

        BankWalletResponse response = bankWalletQueryService.getByWalletAddress(WALLET_ADDRESS);

        assertThat(response.walletAddress()).isEqualTo(WALLET_ADDRESS);
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("12345"));
    }

    @Test
    @DisplayName("지갑 주소가 없으면 BANK_WALLET_NOT_FOUND 예외를 던진다")
    void getByWalletAddressThrowsWhenWalletMissing() {
        given(bankWalletRepository.findByWalletAddress(WALLET_ADDRESS))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> bankWalletQueryService.getByWalletAddress(WALLET_ADDRESS))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(WalletErrorCode.BANK_WALLET_NOT_FOUND);
    }

    private static BankWallet bankWallet() {
        Institution institution =
                Institution.builder()
                        .id(1L)
                        .institutionCode("WOORI")
                        .institutionName("Woori Bank")
                        .build();

        BankWallet wallet =
                BankWallet.builder()
                        .id(10L)
                        .institution(institution)
                        .walletAddress(WALLET_ADDRESS)
                        .encryptedPrivateKey("encrypted")
                        .build();
        wallet.updateBalance(new BigDecimal("12345"));
        return wallet;
    }
}
