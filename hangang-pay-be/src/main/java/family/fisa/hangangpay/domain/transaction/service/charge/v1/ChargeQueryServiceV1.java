package family.fisa.hangangpay.domain.transaction.service.charge.v1;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeInitResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.UserChargeHistoryDetail;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeQueryService;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.service.WalletQueryService;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
public class ChargeQueryServiceV1 implements ChargeQueryService {

    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("700000");
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.1");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final WalletQueryService walletQueryService;
    private final PaginationService paginationService;

    /** 잔액, 월 한도, 계좌 목록을 조합해 충전 초기화 응답 반환 */
    @Override
    public ChargeInitResponse getChargeInit(Long partyId) {
        List<Account> accounts = accountRepository.findAllByParty_Id(partyId);

        // 잔액 - 현재 지갑 잔액을 기준으로 해야함
        BigDecimal walletBalance = walletQueryService.getBalance(partyId).balance();

        // 충전 가능 금액 계산
        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = YearMonth.now().plusMonths(1).atDay(1).atStartOfDay();
        BigDecimal chargedThisMonth =
                transactionRepository.sumMonthlyAmount(
                        partyId,
                        TransactionType.CHARGE,
                        TransactionStatus.SUCCESS,
                        startOfMonth,
                        startOfNextMonth);
        // 월 한도에서 이번 달 충전액을 뺀 나머지가 이번 달 충전 가능 금액이 됨
        BigDecimal remainingLimit = MONTHLY_LIMIT.subtract(chargedThisMonth).max(BigDecimal.ZERO);

        return ChargeInitResponse.of(
                partyId, walletBalance, MONTHLY_LIMIT, remainingLimit, DISCOUNT_RATE, accounts);
    }

    /** 사용자 충전 정보 가져오기 */
    @Override
    public CursorPageResponse<ChargeHistoryItem> getChargeHistories(
            Long partyId, CursorPageRequest request, int size) {
        log.info("충전 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        // 1. CHARGE Type의 Transaction 가져오기
        Window<Transaction> transactions =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(TransactionType.CHARGE),
                        position,
                        Limit.of(size));

        // 2. ChargeHistoryItem 으로 변경
        Window<ChargeHistoryItem> window = transactions.map(ChargeHistoryItem::from);

        log.info("충전 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(window);
    }

    /** 사용자 충전 상세 내역 조회 */
    @Override
    public UserChargeHistoryDetail getUserChargeHistoryDetail(Long partyId, Long transactionId) {
        // 1. Transaction 조회
        Transaction transaction =
                transactionRepository
                        .findDetailByIdAndTypes(transactionId, List.of(TransactionType.CHARGE))
                        .orElseThrow(
                                () -> new BusinessException(TransactionErrorCode.CHARGE_NOT_FOUND));

        // 2. 소유주 검증
        verifyOwner(partyId, transaction);

        return UserChargeHistoryDetail.from(transaction);
    }

    /** 조회자가 트랜잭션 발생자인지 검증 */
    private void verifyOwner(Long partyId, Transaction transaction) {
        if (!transaction.getFromParty().getId().equals(partyId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }
    }
}
