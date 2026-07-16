package family.fisa.hangangpay.global.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 위임 PasswordEncoder(BCrypt)의 {@code matches} 연산 시간을 계측하는 데코레이터.
 *
 * <p>{@code matches}(비밀번호·PIN 검증)는 BCrypt 해싱이라 CPU를 크게 쓴다. 이 시간을 Micrometer Timer로 노출해
 * (Prometheus: {@code security_password_matches_seconds_*}) 커넥션 점유 시간 대비 BCrypt 비중을 관측할 수 있게 한다.
 * 결과·동작은 위임 인코더와 동일하다.
 */
public class TimedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Timer matchTimer;

    public TimedPasswordEncoder(PasswordEncoder delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.matchTimer =
                Timer.builder("security.password.matches")
                        .description("PasswordEncoder.matches (BCrypt) computation time")
                        .publishPercentileHistogram()
                        .minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofSeconds(1))
                        .register(meterRegistry);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return matchTimer.record(() -> delegate.matches(rawPassword, encodedPassword));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
