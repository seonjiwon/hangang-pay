package family.fisa.hangangpay.domain.merchant.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankWalletResponse;
import family.fisa.hangangpay.client.bank.dto.response.MerchantRedeemInitResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantDashboardResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantInfoResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantMyPageResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantQueryService {
    private final MerchantRepository merchantRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final BankClient bankClient;
    private final TransactionRepository transactionRepository;

    public MerchantMyPageResponse getMyPage(Long partyId) {
        Merchant merchant =
                merchantRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        Account account =
                accountRepository
                        .findByParty_IdAndAccountType(partyId, AccountType.SETTLEMENT)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                MerchantErrorCode
                                                        .MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND));

        return MerchantMyPageResponse.from(merchant, account);
    }

    public MerchantInfoResponse getMerchantInfo(Long merchantId) {
        log.info("가맹점 정보 조회 시작. merchantId={}", merchantId);

        Merchant merchant = findMerchantById(merchantId);
        Wallet wallet = findWalletByPartyId(merchant.getParty().getId());

        log.info("가맹점 정보 조회 완료. merchantId={}", merchantId);

        return MerchantInfoResponse.from(merchant, wallet);
    }

    /** 가맹점 정산 신청 화면 진입용 정보 조회 */
    public MerchantRedeemInitResponse getRedeemInit(Long partyId) {
        log.info("가맹점 정산 신청 정보 조회 시작. partyId={}", partyId);

        // 1. 지갑 조회
        Wallet wallet = findWalletByPartyId(partyId);

        // 2. SETTLEMENT 계좌 조회
        Account settlementAccount =
                accountRepository
                        .findByParty_IdAndAccountType(partyId, AccountType.SETTLEMENT)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                MerchantErrorCode
                                                        .MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND));

        // 3. bank애서 보유 토큰 잔액 조회 (환전 가능 금액)
        BankWalletResponse bankWalletResponse =
                bankClient.getBankWalletByAddress(wallet.getAddress());

        log.info(
                "가맹점 정산 신청 정보 조회 완료. partyId={}, availableAmount={}",
                partyId,
                bankWalletResponse.balance());

        return MerchantRedeemInitResponse.from(bankWalletResponse.balance(), settlementAccount);
    }

    /** 가맹점 매출 요약 조회 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MerchantDashboardResponse getDashboard(Long partyId) {
        log.info("가맹점 매출 요약 조회 시작. partyId={}", partyId);

        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = today.plusMonths(1).withDayOfMonth(1).atStartOfDay();

        List<Transaction> todayPayments =
                transactionRepository.findMerchantPaymentsBetween(
                        partyId, TransactionStatus.SUCCESS, startOfToday, startOfTomorrow);

        BigDecimal todaySales =
                todayPayments.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        long todayCount = todayPayments.size();
        BigDecimal pendingSettlement = getWalletBalance(partyId);

        BigDecimal monthlyTotalSales =
                transactionRepository
                        .findMerchantPaymentsBetween(
                                partyId, TransactionStatus.SUCCESS, startOfMonth, startOfNextMonth)
                        .stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info(
                "가맹점 매출 요약 조회 완료. partyId={}, todaySales={}, todayCount={}, pendingSettlement={}, monthlyTotalSales={}",
                partyId,
                todaySales,
                todayCount,
                pendingSettlement,
                monthlyTotalSales);

        return new MerchantDashboardResponse(
                todaySales, todayCount, pendingSettlement, monthlyTotalSales);
    }

    private BigDecimal getWalletBalance(Long partyId) {
        // TODO: refactoring
        // redeem 쪽에서도 같은 로직을 사용하고 있음. 사용자 쪽에서도?
        // 사용자/가맹점 공통 지갑 잔액 조회 유스케이스가 늘어나면
        // WalletQueryService 또는 별도 WalletBalanceQueryService로 분리한다.
        Wallet wallet = findWalletByPartyId(partyId);
        BankWalletResponse bankWalletResponse =
                bankClient.getBankWalletByAddress(wallet.getAddress());
        return bankWalletResponse.balance();
    }

    private Merchant findMerchantById(Long merchantId) {
        return merchantRepository
                .findById(merchantId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    private Wallet findWalletByPartyId(Long partyId) {
        return walletRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Merchant getByPartyId(Long partyId) {
        return merchantRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }
}
