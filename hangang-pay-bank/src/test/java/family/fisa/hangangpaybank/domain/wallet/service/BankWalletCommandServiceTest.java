package family.fisa.hangangpaybank.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.wallet.dto.request.CreateBankWalletRequest;
import family.fisa.hangangpaybank.domain.wallet.dto.response.BankWalletResponse;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.crypto.WalletKeyCipher;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankWalletCommandServiceTest {

    @Mock private BankWalletRepository bankWalletRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private WalletKeyCipher walletKeyCipher;
    @Mock private ContractCallService contractCallService;

    @InjectMocks private BankWalletCommandService bankWalletCommandService;

    @Test
    @DisplayName("지갑 생성 시 DB balance 없이 지갑 주소와 암호화 키를 저장하고 잔액 0을 반환한다")
    void createStoresWalletWithoutDbBalance() {
        Institution institution =
                Institution.builder()
                        .id(1L)
                        .institutionCode("WOORI")
                        .institutionName("Woori Bank")
                        .build();
        CreateBankWalletRequest request = new CreateBankWalletRequest(1L, 10L, false);

        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));
        given(walletKeyCipher.encryptPrivateKey(any())).willReturn("encrypted-private-key");
        given(bankWalletRepository.save(any(BankWallet.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BankWalletResponse response = bankWalletCommandService.create(request);

        assertThat(response.institutionId()).isEqualTo(1L);
        assertThat(response.walletAddress()).startsWith("0x");
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(contractCallService, never()).setMerchant(any());
    }
}
