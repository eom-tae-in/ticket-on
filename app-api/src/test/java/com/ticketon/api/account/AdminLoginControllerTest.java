package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.admin.AdminAccountController;
import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.LogInAdminCommand;
import com.ticketon.application.account.port.in.LogInAdminResult;
import com.ticketon.application.account.port.in.LogInAdminUseCase;
import com.ticketon.application.admin.port.in.IssueAdminAccountCommand;
import com.ticketon.application.admin.port.in.IssueAdminAccountResult;
import com.ticketon.application.admin.port.in.IssueAdminAccountUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.RuleViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 로그인·로그아웃 API — 보안 필터를 건 채로 세션이 열리고 닫히는지, 세션에 담긴 등급으로 최고 관리자 전용 경로가 갈리는지,
 * 영역 분리(관리자 세션은 관객·주최 영역 403), 입력 검증(400), 돌아갈 경로, 오류 응답을 본다. 유스케이스는 흉내 낸다. 세션은 MockMvc의
 * 세션으로 흉내 내고, DB에 저장되는 모습은 통합 테스트가 본다.
 */
@WebMvcTest(controllers = {AdminLoginController.class, AdminAccountController.class})
@Import(SecurityConfiguration.class)
@DisplayName("관리자 로그인·로그아웃 API")
class AdminLoginControllerTest {

    private static final String LOGIN_PATH = "/api/auth/admin/login";

    private static final String LOGOUT_PATH = "/api/auth/admin/logout";

    private static final String ISSUE_PATH = "/api/admin/super/admin-accounts";

    private static final String DOHYUN_EMAIL = "dohyun@ticket-on.example";

    private static final String DOHYUN_PASSWORD = "dohyun-admin-1234";

    private static final AccountRef DOHYUN = AccountRef.of(AccountKind.ADMIN, 1);

    private static final AccountRef ADMIN = AccountRef.of(AccountKind.ADMIN, 2);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private LogInAdminUseCase logInAdminUseCase;

    @MockitoBean
    private IssueAdminAccountUseCase issueAdminAccountUseCase;

    @Nested
    @DisplayName("로그인")
    class LogIn {

        @Test
        @DisplayName("최고 관리자로 로그인하면 응답에 등급 SUPER_ADMIN이 오고, 그 세션으로 최고 관리자 전용 경로(관리자 계정 발급)에 들어간다")
        void superAdminSessionEntersSuperAdminPath() throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(DOHYUN, AdminGrade.SUPER_ADMIN));
            given(issueAdminAccountUseCase.issue(any()))
                    .willReturn(new IssueAdminAccountResult(3, "new-admin@ticket-on.example", AdminGrade.ADMIN));
            MockHttpSession session = new MockHttpSession();

            mockMvc.perform(loginRequest(loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD)).session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"returnPath\":\"/admin\",\"grade\":\"SUPER_ADMIN\"}",
                            JsonCompareMode.STRICT));

            mockMvc.perform(issueRequest().session(session)).andExpect(status().isCreated());
            ArgumentCaptor<IssueAdminAccountCommand> command = ArgumentCaptor.forClass(IssueAdminAccountCommand.class);
            verify(issueAdminAccountUseCase).issue(command.capture());
            assertThat(command.getValue().issuer()).isEqualTo(DOHYUN);
        }

        @Test
        @DisplayName("관리자로 로그인하면 응답에 등급 ADMIN이 오고, 그 세션으로는 최고 관리자 전용 경로가 403이다 — 유스케이스를 부르지 않는다")
        void adminSessionIsDeniedSuperAdminPath() throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(ADMIN, AdminGrade.ADMIN));
            MockHttpSession session = new MockHttpSession();

            mockMvc.perform(loginRequest(loginBody("admin@ticket-on.example", "admin-1234")).session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"returnPath\":\"/admin\",\"grade\":\"ADMIN\"}", JsonCompareMode.STRICT));

            mockMvc.perform(issueRequest().session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
            verify(issueAdminAccountUseCase, never()).issue(any());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"/api/audience/account", "/api/organizer/home", "/api/checkin/events"})
        @DisplayName("관리자로 로그인한 세션으로는 관객·주최·검표 영역이 403이다")
        void adminSessionCannotEnterOtherAreas(String path) throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(DOHYUN, AdminGrade.SUPER_ADMIN));
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(loginRequest(loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD)).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(get(path).session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
        }

        @Test
        @DisplayName("로그인하면 세션 id가 바뀐다 — 세션 고정 공격 방지")
        void logInChangesSessionId() throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(DOHYUN, AdminGrade.SUPER_ADMIN));
            MockHttpSession session = new MockHttpSession();
            String sessionIdBeforeLogin = session.getId();

            mockMvc.perform(loginRequest(loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD)).session(session))
                    .andExpect(status().isOk());

            assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);
        }

        @Test
        @DisplayName("이메일은 앞뒤 공백을 걷고, 비밀번호는 적은 그대로 유스케이스에 넘긴다 — 최소 길이는 보지 않는다")
        void convertsRequestToCommand() throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(DOHYUN, AdminGrade.SUPER_ADMIN));

            mockMvc.perform(loginRequest(loginBody("  Dohyun@Ticket-On.example ", " abc "))).andExpect(status().isOk());

            ArgumentCaptor<LogInAdminCommand> command = ArgumentCaptor.forClass(LogInAdminCommand.class);
            verify(logInAdminUseCase).logIn(command.capture());
            assertThat(command.getValue().email()).isEqualTo(EmailAddress.of("Dohyun@Ticket-On.example"));
            assertThat(command.getValue().password().value()).isEqualTo(" abc ");
        }

        @Test
        @DisplayName("로그인이 안 되면 409 ADMIN_LOGIN_FAILED이고 세션은 로그인되지 않는다")
        void failedLoginLeavesSessionAnonymous() throws Exception {
            given(logInAdminUseCase.logIn(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.ADMIN_LOGIN_FAILED, "비밀번호 틀림"));
            MockHttpSession session = new MockHttpSession();

            mockMvc.perform(loginRequest(loginBody(DOHYUN_EMAIL, "wrong-password")).session(session))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("ADMIN_LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다."));

            mockMvc.perform(issueRequest().session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이고 유스케이스를 부르지 않는다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON)
                            .content(json(loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD))))
                    .andExpect(status().isForbidden());

            verify(logInAdminUseCase, never()).logIn(any());
        }

        @ParameterizedTest(name = "{0} = \"{1}\" → {2}")
        @CsvSource(delimiter = '|', value = {
                "email    | ''     | 이메일을 입력해 주세요.",
                "email    | '   '  | 이메일을 입력해 주세요.",
                "email    | '　'   | 이메일을 입력해 주세요.",
                "email    | dohyun | 이메일 주소를 확인해 주세요.",
                "password | ''     | 비밀번호를 입력해 주세요."})
        @DisplayName("칸이 비었거나(전각 공백만 넣은 것 포함) 형식이 틀리면 400이고 유스케이스를 부르지 않는다")
        void rejectsInvalidField(String field, String value, String message) throws Exception {
            Map<String, Object> body = loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD);
            body.put(field, value);

            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

            verify(logInAdminUseCase, never()).logIn(any());
        }
    }

    @Nested
    @DisplayName("로그인 뒤 돌아갈 경로 — 기본은 관리자 첫 화면(/admin)")
    class ReturnPathAfterLogin {

        @Test
        @DisplayName("같은 사이트 안의 경로면 그대로 돌려준다")
        void returnsSameSitePath() throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(ADMIN, AdminGrade.ADMIN));
            Map<String, Object> body = loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD);
            body.put("returnPath", "/admin/organizers?status=UNDER_REVIEW");

            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.returnPath").value("/admin/organizers?status=UNDER_REVIEW"));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"https://evil.example", "//evil.example", "/\\evil.example", "admin", ""})
        @DisplayName("다른 사이트로 읽힐 수 있는 경로는 버리고 관리자 첫 화면(/admin)을 돌려준다 — 로그인은 된다")
        void replacesOtherSitePathWithAdminHome(String returnPath) throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(ADMIN, AdminGrade.ADMIN));
            Map<String, Object> body = loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD);
            body.put("returnPath", returnPath);

            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.returnPath").value("/admin"));
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class LogOut {

        @Test
        @DisplayName("로그아웃하면 204이고, 그 세션으로는 관리자 영역이 401이다")
        void logOutEndsSession() throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(DOHYUN, AdminGrade.SUPER_ADMIN));
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(loginRequest(loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD)).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(post(LOGOUT_PATH).session(session).with(csrf())).andExpect(status().isNoContent());

            mockMvc.perform(issueRequest().session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @Test
        @DisplayName("같은 관리자가 다른 기기에서 로그인한 세션은 남는다")
        void otherDeviceStaysLoggedIn() throws Exception {
            given(logInAdminUseCase.logIn(any())).willReturn(new LogInAdminResult(DOHYUN, AdminGrade.SUPER_ADMIN));
            given(issueAdminAccountUseCase.issue(any()))
                    .willReturn(new IssueAdminAccountResult(3, "new-admin@ticket-on.example", AdminGrade.ADMIN));
            MockHttpSession phone = new MockHttpSession();
            MockHttpSession laptop = new MockHttpSession();
            mockMvc.perform(loginRequest(loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD)).session(phone))
                    .andExpect(status().isOk());
            mockMvc.perform(loginRequest(loginBody(DOHYUN_EMAIL, DOHYUN_PASSWORD)).session(laptop))
                    .andExpect(status().isOk());

            mockMvc.perform(post(LOGOUT_PATH).session(phone).with(csrf())).andExpect(status().isNoContent());

            mockMvc.perform(issueRequest().session(laptop)).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("로그인하지 않았어도 204다")
        void logOutWithoutLogin() throws Exception {
            mockMvc.perform(post(LOGOUT_PATH).with(csrf())).andExpect(status().isNoContent());
        }
    }

    private static Map<String, Object> loginBody(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private MockHttpServletRequestBuilder loginRequest(Map<String, Object> body) {
        return post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content(json(body)).with(csrf());
    }

    private MockHttpServletRequestBuilder issueRequest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "new-admin@ticket-on.example");
        body.put("password", "new-admin-1234");
        body.put("grade", "ADMIN");
        return post(ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(json(body)).with(csrf());
    }

    private String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }
}
