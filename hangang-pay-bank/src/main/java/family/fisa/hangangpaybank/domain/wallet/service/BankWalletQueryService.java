package family.fisa.hangangpaybank.domain.wallet.service;

import family.fisa.hangangpaybank.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpaybank.domain.wallet.dto.response.BankWalletResponse;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankWalletQueryService {

    private final BankWalletRepository bankWalletRepository;

    public BankWalletResponse getByWalletAddress(String walletAddress) {
        BankWallet bankWallet =
                bankWalletRepository
                        .findByWalletAddress(walletAddress)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                WalletErrorCode.BANK_WALLET_NOT_FOUND));

        return BankWalletResponse.from(bankWallet, bankWallet.getBalance());
    }
}
