package family.fisa.hangangpay.domain.wallet.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankWalletResponse;
import family.fisa.hangangpay.domain.wallet.dto.response.WalletBalanceResponse;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지갑 조회 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletQueryService {

    private static final String UNIT = "KRW";

    /** 지갑 레포지토리 */
    private final WalletRepository walletRepository;

    /** 은행 연동 클라이언트 */
    private final BankClient bankClient;

    /** 파티 식별자 기준 지갑 잔액 조회 */
    public WalletBalanceResponse getBalance(Long partyId) {
        // 파티 식별자로 지갑 조회
        Wallet wallet =
                walletRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));

        // 잔액은 source of truth(bank/블록체인)에서 조회
        BankWalletResponse bankWallet = bankClient.getBankWalletByAddress(wallet.getAddress());

        log.info(
                "지갑 잔액 조회: partyId={}, walletAddress={}, balance={}",
                partyId,
                wallet.getAddress(),
                bankWallet.balance());

        return new WalletBalanceResponse(
                wallet.getAddress(), bankWallet.balance(), UNIT, wallet.getUpdatedAt());
    }
}
