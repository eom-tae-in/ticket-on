package com.ticketon.api.security;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 로그인 주체({@link AccountRef}) ↔ Spring Security 인증 정보.
 * <p>
 * 인증 정보의 이름은 주체 이름 "종류:id"다. Spring Session JDBC가 이 이름을 spring_session.principal_name에 적으므로
 * 같은 이메일의 관객 계정과 운영진 로그인이 섞이지 않고, 세션 끊기(SessionTerminationPort)가 이 이름으로 그 주체의 세션을 찾는다.
 * 권한은 계정 종류 하나({@code ROLE_<종류>})이고, 영역별 경로 규칙이 이것을 본다(package-info).
 * <p>
 * 세션에는 이 인증 정보가 직렬화되어 저장된다. 그래서 주체를 문자열 이름으로 담고 도메인 객체를 넣지 않는다 — 도메인 클래스를
 * 바꿔도 이미 로그인된 세션을 읽을 수 있게.
 */
public final class AccountAuthentication {

    private static final String ROLE_PREFIX = "ROLE_";

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private AccountAuthentication() {
    }

    /** 로그인한 주체의 인증 정보. 비밀번호 같은 자격 증명은 담지 않는다. */
    public static Authentication of(AccountRef account) {
        return UsernamePasswordAuthenticationToken.authenticated(account.principalName(), null,
                List.of(new SimpleGrantedAuthority(roleOf(account.kind()))));
    }

    /** 인증 정보에서 로그인 주체를 꺼낸다. 로그인하지 않았으면(없음·익명) 비어 있다. */
    public static Optional<AccountRef> accountOf(@Nullable Authentication authentication) {
        if (!TRUST_RESOLVER.isAuthenticated(authentication)) {
            return Optional.empty();
        }
        return Optional.of(AccountRef.fromPrincipalName(authentication.getName()));
    }

    private static String roleOf(AccountKind kind) {
        return ROLE_PREFIX + kind.name();
    }
}
