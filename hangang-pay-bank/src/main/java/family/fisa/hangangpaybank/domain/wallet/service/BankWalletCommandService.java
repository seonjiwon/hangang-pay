package family.fisa.hangangpaybank.domain.wallet.service;

import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.institution.code.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.wallet.dto.request.CreateBankWalletRequest;
import family.fisa.hangangpaybank.domain.wallet.dto.response.BankWalletResponse;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.crypto.WalletKeyCipher;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BankWalletCommandService {

    private final BankWalletRepository bankWalletRepository;
    private final InstitutionRepository institutionRepository;
    private final WalletKeyCipher walletKeyCipher;
    private final ContractCallService contractCallService;

    public BankWalletResponse create(CreateBankWalletRequest request) {
        // 1. 소속 기관 조회
        Institution institution =
                institutionRepository
                        .findById(request.institutionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        // 2. Web3j로 EC keypair 생성
        ECKeyPair keyPair;
        try {
            keyPair = Keys.createEcKeyPair();
        } catch (InvalidAlgorithmParameterException
                | NoSuchAlgorithmException
                | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
        String walletAddress = "0x" + Keys.getAddress(keyPair);
        String privateKeyHex = keyPair.getPrivateKey().toString(16);

        // 3. private key 암호화
        String encryptedPrivateKey = walletKeyCipher.encryptPrivateKey(privateKeyHex);

        // 4. BankWallet 저장
        BankWallet bankWallet =
                BankWallet.builder()
                        .institution(institution)
                        .walletAddress(walletAddress)
                        .encryptedPrivateKey(encryptedPrivateKey)
                        .build();
        BankWallet saved = bankWalletRepository.save(bankWallet);

        // 5. 가맹점이면 온체인 화이트리스트 등록
        if (request.merchant()) {
            try {
                contractCallService.setMerchant(walletAddress);
                log.info("[bank] 가맹점 온체인 등록 완료. walletAddress={}", walletAddress);
            } catch (Exception e) {
                log.warn("[bank] 가맹점 온체인 등록 실패. walletAddress={}", walletAddress, e);
            }
        }

        return BankWalletResponse.from(saved, BigDecimal.ZERO);
    }
}
