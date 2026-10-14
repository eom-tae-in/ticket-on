package com.ticketon.api.security;

import com.ticketon.domain.policy.ProposedPolicy;
import org.springframework.boot.session.autoconfigure.SessionTimeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 로그인 세션(Spring Session JDBC)의 비활성 만료 시간.
 * <p>
 * 값은 정책 숫자 모음에서 가져온다. 설정 파일에 숫자를 따로 적지 않는다(.claude/rules/planning-docs.md "한곳에 모은다").
 * 이 빈이 있으면 Spring Boot는 spring.session.timeout·server.servlet.session.timeout 대신 이 값을 세션 저장소에 넣는다.
 */
@Configuration(proxyBeanMethods = false)
public class LoginSessionConfiguration {

    @Bean
    public SessionTimeout loginSessionTimeout() {
        return () -> ProposedPolicy.LOGIN_SESSION_IDLE_TIMEOUT;
    }
}
