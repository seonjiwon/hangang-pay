package family.fisa.hangangpaybank.domain.transaction.service.exchange.v1;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.ExchangeBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.account.code.AccountErrorCode;
import family.fisa.hangangpaybank.domain.institution.code.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.account.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.account.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.account.entity.LedgerType;
import family.fisa.hangangpaybank.domain.account.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.ExchangeSyncRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
import family.fisa.hangangpaybank.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환전 흐름의 메인 트랜잭션 및 실패 보상을 담당하는 서비스.
 *
 * <p>executeExchange는 명시적 @Transactional(REQUIRED)로 현금 선입금 + account_ledger SUCCESS + outbox NEW를
 * 하나의 트랜잭션에 커밋한다. 실패 보상(account_ledger FAILED)만 REQUIRES_NEW로 분리해 메인 트랜잭션이 롤백돼도 실패 기록이 남게
 * 한다(오케스트레이터가 cross-bean으로 호출).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeStateWriterV1 implements ExchangeStateWriter {

    private final InstitutionRepository institutionRepository;
    private final BankWalletRepository bankWalletRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final BlockchainSyncRequester syncRequester;

    @Override
    @Transactional
    public ExchangeResponse executeExchange(ExchangeRequest request) {
        log.info(
                "[bank] exchange 시작. transactionUuid={}, institutionId={}, amount={}",
                request.transactionUuid(),
                request.institutionId(),
                request.amount());

        // 1. 멱등성 확인
        Optional<AccountLedger> existingOpt =
                accountLedgerRepository.findByIdempotentKey(request.transactionUuid());
        if (existingOpt.isPresent()) {
            return handleIdempotent(request, existingOpt.get());
        }

        // 2. 기관 존재 검증 (lock 이전)
        findInstitution(request.institutionId());

        // 3. wallet → account 순서로 잠금 획득 후 잔액 재검증
        BankWallet bankWallet = findBankWalletWithLock(request.walletAddress());
        BankAccount bankAccount =
                findBankAccountWithLock(request.institutionId(), request.accountNumber());

        ensureSufficientBalance(bankWallet.getBalance(), request.amount());

        // 4. 토큰 차감
        bankWallet.decreaseBalance(request.amount());

        // 5. 현금 입금
        bankAccount.increaseBalance(request.amount());
        BigDecimal newAccountBalance = bankAccount.getBalance();
        AccountLedger accountLedger =
                accountLedgerRepository.save(
                        AccountLedger.builder()
                                .bankAccount(bankAccount)
                                .ledgerType(LedgerType.DEPOSIT)
                                .status(LedgerStatus.SUCCESS)
                                .amount(request.amount())
                                .balanceAfter(newAccountBalance)
                                .idempotentKey(request.transactionUuid())
                                .build());

        // 6. blockchain_ledger(PENDING) + outbox(NEW) 생성
        ExchangeBlockchainPayload payload =
                new ExchangeBlockchainPayload(
                        request.institutionId(), bankWallet.getWalletAddress(), request.amount());
        syncRequester.request(new ExchangeSyncRequest(request.transactionUuid(), payload));

        log.info(
                "[bank] exchange 접수 완료(PROCESSING). transactionUuid={}", request.transactionUuid());
        return ExchangeResponse.accepted(request, accountLedger, newAccountBalance);
    }

    /** 환전 실패 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedAccountLedger(ExchangeRequest request) {
        if (accountLedgerRepository.findByIdempotentKey(request.transactionUuid()).isPresent()) {
            log.info(
                    "[bank] account_ledger 기록 존재 — FAILED 보상 생략. transactionUuid={}",
                    request.transactionUuid());
            return;
        }

        // 1. 환전 실패 저장
        BankAccount bankAccount = findBankAccount(request.institutionId(), request.accountNumber());
        accountLedgerRepository.save(
                AccountLedger.builder()
                        .bankAccount(bankAccount)
                        .ledgerType(LedgerType.DEPOSIT)
                        .status(LedgerStatus.FAILED)
                        .amount(request.amount())
                        .balanceAfter(bankAccount.getBalance())
                        .idempotentKey(request.transactionUuid())
                        .build());

        log.info(
                "[bank] account_ledger FAILED 저장 완료. transactionUuid={}",
                request.transactionUuid());
    }

    /** 동일 transactionUuid 재요청 처리. 결제 멱등 분기와 동일한 정책 */
    private ExchangeResponse handleIdempotent(ExchangeRequest request, AccountLedger existing) {
        switch (existing.getStatus()) {
            case SUCCESS -> {
                log.info(
                        "[bank] 멱등성: 환전 SUCCESS 재요청. transactionUuid={}",
                        request.transactionUuid());

                BankAccount bankAccount =
                        findBankAccount(request.institutionId(), request.accountNumber());

                return ExchangeResponse.from(
                        request.transactionUuid(), existing, bankAccount.getBalance());
            }

            // PENDING = 아직 처리중
            case PENDING ->
                    throw new BusinessException(
                            TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
            // FAILED = 이미 실패한 거래 재요청
            default -> throw new BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_FAILED);
        }
    }

    private void findInstitution(Long institutionId) {
        institutionRepository
                .findById(institutionId)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.INSTITUTION_NOT_FOUND));
    }

    private BankWallet findBankWalletWithLock(String walletAddress) {
        return bankWalletRepository
                .findByWalletAddressWithLock(normalizeAddress(walletAddress))
                .orElseThrow(
                        () -> new BusinessException(WalletErrorCode.BANK_WALLET_NOT_FOUND));
    }

    private BankAccount findBankAccount(Long institutionId, String accountNumber) {
        return bankAccountRepository
                .findByInstitution_IdAndAccountNumber(institutionId, accountNumber)
                .orElseThrow(
                        () -> new BusinessException(AccountErrorCode.BANK_ACCOUNT_NOT_FOUND));
    }

    private BankAccount findBankAccountWithLock(Long institutionId, String accountNumber) {
        return bankAccountRepository
                .findByInstitution_IdAndAccountNumberWithLock(institutionId, accountNumber)
                .orElseThrow(
                        () -> new BusinessException(AccountErrorCode.BANK_ACCOUNT_NOT_FOUND));
    }

    /** 토큰 잔액 검증 — DB 잔액(서비스 단위)과 요청 금액을 직접 비교 (결제 ensureSufficientBalance와 동일). */
    private static void ensureSufficientBalance(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
        }
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }
}
