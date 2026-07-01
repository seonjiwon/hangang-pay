package family.fisa.hangangpay.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankWalletResponse;
import family.fisa.hangangpay.domain.wallet.dto.response.WalletBalanceResponse;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletQueryServiceTest {

    @Mock private WalletRepository walletRepository;

    @Mock private BankClient bankClient;

    @InjectMocks private WalletQueryService walletQueryService;

    @Test
    @DisplayName("잔액은 DB 합산이 아니라 bank 조회 결과를 반환한다")
    void getBalance_returnsBankBalance() {
        // given
        Long partyId = 1L;
        String address = "0xabc";
        Wallet wallet = mock(Wallet.class);
        given(wallet.getAddress()).willReturn(address);
        given(walletRepository.findByParty_Id(partyId)).willReturn(Optional.of(wallet));
        given(bankClient.getBankWalletByAddress(address))
                .willReturn(new BankWalletResponse(10L, 1L, address, new BigDecimal("5000")));

        // when
        WalletBalanceResponse response = walletQueryService.getBalance(partyId);

        // then
        assertThat(response.walletAddress()).isEqualTo(address);
        assertThat(response.balance()).isEqualByComparingTo("5000");
        assertThat(response.unit()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("지갑이 없으면 BusinessException, bank는 호출하지 않는다")
    void getBalance_walletNotFound() {
        // given
        Long partyId = 99L;
        given(walletRepository.findByParty_Id(partyId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> walletQueryService.getBalance(partyId))
                .isInstanceOf(BusinessException.class);
        verify(bankClient, never())
                .getBankWalletByAddress(org.mockito.ArgumentMatchers.anyString());
    }
}
