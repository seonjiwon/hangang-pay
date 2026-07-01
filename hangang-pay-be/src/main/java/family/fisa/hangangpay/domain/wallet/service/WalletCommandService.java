package family.fisa.hangangpay.domain.wallet.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.request.BankWalletCreateRequest;
import family.fisa.hangangpay.client.bank.dto.response.BankWalletResponse;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletCommandService {

    private final WalletRepository walletRepository;
    private final BankClient bankClient;

    /**
     * 회원/가맹점 가입 시 사용할 EOA 지갑을 bank에서 발급받아 BE WALLET 테이블에 저장합니다.
     *
     * <p>Custodial 모델: bank가 keypair 생성 및 보유. BE는 walletAddress만 저장.
     */
    public Wallet createWallet(Party party, Institution institution) {
        // 가맹점일경우 온체인 가맹점 화이트리스트 등록까지 요청
        boolean merchant = party.getPartyType() == PartyType.MERCHANT;

        // 1. bank에 지갑 발급 요청 (Custodial - bank가 keypair 생성)
        BankWalletResponse bankWallet =
                bankClient.createBankWallet(
                        new BankWalletCreateRequest(institution.getId(), merchant));

        // 2. wallet_address 정규화
        String walletAddress = normalizeAddress(bankWallet.walletAddress());

        // 3. BE Wallet 저장
        Wallet wallet =
                Wallet.builder()
                        .party(party)
                        .institution(institution)
                        .address(walletAddress)
                        .build();

        return walletRepository.save(wallet);
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }
}
