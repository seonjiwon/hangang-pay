package family.fisa.hangangpay.domain.transaction.service.exchange.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeInitResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.dto.response.WalletBalanceResponse;
import family.fisa.hangangpay.domain.wallet.service.WalletQueryService;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExchangeQueryServiceV1Test {

    @Mock TransactionRepository transactionRepository;
    @Mock WalletQueryService walletQueryService;
    @Mock PaginationService paginationService;

    @InjectMocks ExchangeQueryServiceV1 exchangeQueryService;

    private static final Long PARTY_ID = 10L;
    private static final Long OTHER_PARTY_ID = 99L;
    private static final Long TRANSACTION_ID = 1L;
    private static final int PAGE_SIZE = 10;
    private static final LocalDateTime CHARGE_AT = LocalDateTime.of(2026, 5, 1, 0, 0);

    private CursorPageRequest emptyRequest() {
        return new CursorPageRequest(null, null);
    }

    private Party party(Long id) {
        return Party.builder().id(id).partyType(PartyType.USER).build();
    }

    private Transaction mockTx(Long id, Party fromParty, Party toParty, TransactionType type) {
        Transaction tx = mock(Transaction.class);
        lenient().when(tx.getId()).thenReturn(id);
        lenient().when(tx.getTransactionUuid()).thenReturn("transaction-uuid-%d".formatted(id));
        lenient().when(tx.getApprovalNumber()).thenReturn("APV-2026-%08d".formatted(id));
        lenient().when(tx.getFromParty()).thenReturn(fromParty);
        lenient().when(tx.getToParty()).thenReturn(toParty);
        lenient().when(tx.getTransactionType()).thenReturn(type);
        lenient().when(tx.getStatus()).thenReturn(TransactionStatus.SUCCESS);
        lenient().when(tx.getAmount()).thenReturn(new BigDecimal("10000"));
        lenient().when(tx.getCreatedAt()).thenReturn(LocalDateTime.now());
        lenient().when(tx.getUpdatedAt()).thenReturn(LocalDateTime.now());
        return tx;
    }

    private Transaction chargeOf(String amount) {
        Transaction tx =
                Transaction.builder()
                        .transactionType(TransactionType.CHARGE)
                        .status(TransactionStatus.SUCCESS)
                        .amount(new BigDecimal(amount))
                        .build();
        ReflectionTestUtils.setField(tx, "createdAt", CHARGE_AT);
        return tx;
    }

    private void stubWalletBalance(String balance) {
        when(walletQueryService.getBalance(PARTY_ID))
                .thenReturn(new WalletBalanceResponse(null, new BigDecimal(balance), null, null));
    }

    private void stubEligibilityCalculation(
            String chargedBefore,
            String paidBefore,
            String exchangedBefore,
            String chargeAmount,
            String usedSince) {
        when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                .thenReturn(Optional.of(chargeOf(chargeAmount)));
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.CHARGE, CHARGE_AT))
                .thenReturn(new BigDecimal(chargedBefore));
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.PAYMENT, CHARGE_AT))
                .thenReturn(new BigDecimal(paidBefore));
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.EXCHANGE, CHARGE_AT))
                .thenReturn(new BigDecimal(exchangedBefore));
        when(transactionRepository.sumSuccessByTypeSince(
                        PARTY_ID, TransactionType.PAYMENT, CHARGE_AT))
                .thenReturn(new BigDecimal(usedSince));
    }

    @Nested
    @DisplayName("checkEligibility")
    class CheckEligibility {

        @Test
        @DisplayName("충전 이력 없음 -> false")
        void 충전이력_없음() {
            when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                    .thenReturn(Optional.empty());

            assertThat(exchangeQueryService.checkEligibility(PARTY_ID)).isFalse();
        }

        @Test
        @DisplayName("사용액이 임계값 1원 미달 -> false")
        void 임계값_미달() {
            // balanceBefore=10K, charge=60K -> balanceAfter=70K, threshold=42K, used=41999
            stubEligibilityCalculation("10000", "0", "0", "60000", "41999");

            assertThat(exchangeQueryService.checkEligibility(PARTY_ID)).isFalse();
        }

        @Test
        @DisplayName("사용액이 정확히 60% (threshold 도달) -> true")
        void 정확히_임계값_통과() {
            // balanceBefore=10K, charge=60K -> balanceAfter=70K, threshold=42K, used=42000
            stubEligibilityCalculation("10000", "0", "0", "60000", "42000");

            assertThat(exchangeQueryService.checkEligibility(PARTY_ID)).isTrue();
        }

        @Test
        @DisplayName("첫 충전 (잔액 0) + 충전 50K, 사용 30K -> true")
        void 첫_충전_통과() {
            // balanceBefore=0, charge=50K -> balanceAfter=50K, threshold=30K, used=30000
            stubEligibilityCalculation("0", "0", "0", "50000", "30000");

            assertThat(exchangeQueryService.checkEligibility(PARTY_ID)).isTrue();
        }
    }

    @Nested
    @DisplayName("getExchangeInit")
    class GetExchangeInit {

        @Test
        @DisplayName("지갑 잔액 = 충전 - 결제 - 환전")
        void 잔액_계산() {
            stubWalletBalance("50000");
            when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                    .thenReturn(Optional.empty());

            ExchangeInitResponse result = exchangeQueryService.getExchangeInit(PARTY_ID);

            assertThat(result.walletBalance()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("충전 이력 없으면 eligible=false")
        void 충전이력_없으면_eligible_false() {
            stubWalletBalance("0");
            when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                    .thenReturn(Optional.empty());

            ExchangeInitResponse result = exchangeQueryService.getExchangeInit(PARTY_ID);

            assertThat(result.eligible()).isFalse();
        }

        @Test
        @DisplayName("자격 충족하면 eligible=true, 잔액도 함께 반환")
        void 자격충족_eligible_true() {
            stubWalletBalance("88000");
            stubEligibilityCalculation("10000", "0", "0", "60000", "42000");

            ExchangeInitResponse result = exchangeQueryService.getExchangeInit(PARTY_ID);

            assertThat(result.eligible()).isTrue();
            assertThat(result.walletBalance()).isEqualByComparingTo("88000");
        }
    }

    @Nested
    @DisplayName("환전 내역 조회 (getExchangeHistories)")
    class GetExchangeHistories {

        @Test
        @DisplayName("정상: EXCHANGE 타입을 status=SUCCESS로 조회")
        void success() {
            Window<Transaction> empty = Window.from(List.of(), i -> ScrollPosition.offset(i));
            when(paginationService.resolveScrollPosition(any()))
                    .thenReturn(ScrollPosition.offset());
            when(transactionRepository.findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(List.of(TransactionType.EXCHANGE)),
                            any(ScrollPosition.class),
                            any(Limit.class)))
                    .thenReturn(empty);
            when(paginationService.toCursorPage(any())).thenReturn(mock(CursorPageResponse.class));

            exchangeQueryService.getExchangeHistories(PARTY_ID, emptyRequest(), PAGE_SIZE);

            verify(transactionRepository)
                    .findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(List.of(TransactionType.EXCHANGE)),
                            any(ScrollPosition.class),
                            any(Limit.class));
        }
    }

    @Nested
    @DisplayName("환전 상세 조회 (getUserExchangeHistoryDetail)")
    class GetUserExchangeHistoryDetail {

        @Test
        @DisplayName("Transaction 없음 -> EXCHANGE_NOT_FOUND")
        void throws_whenNotFound() {
            when(transactionRepository.findDetailByIdAndTypes(
                            eq(TRANSACTION_ID), eq(List.of(TransactionType.EXCHANGE))))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    exchangeQueryService.getUserExchangeHistoryDetail(
                                            PARTY_ID, TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", TransactionErrorCode.EXCHANGE_NOT_FOUND);
        }

        @Test
        @DisplayName("소유자 불일치 -> NOT_OWNER")
        void throws_whenNotOwner() {
            Transaction tx =
                    mockTx(TRANSACTION_ID, party(OTHER_PARTY_ID), null, TransactionType.EXCHANGE);
            when(transactionRepository.findDetailByIdAndTypes(eq(TRANSACTION_ID), any()))
                    .thenReturn(Optional.of(tx));

            assertThatThrownBy(
                            () ->
                                    exchangeQueryService.getUserExchangeHistoryDetail(
                                            PARTY_ID, TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.NOT_OWNER);
        }
    }

    @Nested
    @DisplayName("가맹점 정산 내역 조회 (getMerchantSettlementHistory)")
    class GetMerchantSettlementHistory {

        @Test
        @DisplayName("정상: 현재 가맹점의 EXCHANGE 타입을 status=SUCCESS로 조회")
        void success() {
            Window<Transaction> empty = Window.from(List.of(), i -> ScrollPosition.offset(i));
            when(paginationService.resolveScrollPosition(any()))
                    .thenReturn(ScrollPosition.offset());
            when(transactionRepository.findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(List.of(TransactionType.EXCHANGE)),
                            any(ScrollPosition.class),
                            any(Limit.class)))
                    .thenReturn(empty);
            when(paginationService.toCursorPage(any())).thenReturn(mock(CursorPageResponse.class));

            exchangeQueryService.getMerchantSettlementHistory(PARTY_ID, emptyRequest(), PAGE_SIZE);

            verify(transactionRepository)
                    .findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(List.of(TransactionType.EXCHANGE)),
                            any(ScrollPosition.class),
                            any(Limit.class));
        }
    }
}
