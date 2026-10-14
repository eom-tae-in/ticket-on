package com.ticketon.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.session.autoconfigure.SessionTimeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 세션 쿠키·만료 설정이 application.yml(과 운영 프로필)에서 읽히는 값을 본다. DB 없이 돈다.
 * <p>
 * Spring Session은 이 값으로 세션 쿠키를 만든다(SessionAutoConfiguration). 실제 Set-Cookie 머리글은 통합 테스트가 본다.
 */
@DisplayName("로그인 세션 설정 — 쿠키 속성과 비활성 만료")
class LoginSessionSettingsTest {

    @Nested
    @SpringBootTest(classes = SettingsOnly.class, webEnvironment = WebEnvironment.NONE)
    @DisplayName("기본 프로필")
    class DefaultProfile {

        @Autowired
        private ServerProperties serverProperties;

        @Autowired
        private SessionTimeout sessionTimeout;

        @Test
        @DisplayName("세션 쿠키는 HttpOnly, SameSite=Lax다 (개발 중에는 http로도 실리게 Secure를 정하지 않는다)")
        void sessionCookieIsHttpOnlyAndLax() {
            Cookie cookie = serverProperties.getServlet().getSession().getCookie();

            assertThat(cookie.getHttpOnly()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo(SameSite.LAX);
            assertThat(cookie.getSecure()).isNull();
        }

        @Test
        @DisplayName("비활성 만료는 정책 숫자 모음의 제안값(30분)이다 — 확인 필요 #8")
        void idleTimeoutComesFromPolicy() {
            assertThat(sessionTimeout.getTimeout()).isEqualTo(ProposedPolicy.LOGIN_SESSION_IDLE_TIMEOUT);
            assertThat(ProposedPolicy.LOGIN_SESSION_IDLE_TIMEOUT).isEqualTo(Duration.ofMinutes(30));
        }
    }

    @Nested
    @SpringBootTest(classes = SettingsOnly.class, webEnvironment = WebEnvironment.NONE)
    @ActiveProfiles("prod")
    @DisplayName("운영 프로필(prod)")
    class ProdProfile {

        @Autowired
        private ServerProperties serverProperties;

        @Test
        @DisplayName("세션 쿠키에 Secure가 붙고, HttpOnly·SameSite=Lax는 그대로다")
        void sessionCookieIsSecure() {
            Cookie cookie = serverProperties.getServlet().getSession().getCookie();

            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.getHttpOnly()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo(SameSite.LAX);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServerProperties.class)
    @Import(LoginSessionConfiguration.class)
    static class SettingsOnly {
    }
}
