package family.fisa.hangangpay.domain.transaction.service.payment.v1;

import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantPaymentDetailResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.MerchantPaymentDetail;
import family.fisa.hangangpay.domain.transaction.dto.user.response.MerchantPaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.UserPaymentHistoryDetail;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentQueryService;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
public class PaymentQueryServiceV1 implements PaymentQueryService {

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final PaginationService paginationService;

    /** 사용자 결제 정보 가져오기 */
    @Override
    public CursorPageResponse<PaymentHistoryItem> getUserPaymentHistory(
            Long partyId, CursorPageRequest request, int size) {
        log.info("결제 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        // 1. PAYMENT Type의 Transaction 가져오기
        Window<Transaction> window =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(TransactionType.PAYMENT, TransactionType.CANCEL),
                        position,
                        Limit.of(size));

        // 2. 상대방 Party Id 가져오기
        List<Long> toPartyIds =
                window.getContent().stream().map(t -> t.getToParty().getId()).toList();

        // 3. 가져온 상대방 ID 기반으로 가맹점 조회
        Map<Long, String> merchantNameMap =
                merchantRepository.findByParty_IdIn(toPartyIds).stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.getParty().getId(), Merchant::getMerchantName));

        // 4. PaymentHistoryItem 변환
        Window<PaymentHistoryItem> responseWindow =
                window.map(
                        t -> {
                            Long payeePartyId = t.getToParty().getId();
                            String merchantName = merchantNameMap.get(payeePartyId);

                            if (merchantName == null) {
                                log.warn(
                                        "가맹점 정보 없음. transactionId={}, payeePartyId={}",
                                        t.getId(),
                                        payeePartyId);
                                merchantName = "알 수 없는 가맹점";
                            }

                            return PaymentHistoryItem.from(t, merchantName);
                        });

        log.info("결제 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(responseWindow);
    }

    /** 가맹점 결제 내역 조회 */
    @Override
    public CursorPageResponse<MerchantPaymentHistoryItem> getMerchantPaymentHistory(
            Long partyId, CursorPageRequest request, int size) {
        log.info("가맹점 결제 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        Window<Transaction> transactions =
                transactionRepository.findPaymentTransactionsByMerchantPartyId(
                        partyId, TransactionStatus.SUCCESS, position, Limit.of(size));

        List<Long> payerPartyIds =
                transactions.getContent().stream()
                        .map(this::resolvePayerPartyId)
                        .distinct()
                        .toList();

        Map<Long, String> payerNameMap =
                userRepository.findByParty_IdIn(payerPartyIds).stream()
                        .collect(
                                Collectors.toMap(
                                        user -> user.getParty().getId(), User::getUsername));

        Window<MerchantPaymentHistoryItem> window =
                transactions.map(
                        transaction -> {
                            Long payerPartyId = resolvePayerPartyId(transaction);
                            String payerName =
                                    payerNameMap.getOrDefault(payerPartyId, "알 수 없는 사용자");
                            return MerchantPaymentHistoryItem.from(transaction, payerName);
                        });

        log.info("가맹점 결제 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(window);
    }

    /** 사용자 결제 상세 내역 조회 */
    @Override
    public UserPaymentHistoryDetail getUserPaymentHistoryDetail(Long partyId, Long transactionId) {
        // 1. Transaction 조회
        Transaction transaction =
                transactionRepository
                        .findDetailByIdAndTypes(
                                transactionId,
                                List.of(TransactionType.PAYMENT, TransactionType.CANCEL))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.PAYMENT_NOT_FOUND));

        // 2. 소유주 검증
        verifyOwner(partyId, transaction);

        return UserPaymentHistoryDetail.from(transaction);
    }

    /** 가맹점 결제 상세 내역 조회 */
    @Override
    public MerchantPaymentDetailResponse<MerchantPaymentDetail> getMerchantPaymentDetail(
            Long partyId, Long transactionId) {
        Transaction transaction =
                transactionRepository
                        .findDetailByIdAndTypes(
                                transactionId,
                                List.of(TransactionType.PAYMENT, TransactionType.CANCEL))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.PAYMENT_NOT_FOUND));

        verifyMerchantOwner(partyId, transaction);

        Long payerPartyId = resolvePayerPartyId(transaction);
        String payerName =
                userRepository
                        .findByParty_Id(payerPartyId)
                        .map(User::getUsername)
                        .orElse("알 수 없는 사용자");

        return MerchantPaymentDetailResponse.of(
                transaction.getTransactionType(),
                MerchantPaymentDetail.from(
                        transaction, payerName, isMerchantPaymentCancelAvailable(transaction)));
    }

    /** 조회자가 트랜잭션 발생자인지 검증 */
    private void verifyOwner(Long partyId, Transaction transaction) {
        if (!transaction.getFromParty().getId().equals(partyId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }
    }

    private void verifyMerchantOwner(Long partyId, Transaction transaction) {
        Long merchantPartyId =
                transaction.getTransactionType() == TransactionType.CANCEL
                        ? transaction.getFromParty().getId()
                        : transaction.getToParty().getId();

        if (!merchantPartyId.equals(partyId)) {
            throw new BusinessException(MerchantErrorCode.NOT_OWNER);
        }
    }

    private Long resolvePayerPartyId(Transaction transaction) {
        if (transaction.getTransactionType() == TransactionType.CANCEL) {
            return transaction.getToParty().getId();
        }
        return transaction.getFromParty().getId();
    }

    /** 가맹점이 해당 결제에 대해 취소 가능 여부 확인 */
    private boolean isMerchantPaymentCancelAvailable(Transaction transaction) {
        return transaction.getTransactionType() == TransactionType.PAYMENT
                && transaction.getStatus() == TransactionStatus.SUCCESS
                && !transactionRepository.existsSuccessCancelByOriginalTransactionUuid(
                        transaction.getTransactionUuid());
    }
}
