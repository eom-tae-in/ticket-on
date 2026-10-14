package com.ticketon.api.security;

import com.ticketon.domain.account.AccountRef;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * 로그인 세션 열고 닫기 — 계정 확인을 마친 주체를 지금 세션에 로그인시키고, 로그아웃하면 지금 세션을 끝낸다.
 * <p>
 * 각 계정의 로그인 컨트롤러가 로그인 유스케이스로 이메일·비밀번호 등을 확인한 뒤, 돌려받은 주체로 {@link #logIn}을 부른다.
 * 세션은 Spring Session JDBC가 DB에 저장하고, 주체 이름 "종류:id"로 묶인다({@link AccountAuthentication}).
 * <ul>
 *   <li>세션 고정 공격을 막으려고 로그인 전에 세션 id를 바꾼다.</li>
 *   <li>로그인 전에 받아 둔 CSRF 토큰을 버린다. 다음 요청에서 새 토큰을 받는다.</li>
 *   <li>한 세션에는 주체 하나만 로그인한다. 같은 세션에서 다른 계정으로 로그인하면 이전 주체를 대신한다.</li>
 * </ul>
 * {@link #logOut}은 <b>지금 세션 하나만</b> 끝낸다. 같은 주체가 다른 기기에서 로그인한 세션은 그대로 둔다. 관리자 정지·탈퇴처럼
 * 그 주체의 세션을 모두 끊어야 할 때는 application의 SessionTerminationPort를 쓴다.
 */
public class SessionLogin {

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    private final SecurityContextRepository securityContextRepository;

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    private final LogoutHandler logoutHandler;

    public SessionLogin(SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
        this.logoutHandler = new CompositeLogoutHandler(
                new CsrfLogoutHandler(csrfTokenRepository),
                securityContextLogoutHandler(securityContextRepository));
    }

    public void logIn(AccountRef account, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = AccountAuthentication.of(account);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /**
     * 지금 세션을 끝낸다 — CSRF 토큰을 버리고, 세션을 없애고(DB의 세션 줄이 지워지고 세션 쿠키가 만료된다), 로그인 정보를 비운다.
     * 로그인하지 않았거나 세션이 없어도 오류 없이 끝난다.
     */
    public void logOut(HttpServletRequest request, HttpServletResponse response) {
        logoutHandler.logout(request, response, securityContextHolderStrategy.getContext().getAuthentication());
    }

    private static SecurityContextLogoutHandler securityContextLogoutHandler(
            SecurityContextRepository securityContextRepository) {
        SecurityContextLogoutHandler handler = new SecurityContextLogoutHandler();
        handler.setSecurityContextRepository(securityContextRepository);
        return handler;
    }
}
