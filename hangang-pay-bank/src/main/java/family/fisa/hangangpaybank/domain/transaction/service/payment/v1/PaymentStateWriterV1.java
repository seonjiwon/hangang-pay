package family.fisa.hangangpaybank.domain.transaction.service.payment.v1;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.CancelBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.PaymentBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerDirection;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.wallet.repository.WalletLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제/취소 흐름의 메인 트랜잭션 및 상태 전환을 담당하는 서비스.
 *
 * <p>executePayment/executeCancel은 클래스 레벨 @Transactional(REQUIRED)로 메인 DB 트랜잭션을 수행한다. 성공
 * WalletLedger는 부모 트랜잭션에 참여해 잔액 변경/outbox 생성과 함께 커밋한다. 실패 WalletLedger(FAILED)만 REQUIRES_NEW로 분리해
 * 메인 트랜잭션이 롤백돼도 실패 기록이 남게 한다(오케스트레이터가 cross-bean으로 호출).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentStateWriterV1 implements PaymentStateWriter {

    private final BankWalletRepository bankWalletRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final BlockchainSyncRequester syncRequester;
    private final ContractCallService contractCallService;

    @Override
    public PaymentResponse executePayment(PaymentRequest request) {
        log.info(
                "[bank] payment 시작. transactionUuid={}, amount={}",
                request.transactionUuid(),
                request.amount());

        // 1. 멱등성 확인 - DB lock 이전
        Optional<WalletLedger> existingOpt = findExisting(request.transactionUuid());
        if (existingOpt.isPresent()) {
            WalletLedger existing = existingOpt.get();
            switch (existing.getStatus()) {
                case SUCCESS -> {
                    log.info(
                            "[bank] 멱등성: SUCCESS 재요청. transactionUuid={}",
                            request.transactionUuid());
                    BankWallet fromWallet = fetchBankWallet(request.fromWalletAddress());
                    BankWallet toWallet = fetchBankWallet(request.toWalletAddress());
                    return PaymentResponse.from(
                            request.transactionUuid(),
                            WalletLedgerStatus.SUCCESS,
                            existing.getConfirmedAt(),
                            fromWallet.getBalance(),
                            toWallet.getBalance());
                }
                case PENDING -> throwDuplicateProcessing(request.transactionUuid());
                default -> throwAlreadyFailed(request.transactionUuid());
            }
        }

        // 2. merchant whitelist 확인
        ensureMerchant(normalizeAddress(request.toWalletAddress()));

        // 3. wallet lock 걸고 잔액 이체
        BankWallet fromWallet = findBankWalletWithLock(request.fromWalletAddress());
        BankWallet toWallet = findBankWalletWithLock(request.toWalletAddress());

        ensureSufficientBalance(fromWallet.getBalance(), request.amount());

        fromWallet.updateBalance(fromWallet.getBalance().subtract(request.amount()));
        toWallet.updateBalance(toWallet.getBalance().add(request.amount()));

        // 4. wallet ledger에 success 기록 - 같은 트랜잭션 내에서 수행
        LocalDateTime confirmedAt =
                saveSuccessWalletLedgers(
                        fromWallet, toWallet, request.transactionUuid(), request.amount());

        // 5. blockchain 비동기 요청
        syncRequester.request(
                new PaymentSyncRequest(
                        request.transactionUuid(),
                        new PaymentBlockchainPayload(
                                fromWallet.getWalletAddress(),
                                toWallet.getWalletAddress(),
                                request.amount())));

        log.info("[bank] payment 완료. transactionUuid={}", request.transactionUuid());
        return PaymentResponse.from(
                request.transactionUuid(),
                WalletLedgerStatus.SUCCESS,
                confirmedAt,
                fromWallet.getBalance(),
                toWallet.getBalance());
    }

    @Override
    public CancelResponse executeCancel(CancelRequest request) {
        log.info(
                "[bank] cancel 시작. transactionUuid={}, originalTransactionUuid={}",
                request.transactionUuid(),
                request.originalTransactionUuid());

        Optional<WalletLedger> existingOpt = findExisting(request.transactionUuid());
        if (existingOpt.isPresent()) {
            WalletLedger existing = existingOpt.get();
            switch (existing.getStatus()) {
                case SUCCESS -> {
                    log.info(
                            "[bank] 멱등성: 취소 SUCCESS 재요청. transactionUuid={}",
                            request.transactionUuid());
                    BankWallet fromWallet = fetchBankWallet(request.fromWalletAddress());
                    BankWallet toWallet = fetchBankWallet(request.toWalletAddress());
                    return CancelResponse.from(
                            request.transactionUuid(),
                            request.originalTransactionUuid(),
                            WalletLedgerStatus.SUCCESS,
                            existing.getConfirmedAt(),
                            fromWallet.getBalance(),
                            toWallet.getBalance());
                }
                case PENDING -> throwDuplicateProcessing(request.transactionUuid());
                default -> throwAlreadyFailed(request.transactionUuid());
            }
        }

        ensureMerchant(normalizeAddress(request.fromWalletAddress()));

        // cancel의 fromWallet은 merchant, toWallet은 user이므로 to -> from 순서로 잠근다.
        BankWallet toWallet = findBankWalletWithLock(request.toWalletAddress());
        BankWallet fromWallet = findBankWalletWithLock(request.fromWalletAddress());

        ensureSufficientBalance(fromWallet.getBalance(), request.amount());

        fromWallet.updateBalance(fromWallet.getBalance().subtract(request.amount()));
        toWallet.updateBalance(toWallet.getBalance().add(request.amount()));

        LocalDateTime confirmedAt =
                saveSuccessWalletLedgers(
                        fromWallet, toWallet, request.transactionUuid(), request.amount());

        syncRequester.request(
                new CancelSyncRequest(
                        request.transactionUuid(),
                        new CancelBlockchainPayload(
                                request.originalTransactionUuid(),
                                fromWallet.getWalletAddress(),
                                toWallet.getWalletAddress(),
                                request.amount())));

        log.info("[bank] cancel 완료. transactionUuid={}", request.transactionUuid());
        return CancelResponse.from(
                request.transactionUuid(),
                request.originalTransactionUuid(),
                WalletLedgerStatus.SUCCESS,
                confirmedAt,
                fromWallet.getBalance(),
                toWallet.getBalance());
    }

    /** transactionUuid 기준으로 기존 wallet_ledger를 조회한다. 결과에 따라 멱등성 분기를 호출자가 처리한다. */
    @Override
    public Optional<WalletLedger> findExisting(String transactionUuid) {
        return walletLedgerRepository.findFirstByTransactionUuid(transactionUuid);
    }

    /** 결제/취소 성공 WalletLedger를 부모 트랜잭션 안에서 저장한다. */
    @Override
    public LocalDateTime saveSuccessWalletLedgers(
            BankWallet fromWallet, BankWallet toWallet, String transactionUuid, BigDecimal amount) {
        LocalDateTime confirmedAt = LocalDateTime.now();
        saveWalletLedgerPair(
                fromWallet,
                toWallet,
                transactionUuid,
                amount,
                WalletLedgerStatus.SUCCESS,
                confirmedAt);
        log.info("[payment] WalletLedger SUCCESS 저장 완료. uuid={}", transactionUuid);
        return confirmedAt;
    }

    /** 결제/취소 실패 WalletLedger를 주소 기반으로 재조회한 뒤 독립 트랜잭션에 저장한다. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedWalletLedgersByAddress(
            String fromWalletAddress,
            String toWalletAddress,
            String transactionUuid,
            BigDecimal amount) {
        BankWallet fromWallet = fetchBankWallet(fromWalletAddress);
        BankWallet toWallet = fetchBankWallet(toWalletAddress);
        saveWalletLedgerPair(
                fromWallet, toWallet, transactionUuid, amount, WalletLedgerStatus.FAILED, null);
        log.info("[payment] WalletLedger FAILED 저장 완료. uuid={}", transactionUuid);
    }

    /** PENDING 중복 요청 에러 */
    @Override
    public void throwDuplicateProcessing(String transactionUuid) {
        log.warn("[bank] 중복 처리 요청 감지. transactionUuid={}", transactionUuid);
        throw new BusinessException(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
    }

    /** FAILED 거래 재시도 에러 */
    @Override
    public void throwAlreadyFailed(String transactionUuid) {
        log.warn("[bank] 이미 실패한 거래 재요청. transactionUuid={}", transactionUuid);
        throw new BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_FAILED);
    }

    private void saveWalletLedgerPair(
            BankWallet fromWallet,
            BankWallet toWallet,
            String transactionUuid,
            BigDecimal amount,
            WalletLedgerStatus status,
            LocalDateTime confirmedAt) {
        walletLedgerRepository.save(
                WalletLedger.builder()
                        .transactionUuid(transactionUuid)
                        .bankWallet(fromWallet)
                        .direction(WalletLedgerDirection.DEBIT)
                        .status(status)
                        .amount(amount)
                        .confirmedAt(confirmedAt)
                        .build());

        walletLedgerRepository.save(
                WalletLedger.builder()
                        .transactionUuid(transactionUuid)
                        .bankWallet(toWallet)
                        .direction(WalletLedgerDirection.CREDIT)
                        .status(status)
                        .amount(amount)
                        .confirmedAt(confirmedAt)
                        .build());
    }

    private void ensureMerchant(String walletAddress) {
        if (!contractCallService.isMerchant(walletAddress)) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        }
    }

    private static void ensureSufficientBalance(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
        }
    }

    private BankWallet fetchBankWallet(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddress(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(WalletErrorCode.BANK_WALLET_NOT_FOUND));
    }

    private BankWallet findBankWalletWithLock(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddressWithLock(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(WalletErrorCode.BANK_WALLET_NOT_FOUND));
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }

    private record PaymentSyncRequest(String transactionUuid, PaymentBlockchainPayload data)
            implements BlockchainSyncRequest {
        public BlockchainSyncType type() {
            return BlockchainSyncType.PAYMENT;
        }

        public String orderingKey() {
            return data.fromWalletAddress();
        }

        public Object payload() {
            return data;
        }
    }

    private record CancelSyncRequest(String transactionUuid, CancelBlockchainPayload data)
            implements BlockchainSyncRequest {
        public BlockchainSyncType type() {
            return BlockchainSyncType.CANCEL;
        }

        public String orderingKey() {
            return data.toWalletAddress();
        }

        public Object payload() {
            return data;
        }
    }
}
