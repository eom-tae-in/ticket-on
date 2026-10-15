package com.ticketon.api.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc 요청을 로그인한 주체로 보낸다. 실제 로그인({@link SessionLogin})과 같은 인증 정보를 쓴다.
 * <pre>{@code
 * mockMvc.perform(get("/api/audience/...").with(loggedInAs(AccountKind.AUDIENCE, 7)))
 * mockMvc.perform(post("/api/admin/super/...").with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
 * }</pre>
 * {@code loggedInAs(AccountKind.ADMIN, id)}는 등급 없이 로그인한 관리자 — 관리자(ADMIN 등급)와 같다.
 */
public final class TestLogin {

    private TestLogin() {
    }

    public static RequestPostProcessor loggedInAs(AccountKind kind, long id) {
        return loggedInAs(AccountRef.of(kind, id));
    }

    public static RequestPostProcessor loggedInAs(AccountRef account) {
        return authentication(AccountAuthentication.of(account));
    }

    /** 관리자 로그인({@link SessionLogin#logInAdmin})과 같이 등급을 담은 관리자. */
    public static RequestPostProcessor loggedInAsAdmin(long id, AdminGrade grade) {
        return authentication(AccountAuthentication.ofAdmin(AccountRef.of(AccountKind.ADMIN, id), grade));
    }
}
