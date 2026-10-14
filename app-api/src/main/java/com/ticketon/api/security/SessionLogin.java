package com.ticketon.api.security;

import com.ticketon.domain.account.AccountRef;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * 로그인 세션 열기 — 계정 확인을 마친 주체를 지금 세션에 로그인시킨다.
 * <p>
 * 각 계정의 로그인 컨트롤러가 로그인 유스케이스로 이메일·비밀번호 등을 확인한 뒤, 돌려받은 주체로 이것을 부른다.
 * 세션은 Spring Session JDBC가 DB에 저장하고, 주체 이름 "종류:id"로 묶인다({@link AccountAuthentication}).
 * <ul>
 *   <li>세션 고정 공격을 막으려고 로그인 전에 세션 id를 바꾼다.</li>
 *   <li>로그인 전에 받아 둔 CSRF 토큰을 버린다. 다음 요청에서 새 토큰을 받는다.</li>
 *   <li>한 세션에는 주체 하나만 로그인한다. 같은 세션에서 다른 계정으로 로그인하면 이전 주체를 대신한다.</li>
 * </ul>
 */
public class SessionLogin {

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    private final SecurityContextRepository securityContextRepository;

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public SessionLogin(SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    public void logIn(AccountRef account, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = AccountAuthentication.of(account);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
