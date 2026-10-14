package com.ticketon.api.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc 요청을 로그인한 주체로 보낸다. 실제 로그인({@link SessionLogin})과 같은 인증 정보를 쓴다.
 * <pre>{@code
 * mockMvc.perform(get("/api/audience/...").with(loggedInAs(AccountKind.AUDIENCE, 7)))
 * }</pre>
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
}
