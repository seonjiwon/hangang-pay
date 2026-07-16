package family.fisa.hangangpay.global.config;

import family.fisa.hangangpay.global.security.SessionAuthenticationFilter;
import family.fisa.hangangpay.global.security.TimedPasswordEncoder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @SuppressWarnings("squid:S4502") // SonarQube CSRF 경고 무시
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            SessionAuthenticationFilter sessionAuthenticationFilter)
            throws Exception {
        http.cors(c -> c.configurationSource(corsConfigurationSource));

        // CSRF 예외 경로, 새 도메인 POST 개발 시 경로 추가 필요
        http.csrf(
                csrf ->
                        csrf.ignoringRequestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/accounts/**",
                                "/api/v1/charge/**",
                                "/api/v1/exchange/**",
                                "/api/v1/payment/**",
                                "/api/v1/merchant/**"));

        // 경로별 접근 권한 설정, 새 도메인 개발 시 해당 경로 추가 필요
        http.authorizeHttpRequests(
                auth ->
                        auth
                                // Swagger UI 및 API 문서
                                .requestMatchers(
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**",
                                        "/api-docs/**")
                                .permitAll()
                                // Actuator health check and Alloy ㅊ scrape endpoint
                                .requestMatchers("/actuator/health", "/actuator/prometheus")
                                .permitAll()
                                // 인증 도메인
                                .requestMatchers("/api/v1/auth/**")
                                .permitAll()
                                // 사용자 도메인
                                .requestMatchers("/api/v1/users/**", "/api/v1/payment/**")
                                .hasRole("USER")
                                // QR 가맹점 정보 조회(PAY-001): 소비자가 결제 진입 시 호출.
                                // /merchant/{merchantId} 숫자 ID만 허용해 다른 가맹점 전용 경로는 열지 않는다.
                                .requestMatchers(
                                        RegexRequestMatcher.regexMatcher(
                                                HttpMethod.GET, "/api/v1/merchant/\\d+"))
                                .hasRole("USER")
                                // 가맹점 도메인
                                .requestMatchers("/api/v1/merchant/**")
                                .hasRole("MERCHANT")
                                // 계좌 도메인, 임시 인증 비활성화 상태
                                .requestMatchers("/api/v1/accounts/**")
                                .permitAll()
                                // 충전 도메인, 임시 인증 비활성화 상태
                                .requestMatchers("/api/v1/charge/**")
                                .permitAll()
                                // 지갑 잔액 조회: USER, MERCHANT 모두 접근 가능
                                .requestMatchers(HttpMethod.GET, "/api/v1/wallet/balance")
                                .hasAnyRole("USER", "MERCHANT")
                                .anyRequest()
                                .authenticated());

        http.exceptionHandling(
                ex ->
                        ex.authenticationEntryPoint(
                                (request, response, authException) ->
                                        response.sendError(
                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                "Unauthorized")));

        http.addFilterBefore(
                sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(MeterRegistry meterRegistry) {
        return new TimedPasswordEncoder(new BCryptPasswordEncoder(), meterRegistry);
    }
}
