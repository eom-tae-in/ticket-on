package com.ticketon.api.security;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.domain.account.AccountKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 보안 필터를 건 채로 영역별 접근 규칙을 본다. 규칙 표는 package-info에 있다. DB 없이 돈다.
 */
@WebMvcTest(controllers = AreaTestController.class)
@Import({SecurityConfiguration.class, AreaTestController.class})
@DisplayName("영역별 접근 — 경로 접두사를 계정 종류에 묶는다")
class AreaAccessTest {

    private static final String UNAUTHENTICATED_MESSAGE = "로그인이 필요합니다.";

    private static final String ACCESS_DENIED_MESSAGE = "이 요청을 처리할 권한이 없습니다.";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/public/test", "/api/auth/test"})
    @DisplayName("공개 경로와 로그인·가입 경로는 로그인 없이 200이다 (docs/13 \"공개 영역 — 로그인 불필요\")")
    void publicPathsAreOpenWithoutLogin(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(AreaTestController.ANONYMOUS));
    }

    @Test
    @DisplayName("공개 경로에서도 로그인했으면 주체를 받는다")
    void publicPathReceivesAccountWhenLoggedIn() throws Exception {
        mockMvc.perform(get("/api/public/test").with(loggedInAs(AccountKind.AUDIENCE, 7)))
                .andExpect(status().isOk())
                .andExpect(content().string("AUDIENCE:7"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/audience/test", "/api/organizer/test", "/api/admin/test", "/api/checkin/test"})
    @DisplayName("영역 경로에 로그인 없이 오면 401 UNAUTHENTICATED다")
    void areaPathsRequireLogin(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", UNAUTHENTICATED_MESSAGE));
    }

    @ParameterizedTest(name = "{1} 계정 → {0}")
    @CsvSource({
            "/api/audience/test,  AUDIENCE",
            "/api/organizer/test, STAFF",
            "/api/admin/test,     ADMIN",
            "/api/checkin/test,   CHECKIN",
            "/api/checkin/test,   STAFF"})
    @DisplayName("자기 영역에는 들어가고, 컨트롤러는 로그인한 주체를 받는다 — 운영진도 검표 화면을 연다 (docs/13)")
    void accountEntersItsOwnArea(String path, AccountKind kind) throws Exception {
        mockMvc.perform(get(path).with(loggedInAs(kind, 7)))
                .andExpect(status().isOk())
                .andExpect(content().string(kind.name() + ":7"));
    }

    @ParameterizedTest(name = "{1} 계정 → {0}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            /api/audience/test  | STAFF    | 주최자 쪽 로그인으로는 예매할 수 없다 (docs/01)
            /api/audience/test  | ADMIN    | 관리자는 관객 영역에 들어가지 않는다
            /api/audience/test  | CHECKIN  | 검표 계정은 검표 화면만 본다 (docs/01)
            /api/organizer/test | AUDIENCE | 관객 계정으로는 주최 관리를 열 수 없다 (docs/01 "계정 4종")
            /api/organizer/test | ADMIN    | 관리자는 주최 관리 영역에 들어가지 않는다
            /api/organizer/test | CHECKIN  | 검표 계정에는 매출·환불·행사 설정이 열리지 않는다 (docs/01)
            /api/admin/test     | AUDIENCE | 관리자 영역은 관리자만
            /api/admin/test     | STAFF    | 관리자 영역은 관리자만
            /api/admin/test     | CHECKIN  | 관리자 영역은 관리자만
            /api/checkin/test   | AUDIENCE | 검표 화면은 검표 계정과 운영진만
            /api/checkin/test   | ADMIN    | 검표 화면은 검표 계정과 운영진만
            """)
    @DisplayName("다른 계정 종류의 영역에 오면 403 ACCESS_DENIED다")
    void otherAccountKindIsDenied(String path, AccountKind kind, String reason) throws Exception {
        mockMvc.perform(get(path).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", ACCESS_DENIED_MESSAGE));
    }

    @Test
    @DisplayName("영역 접두사 밖의 경로는 막힌다 — 로그인 전 401")
    void unclassifiedPathIsUnauthorizedBeforeLogin() throws Exception {
        mockMvc.perform(get("/api/unclassified/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", UNAUTHENTICATED_MESSAGE));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AccountKind.class)
    @DisplayName("영역 접두사 밖의 경로는 어느 계정으로도 403이다")
    void unclassifiedPathIsDeniedForEveryAccount(AccountKind kind) throws Exception {
        mockMvc.perform(get("/api/unclassified/test").with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", ACCESS_DENIED_MESSAGE));
    }

    @Test
    @DisplayName("서버 상태 확인 경로는 로그인 없이 보안 필터를 지난다 (이 슬라이스에는 actuator가 없어 404)")
    void healthPathPassesSecurityWithoutLogin() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공개 경로라도 로그인 주체가 꼭 필요한 컨트롤러는 로그인 없이 401이다")
    void requiredAccountOnPublicPathIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/public/test/login-required"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", UNAUTHENTICATED_MESSAGE));
    }

    @Test
    @DisplayName("@CurrentAccount를 지원하지 않는 타입에 붙이면 조용히 넘기지 않고 500이다")
    void currentAccountOnUnsupportedTypeFails() throws Exception {
        mockMvc.perform(get("/api/public/test/wrong-type").with(loggedInAs(AccountKind.AUDIENCE, 7)))
                .andExpect(status().isInternalServerError())
                .andExpect(errorBody("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    @DisplayName("CSRF 보호가 켜져 있다 — 토큰 없는 상태 변경 요청은 로그인했어도 403이다")
    void stateChangingRequestWithoutCsrfTokenIsDenied() throws Exception {
        mockMvc.perform(post("/api/audience/test").with(loggedInAs(AccountKind.AUDIENCE, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", ACCESS_DENIED_MESSAGE));
    }

    @Test
    @DisplayName("CSRF 토큰을 실은 상태 변경 요청은 통과한다")
    void stateChangingRequestWithCsrfTokenPasses() throws Exception {
        mockMvc.perform(post("/api/audience/test").with(loggedInAs(AccountKind.AUDIENCE, 7)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("AUDIENCE:7"));
    }

    @Test
    @DisplayName("로그인 없이 막힌 요청은 세션을 만들지 않는다 — 세션 표에 빈 세션이 쌓이지 않게")
    void rejectedAnonymousRequestDoesNotCreateSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/audience/test"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("로그인 없이 공개 경로를 보는 것도 세션을 만들지 않는다")
    void anonymousPublicRequestDoesNotCreateSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
