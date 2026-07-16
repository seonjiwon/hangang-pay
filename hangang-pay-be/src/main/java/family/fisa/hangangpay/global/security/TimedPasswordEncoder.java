package family.fisa.hangangpay.global.security;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 위임 PasswordEncoder(BCrypt)의 {@code matches}를 (1) 시간 계측 + (2) 동시 실행 수 제한 하는 데코레이터.
 *
 * <p>{@code matches}(비밀번호·PIN 검증)는 BCrypt 해싱이라 순수 CPU 작업이다. 동시 실행이 코어 수를 크게 넘으면
 * 오버서브(time-slice)로 전체가 느려지고, 결국 커넥션·스레드까지 굶겨 붕괴로 번진다. 그래서 동시 {@code matches}를
 * CPU 코어 기준(≈ 2 x 코어)으로 제한해 CPU 오버서브를 원천 차단한다 — CPU 작업은 CPU 자원 크기로 격리.
 *
 * <p>계측: {@code security_password_matches_seconds_*}(BCrypt 시간), {@code security_password_bcrypt_available}(여유 permit).
 */
public class TimedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Timer matchTimer;
    private final Semaphore bcryptLimiter;

    public TimedPasswordEncoder(PasswordEncoder delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.matchTimer =
                Timer.builder("security.password.matches")
                        .description("PasswordEncoder.matches (BCrypt) computation time")
                        .publishPercentileHistogram()
                        .minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofSeconds(1))
                        .register(meterRegistry);

        // BCrypt(CPU) 동시 실행 상한. 코어 수의 2배 = 실측상 load/코어<1을 유지하는 최대 효율 지점.
        int permits = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
        this.bcryptLimiter = new Semaphore(permits);
        Gauge.builder("security.password.bcrypt.available", bcryptLimiter, Semaphore::availablePermits)
                .description("BCrypt 동시 실행 여유 permit 수")
                .register(meterRegistry);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        // BCrypt(CPU)를 코어 수 기준으로만 동시 실행 → 오버서브 방지. 초과분은 permit을 기다린다(파킹, CPU 미점유).
        bcryptLimiter.acquireUninterruptibly();
        try {
            return matchTimer.record(() -> delegate.matches(rawPassword, encodedPassword));
        } finally {
            bcryptLimiter.release();
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
