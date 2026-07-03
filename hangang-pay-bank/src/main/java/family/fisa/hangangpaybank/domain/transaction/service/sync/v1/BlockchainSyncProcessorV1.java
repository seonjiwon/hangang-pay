package family.fisa.hangangpaybank.domain.transaction.service.sync.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.SubmittedBlockchainTx;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.CancelBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.ExchangeBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.PaymentBlockchainPayload;
import family.fisa.hangangpaybank.domain.transaction.service.sync.BlockchainLedgerStateWriter;
import family.fisa.hangangpaybank.domain.transaction.service.sync.BlockchainSyncProcessor;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * RabbitMQ 컨슈머에서 수신한 블록체인 동기화 메시지를 처리한다.
 *
 * <p>트랜잭션 경계: - 이 클래스 자체는 @Transactional 없음. 블록체인 I/O 중 DB 트랜잭션을 점유하지 않기 위해. - DB 상태
 * 전환(SUBMITTED/SUCCESS/FAILED)은 BlockchainLedgerStateWriter를 통해 각각 REQUIRES_NEW로 커밋. - txHash
 * 체크포인트(SUBMITTED)를 submit 직후 즉시 커밋해 프로세스 재시작 시 재제출 방지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainSyncProcessorV1 implements BlockchainSyncProcessor {

    /** ERC20 기본 decimals (1e18). 환전(refund)은 charge 계열과 동일하게 1e18 스케일 적용. */
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    // 일시적 인프라 장애만 재시도 대상. 비즈니스/데이터 오류는 retry해도 해소되지 않으므로 FATAL 처리.
    private static final Set<BlockchainErrorCode> RETRYABLE =
            Set.of(
                    BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED,
                    BlockchainErrorCode.BLOCKCHAIN_RECEIPT_TIMEOUT);

    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final ContractCallService contractCallService;
    private final BlockchainLedgerStateWriter ledgerStateWriter;
    private final ObjectMapper objectMapper;

    public void processPayment(BlockchainSyncMessage message) {
        PaymentBlockchainPayload payload = parsePayload(message, PaymentBlockchainPayload.class);
        BlockchainLedger ledger = loadLedger(message.blockchainLedgerId());

        // 이미 처리된 메시지인 경우 무시
        if (isTerminal(ledger)) {
            log.info(
                    "[consumer] 이미 종료된 ledger. uuid={}, status={}",
                    message.transactionUuid(),
                    ledger.getStatus());
            return;
        }

        try {
            // 1. txHash 가져오기
            String txHash = resolvePaymentTxHash(ledger, message, payload);

            // 2. txHash로 receipt 가져오기 - 블록체인 노드 폴링
            TransactionReceipt receipt = contractCallService.waitForReceiptByHash(txHash);

            // 3. receipt 결과 -> 즉시 커밋 (REQUIRES_NEW)
            if (receipt.isStatusOK()) {
                ledgerStateWriter.markSuccess(ledger.getId(), message.transactionUuid(), receipt);
            } else {
                ledgerStateWriter.markFailed(ledger.getId(), message.transactionUuid());
            }
            log.info(
                    "[consumer] PAYMENT receipt 처리 완료. uuid={}, txHash={}, statusOk={}",
                    message.transactionUuid(),
                    txHash,
                    receipt.isStatusOK());

        } catch (BusinessException e) {
            // 4. 실패 -> retryable이면 DLQ에 적재, 아닐 경우 FAILED 처리
            handleFailure(e, ledger.getId(), message.transactionUuid());
        }
    }

    public void processCancel(BlockchainSyncMessage message) {
        CancelBlockchainPayload payload = parsePayload(message, CancelBlockchainPayload.class);
        BlockchainLedger ledger = loadLedger(message.blockchainLedgerId());

        if (isTerminal(ledger)) {
            log.info(
                    "[consumer] 이미 종료된 ledger. uuid={}, status={}",
                    message.transactionUuid(),
                    ledger.getStatus());
            return;
        }

        try {
            String txHash = resolveCancelTxHash(ledger, message, payload);
            TransactionReceipt receipt = contractCallService.waitForReceiptByHash(txHash);
            if (receipt.isStatusOK()) {
                ledgerStateWriter.markSuccess(ledger.getId(), message.transactionUuid(), receipt);
            } else {
                ledgerStateWriter.markFailed(ledger.getId(), message.transactionUuid());
            }
            log.info(
                    "[consumer] CANCEL receipt 처리 완료. uuid={}, txHash={}, statusOk={}",
                    message.transactionUuid(),
                    txHash,
                    receipt.isStatusOK());

        } catch (BusinessException e) {
            handleFailure(e, ledger.getId(), message.transactionUuid());
        }
    }

    public void processExchange(BlockchainSyncMessage message) {
        ExchangeBlockchainPayload payload = parsePayload(message, ExchangeBlockchainPayload.class);
        BlockchainLedger ledger = loadLedger(message.blockchainLedgerId());

        // 이미 처리된 메시지인 경우 무시 (재투입 멱등성)
        if (isTerminal(ledger)) {
            log.info(
                    "[consumer] 이미 종료된 ledger. uuid={}, status={}",
                    message.transactionUuid(),
                    ledger.getStatus());
            return;
        }

        try {
            // 1. txHash 확보 (미제출 시 refund submit 후 SUBMITTED 체크포인트 커밋)
            String txHash = resolveExchangeTxHash(ledger, message, payload);

            // 2. txHash로 receipt 폴링
            TransactionReceipt receipt = contractCallService.waitForReceiptByHash(txHash);

            // 3. 성공 -> SUCCESS 즉시 커밋 (REQUIRES_NEW)
            ledgerStateWriter.markSuccess(ledger.getId(), message.transactionUuid(), receipt);

            log.info(
                    "[consumer] EXCHANGE receipt 처리 완료. uuid={}, txHash={}",
                    message.transactionUuid(),
                    txHash);

        } catch (BusinessException e) {
            // retryable이면 NACK 후 DLQ, 아니면 FAILED 마킹.
            // 주의: 환전은 토큰/현금을 요청 스레드에서 선반영하므로 FAILED 시 수동 정산 대상.
            handleFailure(e, ledger.getId(), message.transactionUuid());
        }
    }

    /**
     * 환전 txHash 확보. 이미 SUBMITTED(txHash 존재)면 재제출 없이 재사용, 없으면 refund(burn) 제출 후 SUBMITTED 체크포인트 즉시
     * 커밋. (프로세스 재시작 시 재제출 방지)
     */
    private String resolveExchangeTxHash(
            BlockchainLedger ledger,
            BlockchainSyncMessage message,
            ExchangeBlockchainPayload payload) {
        if (ledger.getTxHash() != null) {
            log.info("[consumer] 기존 txHash 재사용. uuid={}", message.transactionUuid());
            return ledger.getTxHash();
        }

        BigInteger amount = toTokenUnit(payload.amount());
        SubmittedBlockchainTx submitted =
                contractCallService.submitRefund(
                        payload.institutionId(), payload.walletAddress(), amount);
        ledgerStateWriter.markSubmitted(
                ledger.getId(), message.transactionUuid(), submitted.txHash());
        log.info(
                "[consumer] EXCHANGE submitted. uuid={}, txHash={}",
                message.transactionUuid(),
                submitted.txHash());
        return submitted.txHash();
    }

    private static BigInteger toTokenUnit(BigDecimal amount) {
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }

    /**
     * txHash가 없으면 블록체인에 submit하고 즉시 체크포인트(SUBMITTED) 커밋. 이미 txHash가 있으면(SUBMITTED 상태로 재투입된 경우)
     * submit 없이 기존 txHash 반환.
     */
    private String resolvePaymentTxHash(
            BlockchainLedger ledger,
            BlockchainSyncMessage message,
            PaymentBlockchainPayload payload) {
        if (ledger.getTxHash() != null) {
            log.info("[consumer] 기존 txHash 재사용. uuid={}", message.transactionUuid());
            return ledger.getTxHash();
        }
        BigInteger amount = toTokenUnit(payload.amount());
        SubmittedBlockchainTx submitted =
                contractCallService.submitPayment(
                        message.transactionUuid(),
                        payload.fromWalletAddress(),
                        payload.toWalletAddress(),
                        amount);
        ledgerStateWriter.markSubmitted(
                ledger.getId(), message.transactionUuid(), submitted.txHash());
        log.info(
                "[consumer] PAYMENT submitted. uuid={}, txHash={}",
                message.transactionUuid(),
                submitted.txHash());
        return submitted.txHash();
    }

    private String resolveCancelTxHash(
            BlockchainLedger ledger,
            BlockchainSyncMessage message,
            CancelBlockchainPayload payload) {
        if (ledger.getTxHash() != null) {
            log.info("[consumer] 기존 txHash 재사용. uuid={}", message.transactionUuid());
            return ledger.getTxHash();
        }
        BigInteger amount = toTokenUnit(payload.amount());
        SubmittedBlockchainTx submitted =
                contractCallService.submitCancelPayment(
                        message.transactionUuid(),
                        payload.fromWalletAddress(),
                        payload.toWalletAddress(),
                        amount);
        ledgerStateWriter.markSubmitted(
                ledger.getId(), message.transactionUuid(), submitted.txHash());
        log.info(
                "[consumer] CANCEL submitted. uuid={}, txHash={}",
                message.transactionUuid(),
                submitted.txHash());
        return submitted.txHash();
    }

    /**
     * retryable 오류는 예외를 다시 던져 Spring AMQP가 메시지 처리를 실패로 판단하게 한다. 이후 requeue/DLQ 여부는 listener
     * container의 설정에 따른다 - RabbitMqConfig 참고.
     */
    private void handleFailure(BusinessException e, Long ledgerId, String transactionUuid) {
        if (RETRYABLE.contains(e.getCode())) {
            log.warn(
                    "[consumer] retryable 오류 — NACK 후 DLQ 재투입. uuid={}, code={}",
                    transactionUuid,
                    e.getCode());
            throw e;
        }
        // 컨트랙트가 AlreadyProcessed를 반환한 경우: 이전 tx가 이미 온체인에 확정됐음을 의미.
        // 재시도해도 동일 결과이므로 SUCCESS로 마킹하고 ACK.
        if (BlockchainErrorCode.BLOCKCHAIN_ALREADY_PROCESSED.equals(e.getCode())) {
            log.warn(
                    "[consumer] 온체인 AlreadyProcessed — 이전 tx 확정으로 SUCCESS 마킹. uuid={}",
                    transactionUuid);
            ledgerStateWriter.markAlreadyProcessed(ledgerId, transactionUuid);
            return;
        }
        // 코드/인프라 버그 또는 발생 확률이 사실상 없는 비즈니스 예외. 보상 없이 FAILED 마킹 후 ACK.
        log.error(
                "[consumer] non-retryable 오류. 수동 조사 필요. uuid={}, code={}",
                transactionUuid,
                e.getCode(),
                e);
        ledgerStateWriter.markFailed(ledgerId, transactionUuid);
    }

    private boolean isTerminal(BlockchainLedger ledger) {
        return ledger.getStatus() == BlockchainTxStatus.SUCCESS
                || ledger.getStatus() == BlockchainTxStatus.FAILED;
    }

    private BlockchainLedger loadLedger(Long ledgerId) {
        return blockchainLedgerRepository
                .findById(ledgerId)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "BlockchainLedger not found: id=" + ledgerId));
    }

    private <T> T parsePayload(BlockchainSyncMessage message, Class<T> type) {
        try {
            return objectMapper.treeToValue(message.payload(), type);
        } catch (Exception e) {
            throw new IllegalStateException("payload 파싱 실패. type=" + type.getSimpleName(), e);
        }
    }
}
