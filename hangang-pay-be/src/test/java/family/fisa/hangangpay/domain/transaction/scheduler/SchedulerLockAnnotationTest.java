package family.fisa.hangangpay.domain.transaction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class SchedulerLockAnnotationTest {

    @Test
    void 모든_스케줄러_메서드에_SchedulerLock이_정확히_설정된다() throws NoSuchMethodException {
        assertLock(ReconcileScheduler.class, "reconcilePayments", "reconcilePayments");
        assertLock(ReconcileScheduler.class, "reconcileCancels", "reconcileCancels");
        assertLock(ReconcileScheduler.class, "reconcileExchanges", "reconcileExchanges");
        assertLock(
                IntentExpiryScheduler.class, "expirePaymentIntents", "expireStalePaymentIntents");
        assertLock(IntentExpiryScheduler.class, "expireChargeIntents", "expireStaleChargeIntents");
        assertLock(
                IntentExpiryScheduler.class, "expireExchangeIntents", "expireStaleExchangeIntents");
    }

    private void assertLock(Class<?> type, String method, String expectedName)
            throws NoSuchMethodException {
        Method m = type.getDeclaredMethod(method);
        SchedulerLock lock = m.getAnnotation(SchedulerLock.class);
        assertThat(lock).as("@SchedulerLock on %s.%s", type.getSimpleName(), method).isNotNull();
        assertThat(lock.name()).isEqualTo(expectedName);
        assertThat(lock.lockAtMostFor()).isEqualTo("5m");
        assertThat(lock.lockAtLeastFor()).isEqualTo("5s");
    }
}
