package com.ticketon.api.security;

import com.ticketon.domain.account.AccountKind;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터 — 영역별 경로 접두사를 계정 종류에 묶는다. 경로 규칙과 이유는 package-info에 있다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    /** 로그인 없이 여는 경로 — 공개 API, 로그인·가입, API 명세(화면 타입 생성), 서버 상태 확인. */
    private static final String[] PUBLIC_PATHS = {
            "/api/public/**",
            "/api/auth/**",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            "/actuator/health", "/actuator/health/**"};

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository, SecurityErrorResponseHandler securityErrorResponseHandler) {
        return http
                .authorizeHttpRequests(requests -> requests
                        // 필터 단계 오류가 오류 응답(/error)으로 넘어갈 때는 막지 않는다
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/audience/**").hasRole(AccountKind.AUDIENCE.name())
                        .requestMatchers("/api/organizer/**").hasRole(AccountKind.STAFF.name())
                        // 최고 관리자 전용 경로를 관리자 영역보다 먼저 건다 — 먼저 맞는 규칙 하나만 본다
                        .requestMatchers("/api/admin/super/**").hasRole(AccountAuthentication.SUPER_ADMIN_ROLE)
                        .requestMatchers("/api/admin/**").hasRole(AccountKind.ADMIN.name())
                        .requestMatchers("/api/checkin/**").hasAnyRole(AccountKind.CHECKIN.name(), AccountKind.STAFF.name())
                        .anyRequest().denyAll())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler))
                // 로그인 화면으로 보냈다가 돌아오는 방식이 아니다. 막힌 요청을 세션에 저장하지 않는다(익명 세션이 쌓이지 않게)
                .requestCache(RequestCacheConfigurer::disable)
                // 로그아웃은 계정마다 로그인 컨트롤러가 SessionLogin.logOut으로 지금 세션만 끊는다(예: /api/auth/audience/logout).
                // 기본 /logout(로그인 화면으로 이동)은 쓰지 않는다
                .logout(LogoutConfigurer::disable)
                .build();
    }

    /** 로그인 정보는 세션에 둔다. 필터와 {@link SessionLogin}이 같은 저장소를 쓴다. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /** CSRF 토큰은 Spring Security 기본대로 세션에 둔다. 로그인하면 {@link SessionLogin}이 토큰을 새로 만든다. */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SecurityErrorResponseHandler securityErrorResponseHandler(JsonMapper jsonMapper) {
        return new SecurityErrorResponseHandler(jsonMapper);
    }

    @Bean
    public SessionLogin sessionLogin(SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) {
        return new SessionLogin(securityContextRepository, csrfTokenRepository);
    }
}
