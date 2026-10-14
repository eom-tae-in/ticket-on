package com.ticketon.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.domain.account.AccountKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로그인 세션 열고 닫기({@link SessionLogin}) — 세션은 MockMvc의 세션으로 흉내 낸다. DB에 저장되는 모습은 통합 테스트가 본다.
 */
@WebMvcTest(controllers = {LoginTestController.class, AreaTestController.class})
@Import({SecurityConfiguration.class, LoginTestController.class, AreaTestController.class})
@DisplayName("로그인 세션 열고 닫기")
class SessionLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CsrfTokenRepository csrfTokenRepository;

    @Test
    @DisplayName("로그인하면 그 세션으로 자기 영역에 들어가고, 컨트롤러는 주체를 받는다")
    void loggedInSessionEntersOwnArea() throws Exception {
        MockHttpSession session = new MockHttpSession();

        logIn(session, AccountKind.AUDIENCE, 7);

        mockMvc.perform(get("/api/audience/test").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("AUDIENCE:7"));
        mockMvc.perform(get("/api/organizer/test").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인하면 세션 id가 바뀐다 — 세션 고정 공격 방지")
    void logInChangesSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String sessionIdBeforeLogin = session.getId();

        logIn(session, AccountKind.AUDIENCE, 7);

        assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);
    }

    @Test
    @DisplayName("로그인하면 로그인 전에 받은 CSRF 토큰을 버린다")
    void logInDiscardsCsrfTokenIssuedBeforeLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/api/auth/test/csrf").session(session)).andExpect(status().isOk());
        CsrfToken tokenBeforeLogin = csrfTokenRepository.loadToken(requestWith(session));

        logIn(session, AccountKind.AUDIENCE, 7);

        assertThat(tokenBeforeLogin).isNotNull();
        CsrfToken tokenAfterLogin = csrfTokenRepository.loadToken(requestWith(session));
        assertThat(tokenAfterLogin == null ? null : tokenAfterLogin.getToken()).isNotEqualTo(tokenBeforeLogin.getToken());
    }

    @Test
    @DisplayName("같은 세션에서 다른 계정으로 로그인하면 이전 주체를 대신한다 — 한 세션에 주체 하나")
    void logInWithOtherAccountReplacesPreviousAccount() throws Exception {
        MockHttpSession session = new MockHttpSession();
        logIn(session, AccountKind.AUDIENCE, 7);

        logIn(session, AccountKind.STAFF, 7);

        mockMvc.perform(get("/api/organizer/test").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("STAFF:7"));
        mockMvc.perform(get("/api/audience/test").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 요청에도 CSRF 토큰이 필요하다 — 남의 계정으로 몰래 로그인시키는 요청을 막는다")
    void logInRequiresCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/test/login").param("kind", "AUDIENCE").param("id", "7").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audience/test").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃하면 그 세션이 끝나 다시 로그인해야 한다")
    void logOutEndsSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        logIn(session, AccountKind.AUDIENCE, 7);

        mockMvc.perform(post("/api/auth/test/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/audience/test").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃은 지금 세션만 끝낸다 — 같은 주체가 다른 기기에서 로그인한 세션은 남는다")
    void logOutEndsOnlyCurrentSession() throws Exception {
        MockHttpSession phone = new MockHttpSession();
        MockHttpSession laptop = new MockHttpSession();
        logIn(phone, AccountKind.AUDIENCE, 7);
        logIn(laptop, AccountKind.AUDIENCE, 7);

        mockMvc.perform(post("/api/auth/test/logout").session(phone).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/audience/test").session(laptop))
                .andExpect(status().isOk())
                .andExpect(content().string("AUDIENCE:7"));
    }

    @Test
    @DisplayName("로그아웃 요청에도 CSRF 토큰이 필요하다 — 남이 몰래 로그아웃시키는 요청을 막는다")
    void logOutRequiresCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        logIn(session, AccountKind.AUDIENCE, 7);

        mockMvc.perform(post("/api/auth/test/logout").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audience/test").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않았어도 로그아웃은 오류 없이 끝나고 세션을 만들지 않는다")
    void logOutWithoutLoginSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/test/logout").with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private void logIn(MockHttpSession session, AccountKind kind, long id) throws Exception {
        mockMvc.perform(post("/api/auth/test/login")
                        .param("kind", kind.name())
                        .param("id", String.valueOf(id))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private static MockHttpServletRequest requestWith(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }
}
