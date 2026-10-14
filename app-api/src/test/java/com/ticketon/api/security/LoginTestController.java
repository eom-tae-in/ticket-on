package com.ticketon.api.security;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시험 전용 로그인 — 계정 확인 없이 받은 주체로 바로 세션을 연다. 실제 로그인 컨트롤러가 유스케이스로 계정을 확인한 뒤 하는 일과 같다.
 * 로그아웃도 실제 로그아웃처럼 지금 세션을 끝낸다.
 * <p>
 * {@link TestComponent}라 컴포넌트 스캔에 잡히지 않는다. 쓰는 테스트가 {@code @Import}로 넣는다. 다른 패키지의 통합 테스트도
 * CSRF 토큰을 받으려고 넣으므로 공개 클래스로 둔다(화면이 토큰을 받는 실제 방법은 #136에서 정한다).
 */
@TestComponent
@RestController
public class LoginTestController {

    private final SessionLogin sessionLogin;

    LoginTestController(SessionLogin sessionLogin) {
        this.sessionLogin = sessionLogin;
    }

    @PostMapping("/api/auth/test/login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logIn(@RequestParam AccountKind kind, @RequestParam long id, HttpServletRequest request,
            HttpServletResponse response) {
        sessionLogin.logIn(AccountRef.of(kind, id), request, response);
    }

    @PostMapping("/api/auth/test/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logOut(HttpServletRequest request, HttpServletResponse response) {
        sessionLogin.logOut(request, response);
    }

    /** 지금 세션의 CSRF 토큰을 꺼낸다(없으면 만들어 세션에 둔다). */
    @GetMapping("/api/auth/test/csrf")
    String csrfToken(CsrfToken token) {
        return token.getToken();
    }
}
