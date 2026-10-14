package com.ticketon.api.security;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시험 전용 컨트롤러 — 메서드 보안처럼 컨트롤러 안에서 Spring Security 예외를 던진다.
 * <p>
 * {@link TestComponent}라 컴포넌트 스캔에 잡히지 않는다. 쓰는 테스트가 {@code @Import}로 넣는다.
 */
@TestComponent
@RestController
class SecurityExceptionTestController {

    @GetMapping({"/api/public/test/access-denied", "/api/audience/test/access-denied"})
    void accessDenied() {
        throw new AccessDeniedException("method security denied");
    }

    @GetMapping("/api/public/test/authentication-required")
    void authenticationRequired() {
        throw new InsufficientAuthenticationException("full authentication required");
    }
}
