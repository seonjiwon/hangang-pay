package family.fisa.hangangpay.domain.transaction.service.payment.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantPaymentDetailResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.MerchantPaymentDetail;
import family.fisa.hangangpay.domain.transaction.dto.user.response.MerchantPaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
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

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceV1Test {

    @Mock MerchantRepository merchantRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock UserRepository userRepository;
    @Mock PaginationService paginationService;

    @InjectMocks PaymentQueryServiceV1 paymentQueryService;

    private static final Long PARTY_ID = 10L;
    private static final Long OTHER_PARTY_ID = 99L;
    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long TRANSACTION_ID = 1L;
    private static final int PAGE_SIZE = 10;

    private CursorPageRequest emptyRequest() {
        return new CursorPageRequest(null, null);
    }

    private Party party(Long id) {
        return Party.builder().id(id).partyType(PartyType.USER).build();
    }

    private Party merchantParty(Long id) {
        return Party.builder().id(id).partyType(PartyType.MERCHANT).build();
    }

    private Merchant merchant(Long partyId, String name) {
        return Merchant.builder().party(merchantParty(partyId)).merchantName(name).build();
    }

    private User user(Long partyId, String name) {
        return User.builder().party(party(partyId)).username(name).build();
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

    @Nested
    @DisplayName("결제 내역 조회 (getUserPaymentHistory)")
    class GetUserPaymentHistory {

        @Test
        @DisplayName("정상: PAYMENT/CANCEL 타입을 status=SUCCESS로 조회하고 가맹점명을 join")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void success() {
            Transaction tx =
                    mockTx(
                            TRANSACTION_ID,
                            party(PARTY_ID),
                            merchantParty(MERCHANT_PARTY_ID),
                            TransactionType.PAYMENT);
            Window<Transaction> window = Window.from(List.of(tx), ScrollPosition::offset);

            when(paginationService.resolveScrollPosition(any()))
                    .thenReturn(ScrollPosition.offset());
            when(transactionRepository.findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(List.of(TransactionType.PAYMENT, TransactionType.CANCEL)),
                            any(ScrollPosition.class),
                            any(Limit.class)))
                    .thenReturn(window);
            when(merchantRepository.findByParty_IdIn(List.of(MERCHANT_PARTY_ID)))
                    .thenReturn(List.of(merchant(MERCHANT_PARTY_ID, "카페 드롭탑")));

            CursorPageResponse sentinel = mock(CursorPageResponse.class);
            when(paginationService.toCursorPage(any())).thenReturn(sentinel);

            CursorPageResponse result =
                    paymentQueryService.getUserPaymentHistory(PARTY_ID, emptyRequest(), PAGE_SIZE);

            assertThat(result).isSameAs(sentinel);
            verify(merchantRepository).findByParty_IdIn(List.of(MERCHANT_PARTY_ID));
        }

        @Test
        @DisplayName("가맹점 매핑 없음 -> '알 수 없는 가맹점'으로 fallback")
        void missingMerchant_fallback() {
            Transaction tx =
                    mockTx(
                            TRANSACTION_ID,
                            party(PARTY_ID),
                            merchantParty(MERCHANT_PARTY_ID),
                            TransactionType.PAYMENT);
            Window<Transaction> window = Window.from(List.of(tx), i -> ScrollPosition.offset(i));

            when(paginationService.resolveScrollPosition(any()))
                    .thenReturn(ScrollPosition.offset());
            when(transactionRepository.findTransactionByPartyId(
                            eq(PARTY_ID), eq(TransactionStatus.SUCCESS), any(), any(), any()))
                    .thenReturn(window);
            when(merchantRepository.findByParty_IdIn(List.of(MERCHANT_PARTY_ID)))
                    .thenReturn(List.of());
            when(paginationService.toCursorPage(any())).thenReturn(mock(CursorPageResponse.class));

            paymentQueryService.getUserPaymentHistory(PARTY_ID, emptyRequest(), PAGE_SIZE);

            verify(merchantRepository).findByParty_IdIn(List.of(MERCHANT_PARTY_ID));
        }
    }

    @Nested
    @DisplayName("가맹점 결제 내역 조회 (getMerchantPaymentHistory)")
    class GetMerchantPaymentHistory {

        @Test
        @DisplayName("정상: PAYMENT는 fromParty, CANCEL은 toParty를 결제자로 매핑")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void success_mapsPayerByTransactionType() {
            Transaction payment =
                    mockTx(
                            1L,
                            party(PARTY_ID),
                            merchantParty(MERCHANT_PARTY_ID),
                            TransactionType.PAYMENT);
            Transaction cancel =
                    mockTx(
                            2L,
                            merchantParty(MERCHANT_PARTY_ID),
                            party(OTHER_PARTY_ID),
                            TransactionType.CANCEL);
            Window<Transaction> window =
                    Window.from(List.of(payment, cancel), i -> ScrollPosition.offset(i));

            when(paginationService.resolveScrollPosition(any()))
                    .thenReturn(ScrollPosition.offset());
            when(transactionRepository.findPaymentTransactionsByMerchantPartyId(
                            eq(MERCHANT_PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            any(ScrollPosition.class),
                            any(Limit.class)))
                    .thenReturn(window);
            when(userRepository.findByParty_IdIn(List.of(PARTY_ID, OTHER_PARTY_ID)))
                    .thenReturn(List.of(user(PARTY_ID, "김한강"), user(OTHER_PARTY_ID, "이수")));

            CursorPageResponse sentinel = mock(CursorPageResponse.class);
            when(paginationService.toCursorPage(any()))
                    .thenAnswer(
                            invocation -> {
                                Window<MerchantPaymentHistoryItem> responseWindow =
                                        invocation.getArgument(0);
                                List<MerchantPaymentHistoryItem> content =
                                        responseWindow.getContent();

                                assertThat(content).hasSize(2);
                                assertThat(content.get(0).transactionId()).isEqualTo(1L);
                                assertThat(content.get(0).approvalNumber())
                                        .isEqualTo("APV-2026-00000001");
                                assertThat(content.get(0).payerName()).isEqualTo("김*강");
                                assertThat(content.get(0).transactionType())
                                        .isEqualTo(TransactionType.PAYMENT);
                                assertThat(content.get(1).transactionId()).isEqualTo(2L);
                                assertThat(content.get(1).approvalNumber())
                                        .isEqualTo("APV-2026-00000002");
                                assertThat(content.get(1).payerName()).isEqualTo("이*");
                                assertThat(content.get(1).transactionType())
                                        .isEqualTo(TransactionType.CANCEL);

                                return sentinel;
                            });

            CursorPageResponse result =
                    paymentQueryService.getMerchantPaymentHistory(
                            MERCHANT_PARTY_ID, emptyRequest(), PAGE_SIZE);

            assertThat(result).isSameAs(sentinel);
            verify(userRepository).findByParty_IdIn(List.of(PARTY_ID, OTHER_PARTY_ID));
        }
    }

    @Nested
    @DisplayName("가맹점 결제 상세 조회 (getMerchantPaymentDetail)")
    class GetMerchantPaymentDetail {

        @Test
        @DisplayName("정상: PAYMENT는 fromParty를 결제자로 매핑하고 상세 정보를 반환")
        void success_payment() {
            Transaction tx =
                    mockTx(
                            TRANSACTION_ID,
                            party(PARTY_ID),
                            merchantParty(MERCHANT_PARTY_ID),
                            TransactionType.PAYMENT);
            when(transactionRepository.findDetailByIdAndTypes(
                            eq(TRANSACTION_ID),
                            eq(List.of(TransactionType.PAYMENT, TransactionType.CANCEL))))
                    .thenReturn(Optional.of(tx));
            when(userRepository.findByParty_Id(PARTY_ID))
                    .thenReturn(Optional.of(user(PARTY_ID, "김한강")));

            MerchantPaymentDetailResponse<MerchantPaymentDetail> result =
                    paymentQueryService.getMerchantPaymentDetail(MERCHANT_PARTY_ID, TRANSACTION_ID);

            assertThat(result.transactionType()).isEqualTo(TransactionType.PAYMENT);
            assertThat(result.detail().transactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(result.detail().transactionType()).isEqualTo(TransactionType.PAYMENT);
            assertThat(result.detail().payerName()).isEqualTo("김*강");
            assertThat(result.detail().approvalNumber()).isEqualTo("APV-2026-00000001");
            assertThat(result.detail().paymentStatus()).isEqualTo("SUCCESS");
            assertThat(result.detail().cancelAvailable()).isTrue();
        }

        @Test
        @DisplayName("정상: 이미 취소된 PAYMENT는 취소 불가로 반환")
        void success_paymentAlreadyCanceled() {
            Transaction tx =
                    mockTx(
                            TRANSACTION_ID,
                            party(PARTY_ID),
                            merchantParty(MERCHANT_PARTY_ID),
                            TransactionType.PAYMENT);
            when(transactionRepository.findDetailByIdAndTypes(
                            eq(TRANSACTION_ID),
                            eq(List.of(TransactionType.PAYMENT, TransactionType.CANCEL))))
                    .thenReturn(Optional.of(tx));
            when(transactionRepository.existsSuccessCancelByOriginalTransactionUuid(
                            "transaction-uuid-1"))
                    .thenReturn(true);
            when(userRepository.findByParty_Id(PARTY_ID))
                    .thenReturn(Optional.of(user(PARTY_ID, "김한강")));

            MerchantPaymentDetailResponse<MerchantPaymentDetail> result =
                    paymentQueryService.getMerchantPaymentDetail(MERCHANT_PARTY_ID, TRANSACTION_ID);

            assertThat(result.transactionType()).isEqualTo(TransactionType.PAYMENT);
            assertThat(result.detail().transactionType()).isEqualTo(TransactionType.PAYMENT);
            assertThat(result.detail().cancelAvailable()).isFalse();
        }

        @Test
        @DisplayName("정상: CANCEL은 toParty를 결제자로 매핑하고 취소 불가로 반환")
        void success_cancel() {
            Transaction tx =
                    mockTx(
                            TRANSACTION_ID,
                            merchantParty(MERCHANT_PARTY_ID),
                            party(PARTY_ID),
                            TransactionType.CANCEL);
            when(transactionRepository.findDetailByIdAndTypes(
                            eq(TRANSACTION_ID),
                            eq(List.of(TransactionType.PAYMENT, TransactionType.CANCEL))))
                    .thenReturn(Optional.of(tx));
            when(userRepository.findByParty_Id(PARTY_ID))
                    .thenReturn(Optional.of(user(PARTY_ID, "이수")));

            MerchantPaymentDetailResponse<MerchantPaymentDetail> result =
                    paymentQueryService.getMerchantPaymentDetail(MERCHANT_PARTY_ID, TRANSACTION_ID);

            assertThat(result.transactionType()).isEqualTo(TransactionType.CANCEL);
            assertThat(result.detail().transactionType()).isEqualTo(TransactionType.CANCEL);
            assertThat(result.detail().payerName()).isEqualTo("이*");
            assertThat(result.detail().cancelAvailable()).isFalse();
        }

        @Test
        @DisplayName("Transaction 없음 -> PAYMENT_NOT_FOUND")
        void throws_whenNotFound() {
            when(transactionRepository.findDetailByIdAndTypes(
                            eq(TRANSACTION_ID),
                            eq(List.of(TransactionType.PAYMENT, TransactionType.CANCEL))))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    paymentQueryService.getMerchantPaymentDetail(
                                            MERCHANT_PARTY_ID, TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("소유 가맹점 불일치 -> NOT_OWNER")
        void throws_whenNotOwner() {
            Transaction tx =
                    mockTx(
                            TRANSACTION_ID,
                            party(PARTY_ID),
                            merchantParty(OTHER_PARTY_ID),
                            TransactionType.PAYMENT);
            when(transactionRepository.findDetailByIdAndTypes(eq(TRANSACTION_ID), any()))
                    .thenReturn(Optional.of(tx));

            assertThatThrownBy(
                            () ->
                                    paymentQueryService.getMerchantPaymentDetail(
                                            MERCHANT_PARTY_ID, TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", MerchantErrorCode.NOT_OWNER);
        }
    }

    @Nested
    @DisplayName("결제 상세 조회 (getUserPaymentHistoryDetail)")
    class GetUserPaymentHistoryDetail {

        @Test
        @DisplayName("Transaction 없음 -> PAYMENT_NOT_FOUND")
        void throws_whenNotFound() {
            when(transactionRepository.findDetailByIdAndTypes(
                            eq(TRANSACTION_ID),
                            eq(List.of(TransactionType.PAYMENT, TransactionType.CANCEL))))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    paymentQueryService.getUserPaymentHistoryDetail(
                                            PARTY_ID, TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("소유자 불일치 -> NOT_OWNER")
        void throws_whenNotOwner() {
            Transaction tx =
                    mockTx(
                            TRANSACTION_ID,
                            party(OTHER_PARTY_ID),
                            merchantParty(MERCHANT_PARTY_ID),
                            TransactionType.PAYMENT);
            when(transactionRepository.findDetailByIdAndTypes(eq(TRANSACTION_ID), any()))
                    .thenReturn(Optional.of(tx));

            assertThatThrownBy(
                            () ->
                                    paymentQueryService.getUserPaymentHistoryDetail(
                                            PARTY_ID, TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.NOT_OWNER);
        }
    }
}
