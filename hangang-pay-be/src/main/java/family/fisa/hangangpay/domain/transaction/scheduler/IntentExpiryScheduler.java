package family.fisa.hangangpay.domain.transaction.scheduler;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeStateWriter;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TTL 지난 PENDING intent를 EXPIRED 처리하는 스케줄러.
 *
 * <p>결제·충전·환전 intent 만료를 함께 담당한다(1분 주기).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentExpiryScheduler {

    /** 이 시간 지난 PENDING intent는 만료 */
    private static final int INTENT_TTL_MINUTES = 10;

    private final TransactionRepository transactionRepository;
    private final ChargeStateWriter chargeStateWriter;
    private final ExchangeStateWriter exchangeStateWriter;

    /** TTL 지난 PENDING 결제 intent를 조건부 UPDATE로 일괄 EXPIRED 처리 */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "expireStalePaymentIntents", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void expirePaymentIntents() {
        LocalDateTime now = LocalDateTime.now();
        int expired =
                transactionRepository.expireStalePendingPaymentIntents(
                        now.minusMinutes(INTENT_TTL_MINUTES), now);

        if (expired > 0) {
            log.info("결제 intent 만료 배치 완료. count={}", expired);
        } else {
            log.info("결제 intent 만료가 없습니다.");
        }
    }

    /** TTL 지난 PENDING 충전 intent를 EXPIRED 처리 */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "expireStaleChargeIntents", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void expireChargeIntents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(INTENT_TTL_MINUTES);
        List<Transaction> targets = transactionRepository.findStalePendingChargeIntents(threshold);
        if (targets.isEmpty()) {
            return;
        }
        log.info("충전 intent 만료 배치 시작. count={}", targets.size());
        for (Transaction tx : targets) {
            try {
                chargeStateWriter.markExpired(tx.getTransactionUuid());
            } catch (RuntimeException ex) {
                log.error("충전 intent 만료 처리 실패. transactionUuid={}", tx.getTransactionUuid(), ex);
            }
        }
        log.info("충전 intent 만료 배치 완료. count={}", targets.size());
    }

    /** TTL 지난 PENDING 환전 intent를 EXPIRED 처리 */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "expireStaleExchangeIntents", lockAtMostFor = "5m", lockAtLeastFor = "5s")
    public void expireExchangeIntents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(INTENT_TTL_MINUTES);
        List<Transaction> targets =
                transactionRepository.findStalePendingExchangeIntents(threshold);
        if (targets.isEmpty()) {
            return;
        }
        log.info("환전 intent 만료 배치 시작. count={}", targets.size());
        for (Transaction tx : targets) {
            try {
                exchangeStateWriter.markExpired(tx.getTransactionUuid());
            } catch (RuntimeException ex) {
                log.error("환전 intent 만료 처리 실패. transactionUuid={}", tx.getTransactionUuid(), ex);
            }
        }
        log.info("환전 intent 만료 배치 완료. count={}", targets.size());
    }
}
