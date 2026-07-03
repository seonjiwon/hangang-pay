package family.fisa.hangangpaybank.domain.transaction.service.charge.v1;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.account.code.AccountErrorCode;
import family.fisa.hangangpaybank.domain.institution.code.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.account.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.wallet.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.account.entity.LedgerStatus;
import family.fisa.hangangpaybank.domain.account.entity.LedgerType;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerDirection;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerStatus;
import family.fisa.hangangpaybank.domain.account.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.wallet.repository.WalletLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import family.fisa.hangangpaybank.domain.transaction.service.charge.ChargeStateWriter;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeStateWriterV1 implements ChargeStateWriter {

    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    private final BankAccountRepository bankAccountRepository;
    private final BankWalletRepository bankWalletRepository;
    private final InstitutionRepository institutionRepository;
    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final AccountLedgerRepository accountLedgerRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final ContractCallService contractCallService;

    @Transactional(readOnly = true)
    public Optional<BlockchainLedger> findExistingCharge(String transactionUuid) {
        return blockchainLedgerRepository.findByIdempotentKey(transactionUuid);
    }

    // 멱등성 처리 - 이미 처리된 거래인 경우 반환
    @Transactional(readOnly = true)
    public ChargeResponse getSuccessResponse(
            ChargeRequest request, BlockchainLedger existingLedger) {
        AccountLedger accountLedger =
                accountLedgerRepository
                        .findByIdempotentKey(request.transactionUuid())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "AccountLedger not found: uuid="
                                                        + request.transactionUuid()));
        BankWallet bankWallet = findBankWallet(request.walletAddress());
        return new ChargeResponse(
                request.transactionUuid(),
                accountLedger.getId(),
                existingLedger.getTxHash(),
                existingLedger.getBlockNumber(),
                existingLedger.getConfirmedAt(),
                bankWallet.getBalance());
    }

    // 중복 요청인 경우 예외 반환
    public void throwDuplicateProcessing(String transactionUuid) {
        log.warn("[charge] 중복 처리 요청 감지. transactionUuid={}", transactionUuid);
        throw new BusinessException(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
    }

    // 이미 처리된 거래인 경우 예외 반환
    public void throwAlreadyFailed(String transactionUuid) {
        log.warn("[charge] 이미 실패한 거래 재요청. transactionUuid={}", transactionUuid);
        throw new BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_FAILED);
    }

    // 충전 가능 여부를 read only 트랜잭션으로 검증
    @Transactional(readOnly = true)
    public void validateChargeRequest(ChargeRequest request) {
        findInstitution(request.institutionId());
        BankAccount bankAccount = findBankAccount(request.institutionId(), request.accountNumber());
        findBankWallet(request.walletAddress());
        ensureSufficientBalance(bankAccount.getBalance(), request.amount());
    }

    // blockchain ledger에 pending 상태 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long claimPendingCharge(ChargeRequest request) {
        Institution institution = findInstitution(request.institutionId());
        try {
            BlockchainLedger ledger =
                    blockchainLedgerRepository.save(
                            BlockchainLedger.of(
                                    institution,
                                    BlockchainTxStatus.PENDING,
                                    request.transactionUuid()));
            return ledger.getId();
        } catch (DataIntegrityViolationException e) {
            // DB 동시 요청 시 스프링이 던지는 예외를 비즈니스 에러로 변환해서 던짐
            throw new BusinessException(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
        }
    }

    /** charge 전용 상태 반영. 동기 blockchain 호출과 기존 응답 계약을 유지한다. */
    @Transactional
    public ChargeResponse executeCharge(ChargeRequest request, Long ledgerId) {
        // 잔액 다시 확인
        BankAccount bankAccount =
                findBankAccountWithLock(request.institutionId(), request.accountNumber());
        ensureSufficientBalance(bankAccount.getBalance(), request.amount());
        BankWallet bankWallet = findBankWalletWithLock(request.walletAddress());

        BigDecimal newAccountBalance = bankAccount.getBalance().subtract(request.amount());
        BigDecimal newWalletBalance = bankWallet.getBalance().add(request.mintAmount());

        // 블록체인 동기 요청 - 직접 컨트랙트 함수 호출
        TransactionReceipt receipt =
                contractCallService.charge(
                        request.institutionId(),
                        bankWallet.getWalletAddress(),
                        toTokenUnit(request.mintAmount()));

        // 계좌/지갑 잔액 업데이트
        bankAccount.updateBalance(newAccountBalance);
        bankWallet.updateBalance(newWalletBalance);

        // account ledger 기록
        AccountLedger savedAccountLedger =
                accountLedgerRepository.save(
                        AccountLedger.builder()
                                .bankAccount(bankAccount)
                                .ledgerType(LedgerType.WITHDRAWAL)
                                .status(LedgerStatus.SUCCESS)
                                .amount(request.amount())
                                .balanceAfter(newAccountBalance)
                                .idempotentKey(request.transactionUuid())
                                .build());

        // wallet ledger 기록
        walletLedgerRepository.save(
                WalletLedger.builder()
                        .transactionUuid(request.transactionUuid())
                        .bankWallet(bankWallet)
                        .direction(WalletLedgerDirection.CREDIT)
                        .status(WalletLedgerStatus.SUCCESS)
                        .amount(request.mintAmount())
                        .confirmedAt(LocalDateTime.now())
                        .build());

        // blockchain ledger 기록
        BlockchainLedger ledger = fetchLedger(ledgerId, request.transactionUuid());

        log.info(
                "[charge] CHARGE 처리 완료. uuid={}, txHash={}, blockNumber={}",
                request.transactionUuid(),
                ledger.getTxHash(),
                ledger.getBlockNumber());

        ledger.markSuccess(receipt);

        return new ChargeResponse(
                request.transactionUuid(),
                savedAccountLedger.getId(),
                ledger.getTxHash(),
                ledger.getBlockNumber(),
                ledger.getConfirmedAt(),
                newWalletBalance);
    }

    /** 충전 실패 시 blockchain ledger에 실패 기록. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markChargeFailed(Long ledgerId, String transactionUuid) {
        BlockchainLedger ledger = fetchLedger(ledgerId, transactionUuid);
        ledger.markFailed();
    }

    /** 충전 실패 시 wallet ledger, account ledger에 모두 failed 기록을 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailedChargeLedgers(ChargeRequest request) {
        Optional<BankAccount> bankAccountOpt =
                bankAccountRepository.findByInstitution_IdAndAccountNumber(
                        request.institutionId(), request.accountNumber());
        bankAccountOpt.ifPresent(
                bankAccount ->
                        accountLedgerRepository.save(
                                AccountLedger.builder()
                                        .bankAccount(bankAccount)
                                        .ledgerType(LedgerType.WITHDRAWAL)
                                        .status(LedgerStatus.FAILED)
                                        .amount(request.amount())
                                        .balanceAfter(bankAccount.getBalance())
                                        .idempotentKey(request.transactionUuid())
                                        .build()));

        Optional<BankWallet> bankWalletOpt =
                bankWalletRepository.findByWalletAddress(normalizeAddress(request.walletAddress()));
        bankWalletOpt.ifPresent(
                bankWallet ->
                        walletLedgerRepository.save(
                                WalletLedger.builder()
                                        .transactionUuid(request.transactionUuid())
                                        .bankWallet(bankWallet)
                                        .direction(WalletLedgerDirection.CREDIT)
                                        .status(WalletLedgerStatus.FAILED)
                                        .amount(request.mintAmount())
                                        .build()));

        log.info("[charge] FAILED ledger 저장 완료. uuid={}", request.transactionUuid());
    }

    private Institution findInstitution(Long institutionId) {
        return institutionRepository
                .findById(institutionId)
                .orElseThrow(
                        () -> new BusinessException(InstitutionErrorCode.INSTITUTION_NOT_FOUND));
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

    private BankWallet findBankWallet(String walletAddress) {
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

    private static BigInteger toTokenUnit(BigDecimal amount) {
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }

    private BlockchainLedger fetchLedger(Long ledgerId, String transactionUuid) {
        return blockchainLedgerRepository
                .findById(ledgerId)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "BlockchainLedger not found: id="
                                                + ledgerId
                                                + ", uuid="
                                                + transactionUuid));
    }
}
