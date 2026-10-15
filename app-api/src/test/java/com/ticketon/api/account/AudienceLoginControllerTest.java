package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
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

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.GetMyAudienceAccountQuery;
import com.ticketon.application.account.port.in.GetMyAudienceAccountResult;
import com.ticketon.application.account.port.in.GetMyAudienceAccountUseCase;
import com.ticketon.application.account.port.in.LogInAudienceCommand;
import com.ticketon.application.account.port.in.LogInAudienceResult;
import com.ticketon.application.account.port.in.LogInAudienceUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.RuleViolationException;
import java.util.LinkedHashMap;
import java.util.List;
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
 * 관객 로그인·로그아웃·내 계정 조회 API — 보안 필터를 건 채로 세션이 열리고 닫히는지, 입력 검증(400), 돌아갈 경로, 오류 응답을 본다.
 * 유스케이스는 흉내 낸다. 세션은 MockMvc의 세션으로 흉내 내고, DB에 저장되는 모습은 통합 테스트가 본다.
 */
@WebMvcTest(controllers = {AudienceLoginController.class, AudienceAccountController.class})
@Import(SecurityConfiguration.class)
@DisplayName("관객 로그인·로그아웃·내 계정 조회 API")
class AudienceLoginControllerTest {

    private static final String LOGIN_PATH = "/api/auth/audience/login";

    private static final String LOGOUT_PATH = "/api/auth/audience/logout";

    private static final String ACCOUNT_PATH = "/api/audience/account";

    private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않거나, 이메일 인증을 마치지 않은 계정입니다.";

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, 7);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private LogInAudienceUseCase logInAudienceUseCase;

    @MockitoBean
    private GetMyAudienceAccountUseCase getMyAudienceAccountUseCase;

    @Nested
    @DisplayName("로그인")
    class LogIn {

        @Test
        @DisplayName("로그인하면 그 세션으로 관객 영역(내 계정)에 들어가고, 유스케이스는 세션의 주체(AUDIENCE:7)를 받는다")
        void loggedInSessionEntersAudienceArea() throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));
            given(getMyAudienceAccountUseCase.getMyAccount(any())).willReturn(seoyunAccount(false));
            MockHttpSession session = new MockHttpSession();

            mockMvc.perform(loginRequest(body("seoyun@example.com", "seoyun1234")).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(get(ACCOUNT_PATH).session(session)).andExpect(status().isOk());
            ArgumentCaptor<GetMyAudienceAccountQuery> query = ArgumentCaptor.forClass(GetMyAudienceAccountQuery.class);
            verify(getMyAudienceAccountUseCase).getMyAccount(query.capture());
            assertThat(query.getValue().account()).isEqualTo(SEOYUN);
        }

        @Test
        @DisplayName("로그인하면 세션 id가 바뀐다 — 세션 고정 공격 방지")
        void logInChangesSessionId() throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));
            MockHttpSession session = new MockHttpSession();
            String sessionIdBeforeLogin = session.getId();

            mockMvc.perform(loginRequest(body("seoyun@example.com", "seoyun1234")).session(session))
                    .andExpect(status().isOk());

            assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);
        }

        @Test
        @DisplayName("이메일은 앞뒤 공백을 걷고, 비밀번호는 적은 그대로 유스케이스에 넘긴다")
        void convertsRequestToCommand() throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));

            mockMvc.perform(loginRequest(body("  Seoyun@Example.com ", " seoyun 1234 "))).andExpect(status().isOk());

            ArgumentCaptor<LogInAudienceCommand> command = ArgumentCaptor.forClass(LogInAudienceCommand.class);
            verify(logInAudienceUseCase).logIn(command.capture());
            assertThat(command.getValue().email()).isEqualTo(EmailAddress.of("Seoyun@Example.com"));
            assertThat(command.getValue().password().value()).isEqualTo(" seoyun 1234 ");
        }

        @Test
        @DisplayName("새 비밀번호의 최소 길이는 보지 않는다 — 짧은 비밀번호도 유스케이스가 틀린 비밀번호로 판단한다")
        void shortPasswordReachesUseCase() throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));

            mockMvc.perform(loginRequest(body("seoyun@example.com", "abc"))).andExpect(status().isOk());

            verify(logInAudienceUseCase).logIn(any());
        }

        @Test
        @DisplayName("로그인이 안 되면 409 AUDIENCE_LOGIN_FAILED이고 세션은 로그인되지 않는다")
        void failedLoginLeavesSessionAnonymous() throws Exception {
            given(logInAudienceUseCase.logIn(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.AUDIENCE_LOGIN_FAILED, "비밀번호 틀림"));
            MockHttpSession session = new MockHttpSession();

            mockMvc.perform(loginRequest(body("seoyun@example.com", "wrong-password")).session(session))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("AUDIENCE_LOGIN_FAILED", LOGIN_FAILED_MESSAGE));

            mockMvc.perform(get(ACCOUNT_PATH).session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이고 유스케이스를 부르지 않는다 — 남의 계정으로 몰래 로그인시키는 요청을 막는다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON)
                            .content(json(body("seoyun@example.com", "seoyun1234"))))
                    .andExpect(status().isForbidden());

            verify(logInAudienceUseCase, never()).logIn(any());
        }
    }

    @Nested
    @DisplayName("로그인 입력 검증 — 400 INVALID_INPUT, 칸마다 문구")
    class Validation {

        @ParameterizedTest(name = "{0} = \"{1}\" → {2}")
        @CsvSource(delimiter = '|', value = {
                "email    | ''                  | 이메일을 입력해 주세요.",
                "email    | '   '               | 이메일을 입력해 주세요.",
                "email    | seoyun              | 이메일 주소를 확인해 주세요.",
                "email    | seo yun@example.com | 이메일 주소를 확인해 주세요.",
                "password | ''                  | 비밀번호를 입력해 주세요."})
        @DisplayName("칸이 비었거나 형식이 틀리면 그 칸과 문구를 알려 주고 유스케이스를 부르지 않는다")
        void rejectsInvalidField(String field, String value, String message) throws Exception {
            Map<String, Object> body = body("seoyun@example.com", "seoyun1234");
            body.put(field, value);

            expectInvalidField(body, field, message);
        }

        @ParameterizedTest(name = "{0} 없음 → {1}")
        @CsvSource(delimiter = '|', value = {
                "email    | 이메일을 입력해 주세요.",
                "password | 비밀번호를 입력해 주세요."})
        @DisplayName("칸이 아예 없으면 '입력해 주세요'다")
        void rejectsMissingField(String field, String message) throws Exception {
            Map<String, Object> body = body("seoyun@example.com", "seoyun1234");
            body.remove(field);

            expectInvalidField(body, field, message);
        }

        private void expectInvalidField(Map<String, Object> body, String field, String message) throws Exception {
            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

            verify(logInAudienceUseCase, never()).logIn(any());
        }
    }

    @Nested
    @DisplayName("로그인 뒤 돌아갈 경로 — 보던 행사의 예매 화면으로 (docs/06 \"전체 흐름\")")
    class ReturnPathAfterLogin {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"/events/12/booking", "/events/12/booking?session=3", "/"})
        @DisplayName("같은 사이트 안의 경로면 그대로 돌려준다")
        void returnsSameSitePath(String returnPath) throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));
            Map<String, Object> body = body("seoyun@example.com", "seoyun1234");
            body.put("returnPath", returnPath);

            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isOk())
                    .andExpect(content().json(json(Map.of("returnPath", returnPath)), JsonCompareMode.STRICT));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"https://evil.example", "//evil.example", "/\\evil.example", "events/12", ""})
        @DisplayName("다른 사이트로 읽힐 수 있는 경로는 버리고 관객 첫 화면(/)을 돌려준다 — 로그인은 된다")
        void replacesOtherSitePathWithHome(String returnPath) throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));
            Map<String, Object> body = body("seoyun@example.com", "seoyun1234");
            body.put("returnPath", returnPath);

            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"returnPath\":\"/\"}", JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("돌아갈 경로를 보내지 않으면 관객 첫 화면(/)이다")
        void defaultsToHome() throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));

            mockMvc.perform(loginRequest(body("seoyun@example.com", "seoyun1234")))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"returnPath\":\"/\"}", JsonCompareMode.STRICT));
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class LogOut {

        @Test
        @DisplayName("로그아웃하면 204이고, 그 세션으로는 내 계정이 401이다")
        void logOutEndsSession() throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(loginRequest(body("seoyun@example.com", "seoyun1234")).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(post(LOGOUT_PATH).session(session).with(csrf())).andExpect(status().isNoContent());

            mockMvc.perform(get(ACCOUNT_PATH).session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @Test
        @DisplayName("같은 관객이 다른 기기에서 로그인한 세션은 남는다")
        void otherDeviceStaysLoggedIn() throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));
            given(getMyAudienceAccountUseCase.getMyAccount(any())).willReturn(seoyunAccount(false));
            MockHttpSession phone = new MockHttpSession();
            MockHttpSession laptop = new MockHttpSession();
            mockMvc.perform(loginRequest(body("seoyun@example.com", "seoyun1234")).session(phone)).andExpect(status().isOk());
            mockMvc.perform(loginRequest(body("seoyun@example.com", "seoyun1234")).session(laptop)).andExpect(status().isOk());

            mockMvc.perform(post(LOGOUT_PATH).session(phone).with(csrf())).andExpect(status().isNoContent());

            mockMvc.perform(get(ACCOUNT_PATH).session(laptop)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("로그인하지 않았어도 204다 — 세션이 이미 끝난 화면에서 눌러도 오류가 아니다")
        void logOutWithoutLogin() throws Exception {
            mockMvc.perform(post(LOGOUT_PATH).with(csrf())).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이고 세션은 그대로다")
        void requiresCsrfToken() throws Exception {
            given(logInAudienceUseCase.logIn(any())).willReturn(new LogInAudienceResult(SEOYUN));
            given(getMyAudienceAccountUseCase.getMyAccount(any())).willReturn(seoyunAccount(false));
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(loginRequest(body("seoyun@example.com", "seoyun1234")).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(post(LOGOUT_PATH).session(session)).andExpect(status().isForbidden());

            mockMvc.perform(get(ACCOUNT_PATH).session(session)).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("내 계정 조회")
    class MyAccount {

        @Test
        @DisplayName("이름·이메일·전화번호·이용 정지 여부·연결된 카카오·구글을 돌려준다 — 계정 id는 내보내지 않는다")
        void returnsAccount() throws Exception {
            given(getMyAudienceAccountUseCase.getMyAccount(any())).willReturn(seoyunAccount(false));

            mockMvc.perform(get(ACCOUNT_PATH).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"name":"한서윤","email":"seoyun@example.com","phoneNumber":"01012345678",
                             "suspended":false,"linkedOAuthProviders":[]}""", JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("연결된 카카오·구글은 API 이름(KAKAO, GOOGLE)으로 받은 순서대로 돌려준다")
        void returnsLinkedProviders() throws Exception {
            given(getMyAudienceAccountUseCase.getMyAccount(any())).willReturn(new GetMyAudienceAccountResult("한서윤",
                    "seoyun@example.com", "01012345678", false, List.of(OAuthProvider.KAKAO, OAuthProvider.GOOGLE)));

            mockMvc.perform(get(ACCOUNT_PATH).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.linkedOAuthProviders[0]").value("KAKAO"))
                    .andExpect(jsonPath("$.linkedOAuthProviders[1]").value("GOOGLE"));
        }

        @Test
        @DisplayName("이용 정지 중이면 suspended가 true다 — 정지 중에도 조회된다")
        void suspendedAccount() throws Exception {
            given(getMyAudienceAccountUseCase.getMyAccount(any())).willReturn(seoyunAccount(true));

            mockMvc.perform(get(ACCOUNT_PATH).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.suspended").value(true));
        }

        @Test
        @DisplayName("로그인하지 않았으면 401이다")
        void requiresLogin() throws Exception {
            mockMvc.perform(get(ACCOUNT_PATH))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"STAFF", "ADMIN", "CHECKIN"})
        @DisplayName("관객이 아닌 계정으로는 403이다 — 주최자 쪽 로그인으로는 관객 영역에 들어갈 수 없다 (docs/01)")
        void otherAccountKindIsDenied(AccountKind kind) throws Exception {
            mockMvc.perform(get(ACCOUNT_PATH).with(loggedInAs(kind, 7)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

            verify(getMyAudienceAccountUseCase, never()).getMyAccount(any());
        }

        @Test
        @DisplayName("관객으로 로그인해도 운영진 영역은 403이다")
        void audienceCannotEnterOrganizerArea() throws Exception {
            mockMvc.perform(get("/api/organizer/events").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
        }
    }

    private static GetMyAudienceAccountResult seoyunAccount(boolean suspended) {
        return new GetMyAudienceAccountResult("한서윤", "seoyun@example.com", "01012345678", suspended, List.of());
    }

    private static Map<String, Object> body(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private MockHttpServletRequestBuilder loginRequest(Map<String, Object> body) {
        return post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content(json(body)).with(csrf());
    }

    private String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }
}
