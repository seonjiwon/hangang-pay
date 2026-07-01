package family.fisa.hangangpay.domain.transaction.service.exchange.v1;

import family.fisa.hangangpay.domain.merchant.dto.response.MerchantSettlementHistoryItem;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeInitResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.UserExchangeHistoryDetail;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeQueryService;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.service.WalletQueryService;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeQueryServiceV1 implements ExchangeQueryService {

    private static final BigDecimal USAGE_THRESHOLD_RATE = new BigDecimal("0.60");

    private final TransactionRepository transactionRepository;
    private final WalletQueryService walletQueryService;
    private final PaginationService paginationService;

    @Override
    public ExchangeInitResponse getExchangeInit(Long partyId) {
        // 잔액은 현재 지갑 잔액으로부터 가져옴
        BigDecimal walletBalance = walletQueryService.getBalance(partyId).balance();
        boolean eligible = checkEligibility(partyId);

        log.info(
                "환전 정보 조회. partyId={}, eligible={}, walletBalance={}",
                partyId,
                eligible,
                walletBalance);

        return new ExchangeInitResponse(eligible, walletBalance);
    }

    @Override
    public boolean checkEligibility(Long partyId) {
        Transaction latestCharge =
                transactionRepository.findLatestSuccessCharge(partyId).orElse(null);
        if (latestCharge == null) {
            return false;
        }

        LocalDateTime chargeAt = latestCharge.getCreatedAt();

        BigDecimal chargedBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.CHARGE, chargeAt);
        BigDecimal paidBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.PAYMENT, chargeAt);
        BigDecimal exchangedBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.EXCHANGE, chargeAt);

        BigDecimal balanceBefore = chargedBefore.subtract(paidBefore).subtract(exchangedBefore);
        BigDecimal balanceAfter = balanceBefore.add(latestCharge.getAmount());
        BigDecimal threshold =
                balanceAfter.multiply(USAGE_THRESHOLD_RATE).setScale(0, RoundingMode.UP);

        BigDecimal usedSinceCharge =
                transactionRepository.sumSuccessByTypeSince(
                        partyId, TransactionType.PAYMENT, chargeAt);

        return usedSinceCharge.compareTo(threshold) >= 0;
    }

    /** 사용자 환전 정보 가져오기 */
    @Override
    public CursorPageResponse<ExchangeHistoryItem> getExchangeHistories(
            Long partyId, CursorPageRequest request, int size) {
        log.info("환전 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        // 1. EXCHANGE Type의 Transaction 가져오기
        Window<Transaction> transactions =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(TransactionType.EXCHANGE),
                        position,
                        Limit.of(size));

        // 2. ExchangeHistoryItem 으로 변경
        Window<ExchangeHistoryItem> window = transactions.map(ExchangeHistoryItem::from);

        log.info("환전 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(window);
    }

    /** 사용자 환전 상세 내역 조회 */
    @Override
    public UserExchangeHistoryDetail getUserExchangeHistoryDetail(
            Long partyId, Long transactionId) {
        // 1. Transaction 조회
        Transaction transaction =
                transactionRepository
                        .findDetailByIdAndTypes(transactionId, List.of(TransactionType.EXCHANGE))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.EXCHANGE_NOT_FOUND));

        // 2. 소유주 검증
        verifyOwner(partyId, transaction);

        return UserExchangeHistoryDetail.from(transaction);
    }

    /** 가맹점 정산 내역 조회 */
    @Override
    public CursorPageResponse<MerchantSettlementHistoryItem> getMerchantSettlementHistory(
            Long partyId, CursorPageRequest cursor, int size) {
        log.info("가맹점 정산 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(cursor);

        Window<Transaction> transactions =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(TransactionType.EXCHANGE),
                        position,
                        Limit.of(size));

        Window<MerchantSettlementHistoryItem> window =
                transactions.map(MerchantSettlementHistoryItem::from);

        log.info("가맹점 정산 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(window);
    }

    /** 조회자가 트랜잭션 발생자인지 검증 */
    private void verifyOwner(Long partyId, Transaction transaction) {
        if (!transaction.getFromParty().getId().equals(partyId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }
    }
}
