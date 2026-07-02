package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelReconcileService;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelStateWriter;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentReconcileService;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UNKNOWN/오래된 PROCESSING 거래를 은행 재조회로 확정하는 reconcile 스케줄러.
 *
 * <p>결제·취소·환전 reconcile을 함께 담당한다(모두 1분 주기). 실제 reconcile 로직은 흐름별 {@code *ReconcileService}가 갖고,
 * 스케줄러는 대상 수집 + 반복 + 포기(EXPIRED) sweep만 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileScheduler {

    /** PROCESSING이 "죽어서 미반영"으로 간주되는 임계 시간 (결제 특성상 5분) */
    private static final int PROCESSING_STALE_MINUTES = 5;

    /** 자동 reconcile 포기 임계 시도 횟수 (결제·취소·환전 공통) */
    private static final int MAX_RECONCILE_ATTEMPTS = 10;

    private final TransactionRepository transactionRepository;
    private final PaymentReconcileService paymentReconcileService;
    private final CancelReconcileService cancelReconcileService;
    private final ExchangeReconcileService exchangeReconcileService;
    private final PaymentStateWriter paymentStateWriter;
    private final CancelStateWriter cancelStateWriter;
    private final ExchangeStateWriter exchangeStateWriter;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "reconcilePayments", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void reconcilePayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PROCESSING_STALE_MINUTES);

        List<Transaction> targets =
                transactionRepository.findReconcileTargets(
                        TransactionType.PAYMENT, MAX_RECONCILE_ATTEMPTS, threshold);
        log.info("결제 reconcile 스케줄러 실행: 대상 건수={}", targets.size());

        for (Transaction tx : targets) {
            String transactionUuid = tx.getTransactionUuid();
            try {
                paymentReconcileService.reconcile(tx);
                log.info("결제 reconcile 성공: transactionUuid={}", transactionUuid);
            } catch (BankException e) {
                // 은행 조회 실패(5xx/타임아웃 등) → 다음 주기 재시도. 은행 status·code를 남긴다.
                log.warn(
                        "결제 reconcile - 은행 조회 실패(다음 주기 재시도): transactionUuid={}, bankStatus={}, bankCode={}",
                        transactionUuid,
                        e.getError().status(),
                        e.getError().code());
            } catch (Exception e) {
                log.warn(
                        "결제 reconcile 실패 (다음 실행에 재시도): transactionUuid={}, reason={}",
                        transactionUuid,
                        e.getMessage());
            }
        }

        // 포기 대상(시도 횟수 한도 소진) alert 후 EXPIRED 터미널로 닫는다(다음 주기 sweep·재알림에서 제외).
        for (Transaction abandoned :
                transactionRepository.findAbandonedTargets(
                        TransactionType.PAYMENT, MAX_RECONCILE_ATTEMPTS)) {
            log.error(
                    "[ALERT] 결제 자동 reconcile 포기 - 수기 확인 필요. transactionUuid={}, attempts={}",
                    abandoned.getTransactionUuid(),
                    abandoned.getReconcileAttemptCount());
            paymentStateWriter.markExpired(abandoned.getTransactionUuid());
        }
    }

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "reconcileCancels", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void reconcileCancels() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PROCESSING_STALE_MINUTES);

        List<Transaction> targets =
                transactionRepository.findReconcileTargets(
                        TransactionType.CANCEL, MAX_RECONCILE_ATTEMPTS, threshold);
        log.info("취소 reconcile 스케줄러 실행: 대상 건수={}", targets.size());

        for (Transaction cancelTx : targets) {
            String cancelUuid = cancelTx.getTransactionUuid();
            try {
                cancelReconcileService.reconcile(cancelTx);
                log.info("취소 reconcile 성공: cancelUuid={}", cancelUuid);
            } catch (BankException e) {
                // 은행 조회 실패(5xx/타임아웃 등) → 다음 주기 재시도. 은행 status·code를 남긴다.
                log.warn(
                        "취소 reconcile - 은행 조회 실패(다음 주기 재시도): cancelUuid={}, bankStatus={}, bankCode={}",
                        cancelUuid,
                        e.getError().status(),
                        e.getError().code());
            } catch (Exception e) {
                log.warn(
                        "취소 reconcile 실패 (다음 실행에 재시도): cancelUuid={}, reason={}",
                        cancelUuid,
                        e.getMessage());
            }
        }

        for (Transaction abandoned :
                transactionRepository.findAbandonedTargets(
                        TransactionType.CANCEL, MAX_RECONCILE_ATTEMPTS)) {
            log.error(
                    "[ALERT] 취소 자동 reconcile 포기 - 수기 확인 필요. cancelUuid={}, attempts={}",
                    abandoned.getTransactionUuid(),
                    abandoned.getReconcileAttemptCount());
            cancelStateWriter.markExpired(abandoned.getTransactionUuid());
        }
    }

    /** PROCESSING/UNKNOWN 환전을 bank 조회로 확정 */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "reconcileExchanges", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void reconcileExchanges() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(PROCESSING_STALE_MINUTES);

        List<Transaction> targets =
                transactionRepository.findReconcileTargets(
                        TransactionType.EXCHANGE, MAX_RECONCILE_ATTEMPTS, threshold);
        if (targets.isEmpty()) {
            return;
        }
        log.info("환전 reconcile 배치 시작. count={}", targets.size());
        for (Transaction tx : targets) {
            try {
                exchangeReconcileService.reconcile(tx);
            } catch (BusinessException ex) {
                // 비즈니스 예외(검증 실패 등)는 재시도해도 동일 결과 → 재시도 예산을 올리지 않는다.
                log.warn(
                        "reconcile 비즈니스 예외(재시도 제외). transactionUuid={}, code={}",
                        tx.getTransactionUuid(),
                        ex.getCode().getCode());
            } catch (BankException ex) {
                // 은행 조회 실패(5xx/타임아웃 등) → 일시 오류로 보고 재시도 예산 1 증가. 은행 status·code를 남긴다.
                log.warn(
                        "reconcile 은행 조회 실패(일시, 재시도): transactionUuid={}, bankStatus={}, bankCode={}",
                        tx.getTransactionUuid(),
                        ex.getError().status(),
                        ex.getError().code());
                exchangeStateWriter.incrementRetry(tx.getTransactionUuid());
            } catch (RuntimeException ex) {
                // 그 외 일시 오류 → 재시도 예산을 1 올려 결국 포기 대상으로 수렴시킨다.
                log.error("reconcile 처리 실패(일시). transactionUuid={}", tx.getTransactionUuid(), ex);
                exchangeStateWriter.incrementRetry(tx.getTransactionUuid());
            }
        }
        log.info("환전 reconcile 배치 완료. count={}", targets.size());

        // 시도 한도를 소진한 PROCESSING/UNKNOWN을 alert 후 EXPIRED 터미널로 닫는다.
        for (Transaction abandoned :
                transactionRepository.findAbandonedTargets(
                        TransactionType.EXCHANGE, MAX_RECONCILE_ATTEMPTS)) {
            log.error(
                    "[ALERT] 환전 자동 reconcile 포기 - 수기 확인 필요. transactionUuid={}, attempts={}",
                    abandoned.getTransactionUuid(),
                    abandoned.getReconcileAttemptCount());
            exchangeStateWriter.markExpired(abandoned.getTransactionUuid());
        }
    }
}
