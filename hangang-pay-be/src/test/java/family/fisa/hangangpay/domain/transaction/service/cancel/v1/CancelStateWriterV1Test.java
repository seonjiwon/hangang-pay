package family.fisa.hangangpay.domain.transaction.service.cancel.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CancelStateWriterV1Test {

    private static final Long USER_PARTY_ID = 10L;
    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long OTHER_PARTY_ID = 30L;
    private static final Long TRANSACTION_ID = 100L;
    private static final Long CANCEL_TRANSACTION_ID = 200L;
    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String CANCEL_UUID = "22222222-2222-2222-2222-222222222222";

    @Mock private TransactionRepository transactionRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private CancelStateWriter cancelStateWriter;

    @BeforeEach
    void setUp() {
        cancelStateWriter =
                new CancelStateWriterV1(transactionRepository, merchantRepository, passwordEncoder);
    }

    // ===== prepareCancel =====

    @Test
    @DisplayName("정상 취소 준비 시 CANCEL 거래가 저장되고 PROCESSING 상태로 전환된다")
    void prepareCancel_savesAndReturnsProcessingCancel() {
        // 1. 원본 PAYMENT 거래 (SUCCESS, toParty=가맹점)
        Transaction original = paymentTransaction(MERCHANT_PARTY_ID, TransactionStatus.SUCCESS);

        // 2. 전 단계 검증 통과 설정
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(original));
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant("pin-hash")));
        given(passwordEncoder.matches("123456", "pin-hash")).willReturn(true);
        given(transactionRepository.existsSuccessCancelFor(TRANSACTION_UUID)).willReturn(false);
        // 3. save는 전달받은 객체를 그대로 반환 (JPA id 채번 생략)
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // 4. 실행
        CancelExecutionPrepared result =
                cancelStateWriter.prepareCancel(MERCHANT_PARTY_ID, TRANSACTION_ID, "123456");

        // 5. 취소 방향 검증 - 원본 PAYMENT의 역방향 (가맹점 -> 소비자)
        assertThat(result.fromWalletAddress()).isEqualTo("0x-merchant");
        assertThat(result.toWalletAddress()).isEqualTo("0x-user");
        assertThat(result.amount()).isEqualByComparingTo("10000");
        assertThat(result.originalTransactionUuid()).isEqualTo(TRANSACTION_UUID);

        // 6. 저장된 CANCEL 거래 내용 검증 - save() 이후 markProcessing()이 호출되어 PROCESSING이어야 함
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction cancelTx = captor.getValue();
        assertThat(cancelTx.getTransactionType()).isEqualTo(TransactionType.CANCEL);
        assertThat(cancelTx.getOriginalTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
    }

    @Test
    @DisplayName("가맹점이 원본 결제 수신자(toParty)가 아니면 취소가 거부된다")
    void prepareCancel_failsWhenMerchantIsNotReceiver() {
        // 1. toParty가 다른 가맹점(OTHER_PARTY_ID)인 거래
        Transaction original = paymentTransaction(OTHER_PARTY_ID, TransactionStatus.SUCCESS);
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(original));

        // 2. 현재 세션 가맹점(MERCHANT_PARTY_ID)이 아닌 거래를 취소하려 하면 거부
        assertThatThrownBy(
                        () ->
                                cancelStateWriter.prepareCancel(
                                        MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);
    }

    @Test
    @DisplayName("PAYMENT + SUCCESS 조합이 아닌 거래는 취소할 수 없다")
    void prepareCancel_failsWhenPaymentNotSuccess() {
        // 1. PENDING 상태 거래 - 아직 Bank 실행 전이라 취소 불가
        Transaction original = paymentTransaction(MERCHANT_PARTY_ID, TransactionStatus.PENDING);
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(original));

        // 2. 취소 불가 상태 오류 발생
        assertThatThrownBy(
                        () ->
                                cancelStateWriter.prepareCancel(
                                        MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_CANCELLABLE);
    }

    @Test
    @DisplayName("PIN이 틀리면 결제 취소가 거부된다")
    void prepareCancel_failsWhenPinMismatch() {
        // 1. 유효한 PAYMENT 거래 설정
        Transaction original = paymentTransaction(MERCHANT_PARTY_ID, TransactionStatus.SUCCESS);
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(original));
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant("pin-hash")));
        // 2. 잘못된 PIN 입력
        given(passwordEncoder.matches("wrong-pin", "pin-hash")).willReturn(false);

        // 3. PIN 불일치 오류 발생
        assertThatThrownBy(
                        () ->
                                cancelStateWriter.prepareCancel(
                                        MERCHANT_PARTY_ID, TRANSACTION_ID, "wrong-pin"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.INVALID_PAYMENT_PIN);
    }

    @Test
    @DisplayName("동일 원본에 SUCCESS CANCEL이 이미 존재하면 재취소가 거부된다")
    void prepareCancel_failsWhenAlreadyCancelled() {
        // 1. 유효한 PAYMENT 거래 설정 - PIN까지 통과
        Transaction original = paymentTransaction(MERCHANT_PARTY_ID, TransactionStatus.SUCCESS);
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(original));
        given(merchantRepository.findByParty_Id(MERCHANT_PARTY_ID))
                .willReturn(Optional.of(merchant("pin-hash")));
        given(passwordEncoder.matches("123456", "pin-hash")).willReturn(true);
        // 2. 이미 SUCCESS CANCEL이 존재
        given(transactionRepository.existsSuccessCancelFor(TRANSACTION_UUID)).willReturn(true);

        // 3. 이미 취소된 결제 오류 발생
        assertThatThrownBy(
                        () ->
                                cancelStateWriter.prepareCancel(
                                        MERCHANT_PARTY_ID, TRANSACTION_ID, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_ALREADY_CANCELLED);
    }

    // ===== completeSuccess =====

    @Test
    @DisplayName("Bank 취소 성공 시 CANCEL 거래가 SUCCESS로 확정되고 승인번호가 발급된다")
    void completeSuccess_confirmsCancelWithTxHashAndApprovalNumber() {
        // 1. PROCESSING 상태의 CANCEL 거래 (id 채번 완료 가정)
        Transaction cancelTx = cancelTransaction();
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 27, 14, 0);

        given(transactionRepository.findByTransactionUuid(CANCEL_UUID))
                .willReturn(Optional.of(cancelTx));

        // 2. completeSuccess 실행
        PaymentCancelResponse response =
                cancelStateWriter.completeSuccess(CANCEL_UUID, "0x-cancel-tx", "888", confirmedAt);

        // 3. 거래 상태 전환 검증
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(cancelTx.getTxHash()).isEqualTo("0x-cancel-tx");
        assertThat(cancelTx.getBankTransactionId()).isEqualTo("888");
        // 4. 승인번호 형식 검증 (APV-YYYY-NNNNNNNN)
        assertThat(cancelTx.getApprovalNumber()).startsWith("APV-2026-");
        assertThat(cancelTx.getApprovalNumber()).hasSize("APV-2026-00000200".length());

        // 5. 응답 필드 검증
        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS); // 성공 확정 상태
        assertThat(response.transactionUuid()).isEqualTo(CANCEL_UUID);
        assertThat(response.amount()).isEqualByComparingTo("10000");
        assertThat(response.confirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    @DisplayName("completeSuccess에서 CANCEL 거래를 찾을 수 없으면 PAYMENT_NOT_FOUND 오류가 발생한다")
    void completeSuccess_failsWhenCancelTransactionNotFound() {
        // 1. 존재하지 않는 UUID
        given(transactionRepository.findByTransactionUuid(CANCEL_UUID))
                .willReturn(Optional.empty());

        // 2. PAYMENT_NOT_FOUND 오류 발생
        assertThatThrownBy(
                        () ->
                                cancelStateWriter.completeSuccess(
                                        CANCEL_UUID, "0x-cancel-tx", "888", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_NOT_FOUND);
    }

    // ===== markUnknown =====

    @Test
    @DisplayName("Bank 네트워크 오류 시 CANCEL이 UNKNOWN으로 저장되고 txHash·confirmedAt이 없다")
    void markUnknown_savesUnknownCancelTransaction() {
        // 1. prepareCancel이 이미 PROCESSING으로 커밋해놓은 CANCEL 거래
        Transaction cancelTx = cancelTransaction();
        given(transactionRepository.findByTransactionUuid(CANCEL_UUID))
                .willReturn(Optional.of(cancelTx));

        // 2. markUnknown 실행
        PaymentCancelResponse response = cancelStateWriter.markUnknown(CANCEL_UUID);

        // 3. UNKNOWN 상태 전환 검증 — FAILED가 아님을 명시적으로 확인
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(cancelTx.getStatus()).isNotEqualTo(TransactionStatus.FAILED);

        // 4. 응답 검증 — 은행 확정 전이므로 confirmedAt 없음
        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(response.transactionUuid()).isEqualTo(CANCEL_UUID);
        assertThat(response.confirmedAt()).isNull();
    }

    // ===== recovery =====

    @Test
    @DisplayName("취소 복구 준비 시 원본 소유권과 복구 가능한 CANCEL을 검증한다")
    void prepareRecovery_validatesAndReturnsCancelTarget() {
        Transaction original = paymentTransaction(MERCHANT_PARTY_ID, TransactionStatus.SUCCESS);
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);

        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(original));
        given(
                        transactionRepository.findRecoverableCancelByOriginalTransactionUuid(
                                TRANSACTION_UUID))
                .willReturn(Optional.of(cancelTx));

        CancelExecutionPrepared prepared =
                cancelStateWriter.prepareRecovery(MERCHANT_PARTY_ID, TRANSACTION_ID);

        assertThat(prepared.cancelTransactionUuid()).isEqualTo(CANCEL_UUID);
        assertThat(prepared.originalTransactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(prepared.fromWalletAddress()).isEqualTo("0x-merchant");
        assertThat(prepared.toWalletAddress()).isEqualTo("0x-user");
    }

    @Test
    @DisplayName("취소 복구 준비 시 가맹점이 원본 결제 수신자가 아니면 거부된다")
    void prepareRecovery_rejectsWrongMerchant() {
        Transaction original = paymentTransaction(OTHER_PARTY_ID, TransactionStatus.SUCCESS);
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(original));

        assertThatThrownBy(
                        () -> cancelStateWriter.prepareRecovery(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.PAYMENT_CANCEL_FORBIDDEN);
    }

    @Test
    @DisplayName("취소 복구 준비 시 복구 가능한 CANCEL이 없으면 거부된다")
    void prepareRecovery_rejectsWhenNoRecoverableCancel() {
        Transaction original = paymentTransaction(MERCHANT_PARTY_ID, TransactionStatus.SUCCESS);
        given(
                        transactionRepository.findDetailByIdAndTypes(
                                TRANSACTION_ID, List.of(TransactionType.PAYMENT)))
                .willReturn(Optional.of(original));
        given(
                        transactionRepository.findRecoverableCancelByOriginalTransactionUuid(
                                TRANSACTION_UUID))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () -> cancelStateWriter.prepareRecovery(MERCHANT_PARTY_ID, TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.CANCEL_NOT_RECOVERABLE);
    }

    @Test
    @DisplayName("Bank SUCCESS 취소 복구 결과를 로컬 SUCCESS로 반영한다")
    void applyRecoveryResult_success() {
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);
        BankTransactionStatusResponse bankStatus =
                bankStatus(TransactionStatus.SUCCESS, 888L, "0x-recovered-cancel");

        given(transactionRepository.findByTransactionUuid(CANCEL_UUID))
                .willReturn(Optional.of(cancelTx));

        PaymentCancelResponse response =
                cancelStateWriter.applyRecoveryResult(CANCEL_UUID, bankStatus);

        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(cancelTx.getTxHash()).isNull();
        assertThat(cancelTx.getBankTransactionId()).isEqualTo("888");
    }

    @Test
    @DisplayName("Bank FAILED 취소 복구 결과를 로컬 FAILED로 반영한다")
    void applyRecoveryResult_failed() {
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);
        BankTransactionStatusResponse bankStatus = bankStatus(TransactionStatus.FAILED, null, null);

        given(transactionRepository.findByTransactionUuid(CANCEL_UUID))
                .willReturn(Optional.of(cancelTx));

        PaymentCancelResponse response =
                cancelStateWriter.applyRecoveryResult(CANCEL_UUID, bankStatus);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    @DisplayName("Bank PROCESSING 취소 복구 결과는 로컬 UNKNOWN을 유지한다")
    void applyRecoveryResult_processingKeepsUnknown() {
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);
        BankTransactionStatusResponse bankStatus =
                bankStatus(TransactionStatus.PROCESSING, null, null);

        given(transactionRepository.findByTransactionUuid(CANCEL_UUID))
                .willReturn(Optional.of(cancelTx));

        PaymentCancelResponse response =
                cancelStateWriter.applyRecoveryResult(CANCEL_UUID, bankStatus);

        assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
        assertThat(cancelTx.getStatus()).isEqualTo(TransactionStatus.UNKNOWN);
    }

    @Test
    @DisplayName("Bank SUCCESS 취소 복구 결과에 bankTransactionId가 없으면 오류가 발생한다")
    void applyRecoveryResult_successRequiresBankProof() {
        Transaction cancelTx = cancelTransaction(TransactionStatus.UNKNOWN);
        BankTransactionStatusResponse bankStatus =
                bankStatus(TransactionStatus.SUCCESS, null, "0x-recovered-cancel");

        given(transactionRepository.findByTransactionUuid(CANCEL_UUID))
                .willReturn(Optional.of(cancelTx));

        assertThatThrownBy(() -> cancelStateWriter.applyRecoveryResult(CANCEL_UUID, bankStatus))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
    }

    // ===== 픽스처 =====

    private Transaction paymentTransaction(Long toPartyId, TransactionStatus status) {
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(toPartyId, PartyType.MERCHANT);

        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(TRANSACTION_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(status)
                .fromParty(userParty)
                .toParty(merchantParty)
                .fromWallet(wallet(1L, userParty, "0x-user"))
                .toWallet(wallet(2L, merchantParty, "0x-merchant"))
                .amount(new BigDecimal("10000"))
                .approvalNumber("APV-2026-00000100")
                .itemName("아메리카노")
                .build();
    }

    private Transaction cancelTransaction() {
        return cancelTransaction(TransactionStatus.PROCESSING);
    }

    private Transaction cancelTransaction(TransactionStatus status) {
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);
        Party userParty = party(USER_PARTY_ID, PartyType.USER);

        return Transaction.builder()
                .id(CANCEL_TRANSACTION_ID)
                .transactionUuid(CANCEL_UUID)
                .originalTransactionUuid(TRANSACTION_UUID)
                .transactionType(TransactionType.CANCEL)
                .status(status)
                .fromParty(merchantParty)
                .toParty(userParty)
                .fromWallet(wallet(2L, merchantParty, "0x-merchant"))
                .toWallet(wallet(1L, userParty, "0x-user"))
                .amount(new BigDecimal("10000"))
                .build();
    }

    private BankTransactionStatusResponse bankStatus(
            TransactionStatus status, Long bankTransactionId, String txHash) {
        return new BankTransactionStatusResponse(
                CANCEL_UUID,
                bankTransactionId,
                status,
                txHash,
                LocalDateTime.of(2026, 5, 27, 14, 30));
    }

    private Merchant merchant(String pinHash) {
        return Merchant.builder()
                .id(1L)
                .party(party(MERCHANT_PARTY_ID, PartyType.MERCHANT))
                .merchantName("성수 한강카페")
                .username("merchant")
                .passwordHash("password-hash")
                .paymentPinHash(pinHash)
                .businessNumber("123-45-67890")
                .ownerName("김한강")
                .build();
    }

    private Wallet wallet(Long id, Party party, String address) {
        return Wallet.builder().id(id).party(party).address(address).build();
    }

    private Party party(Long id, PartyType type) {
        return Party.builder().id(id).partyType(type).build();
    }
}
