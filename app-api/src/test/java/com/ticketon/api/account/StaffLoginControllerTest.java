package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.ChangeStaffNicknameCommand;
import com.ticketon.application.account.port.in.ChangeStaffNicknameResult;
import com.ticketon.application.account.port.in.ChangeStaffNicknameUseCase;
import com.ticketon.application.account.port.in.LogInStaffCommand;
import com.ticketon.application.account.port.in.LogInStaffResult;
import com.ticketon.application.account.port.in.LogInStaffUseCase;
import com.ticketon.application.account.port.in.WithdrawStaffAccountCommand;
import com.ticketon.application.account.port.in.WithdrawStaffAccountUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
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
 * 운영진 로그인·로그아웃·닉네임 변경 API — 보안 필터를 건 채로 세션이 열리고 닫히는지, 영역 분리(운영진 세션은 관객 영역 403),
 * 입력 검증(400), 돌아갈 경로, 오류 응답을 본다. 유스케이스는 흉내 낸다. 세션은 MockMvc의 세션으로 흉내 내고, DB에 저장되는 모습은
 * 통합 테스트가 본다.
 */
@WebMvcTest(controllers = {StaffLoginController.class, StaffAccountController.class})
@Import(SecurityConfiguration.class)
@DisplayName("운영진 로그인·로그아웃·닉네임 변경 API")
class StaffLoginControllerTest {

    private static final String LOGIN_PATH = "/api/auth/staff/login";

    private static final String LOGOUT_PATH = "/api/auth/staff/logout";

    private static final String NICKNAME_PATH = "/api/organizer/account/nickname";

    private static final String WITHDRAWAL_PATH = "/api/organizer/account/withdrawal";

    private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않거나, 이메일 인증을 마치지 않은 계정입니다.";

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 7);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private LogInStaffUseCase logInStaffUseCase;

    @MockitoBean
    private ChangeStaffNicknameUseCase changeStaffNicknameUseCase;

    @MockitoBean
    private WithdrawStaffAccountUseCase withdrawStaffAccountUseCase;

    @Nested
    @DisplayName("로그인")
    class LogIn {

        @Test
        @DisplayName("로그인하면 그 세션으로 운영진 영역(닉네임 변경)에 들어가고, 유스케이스는 세션의 주체(STAFF:7)를 받는다")
        void loggedInSessionEntersOrganizerArea() throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            given(changeStaffNicknameUseCase.changeNickname(any())).willReturn(new ChangeStaffNicknameResult("푸른바람"));
            MockHttpSession session = new MockHttpSession();

            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(nicknameRequest("푸른바람").session(session)).andExpect(status().isOk());
            ArgumentCaptor<ChangeStaffNicknameCommand> command = ArgumentCaptor.forClass(ChangeStaffNicknameCommand.class);
            verify(changeStaffNicknameUseCase).changeNickname(command.capture());
            assertThat(command.getValue().account()).isEqualTo(JUNGHOON);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"/api/audience/account", "/api/audience/bookings"})
        @DisplayName("운영진으로 로그인한 세션으로는 관객 영역(예매·마이페이지)이 403이다 — 주최자 쪽 로그인으로는 예매할 수 없다 (docs/01)")
        void loggedInSessionCannotEnterAudienceArea(String audiencePath) throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(get(audiencePath).session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
            mockMvc.perform(post(audiencePath).session(session).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
        }

        @Test
        @DisplayName("로그인하면 세션 id가 바뀐다 — 세션 고정 공격 방지")
        void logInChangesSessionId() throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            MockHttpSession session = new MockHttpSession();
            String sessionIdBeforeLogin = session.getId();

            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")).session(session))
                    .andExpect(status().isOk());

            assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);
        }

        @Test
        @DisplayName("이메일은 앞뒤 공백을 걷고, 비밀번호는 적은 그대로 유스케이스에 넘긴다 — 최소 길이는 보지 않는다")
        void convertsRequestToCommand() throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));

            mockMvc.perform(loginRequest(loginBody("  Junghoon@Example.com ", " abc "))).andExpect(status().isOk());

            ArgumentCaptor<LogInStaffCommand> command = ArgumentCaptor.forClass(LogInStaffCommand.class);
            verify(logInStaffUseCase).logIn(command.capture());
            assertThat(command.getValue().email()).isEqualTo(EmailAddress.of("Junghoon@Example.com"));
            assertThat(command.getValue().password().value()).isEqualTo(" abc ");
        }

        @Test
        @DisplayName("로그인이 안 되면 409 STAFF_LOGIN_FAILED(관객과 같은 문구)이고 세션은 로그인되지 않는다")
        void failedLoginLeavesSessionAnonymous() throws Exception {
            given(logInStaffUseCase.logIn(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.STAFF_LOGIN_FAILED, "비밀번호 틀림"));
            MockHttpSession session = new MockHttpSession();

            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "wrong-password")).session(session))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("STAFF_LOGIN_FAILED", LOGIN_FAILED_MESSAGE));

            mockMvc.perform(nicknameRequest("푸른바람").session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이고 유스케이스를 부르지 않는다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON)
                            .content(json(loginBody("junghoon@example.com", "junghoon1234"))))
                    .andExpect(status().isForbidden());

            verify(logInStaffUseCase, never()).logIn(any());
        }

        @ParameterizedTest(name = "{0} = \"{1}\" → {2}")
        @CsvSource(delimiter = '|', value = {
                "email    | ''       | 이메일을 입력해 주세요.",
                "email    | junghoon | 이메일 주소를 확인해 주세요.",
                "password | ''       | 비밀번호를 입력해 주세요."})
        @DisplayName("칸이 비었거나 형식이 틀리면 400이고 유스케이스를 부르지 않는다")
        void rejectsInvalidField(String field, String value, String message) throws Exception {
            Map<String, Object> body = loginBody("junghoon@example.com", "junghoon1234");
            body.put(field, value);

            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

            verify(logInStaffUseCase, never()).logIn(any());
        }
    }

    @Nested
    @DisplayName("로그인 뒤 돌아갈 경로 — 기본은 주최 첫 화면(/organizer)")
    class ReturnPathAfterLogin {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"/organizer/events", "/organizer/invitations?from=mail"})
        @DisplayName("같은 사이트 안의 경로면 그대로 돌려준다")
        void returnsSameSitePath(String returnPath) throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            Map<String, Object> body = loginBody("junghoon@example.com", "junghoon1234");
            body.put("returnPath", returnPath);

            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isOk())
                    .andExpect(content().json(json(Map.of("returnPath", returnPath)), JsonCompareMode.STRICT));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"https://evil.example", "//evil.example", "/\\evil.example", "organizer", ""})
        @DisplayName("다른 사이트로 읽힐 수 있는 경로는 버리고 주최 첫 화면(/organizer)을 돌려준다 — 로그인은 된다")
        void replacesOtherSitePathWithOrganizerHome(String returnPath) throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            Map<String, Object> body = loginBody("junghoon@example.com", "junghoon1234");
            body.put("returnPath", returnPath);

            mockMvc.perform(loginRequest(body))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"returnPath\":\"/organizer\"}", JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("돌아갈 경로를 보내지 않으면 주최 첫 화면(/organizer)이다")
        void defaultsToOrganizerHome() throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));

            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"returnPath\":\"/organizer\"}", JsonCompareMode.STRICT));
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class LogOut {

        @Test
        @DisplayName("로그아웃하면 204이고, 그 세션으로는 운영진 영역이 401이다")
        void logOutEndsSession() throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(post(LOGOUT_PATH).session(session).with(csrf())).andExpect(status().isNoContent());

            mockMvc.perform(nicknameRequest("푸른바람").session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @Test
        @DisplayName("같은 운영진이 다른 기기에서 로그인한 세션은 남는다")
        void otherDeviceStaysLoggedIn() throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            given(changeStaffNicknameUseCase.changeNickname(any())).willReturn(new ChangeStaffNicknameResult("푸른바람"));
            MockHttpSession phone = new MockHttpSession();
            MockHttpSession laptop = new MockHttpSession();
            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")).session(phone))
                    .andExpect(status().isOk());
            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")).session(laptop))
                    .andExpect(status().isOk());

            mockMvc.perform(post(LOGOUT_PATH).session(phone).with(csrf())).andExpect(status().isNoContent());

            mockMvc.perform(nicknameRequest("푸른바람").session(laptop)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("로그인하지 않았어도 204다")
        void logOutWithoutLogin() throws Exception {
            mockMvc.perform(post(LOGOUT_PATH).with(csrf())).andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("운영진 로그인 탈퇴")
    class Withdraw {

        @Test
        @DisplayName("탈퇴하면 204이고 유스케이스는 세션의 주체(STAFF:7)를 받으며, 그 세션으로는 운영진 영역이 401이다")
        void withdrawEndsSession() throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(post(WITHDRAWAL_PATH).session(session).with(csrf())).andExpect(status().isNoContent());

            verify(withdrawStaffAccountUseCase).withdraw(new WithdrawStaffAccountCommand(JUNGHOON));
            mockMvc.perform(nicknameRequest("푸른바람").session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @Test
        @DisplayName("소유자로 남은 주최자가 있어 거절되면 409 OWNER_MUST_HAND_OVER_FIRST이고 세션은 그대로다")
        void ownerIsRejectedAndStaysLoggedIn() throws Exception {
            given(logInStaffUseCase.logIn(any())).willReturn(new LogInStaffResult(JUNGHOON));
            given(changeStaffNicknameUseCase.changeNickname(any())).willReturn(new ChangeStaffNicknameResult("푸른바람"));
            willThrow(new RuleViolationException(OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST))
                    .given(withdrawStaffAccountUseCase).withdraw(new WithdrawStaffAccountCommand(JUNGHOON));
            MockHttpSession session = new MockHttpSession();
            mockMvc.perform(loginRequest(loginBody("junghoon@example.com", "junghoon1234")).session(session))
                    .andExpect(status().isOk());

            mockMvc.perform(post(WITHDRAWAL_PATH).session(session).with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("OWNER_MUST_HAND_OVER_FIRST", "먼저 소유자를 넘기거나 주최자를 닫아야 합니다."));

            mockMvc.perform(nicknameRequest("푸른바람").session(session)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("로그인하지 않았으면 401이고, 운영진이 아닌 계정이면 403이며 유스케이스를 부르지 않는다")
        void staffOnly() throws Exception {
            mockMvc.perform(post(WITHDRAWAL_PATH).with(csrf()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(WITHDRAWAL_PATH).with(csrf()).with(loggedInAs(AccountKind.AUDIENCE, 7)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(withdrawStaffAccountUseCase);
        }
    }

    @Nested
    @DisplayName("닉네임 변경")
    class ChangeNickname {

        @Test
        @DisplayName("새 닉네임(앞뒤 공백을 걷은 값)과 로그인한 주체로 유스케이스를 부르고, 저장된 닉네임을 돌려준다")
        void changesNickname() throws Exception {
            given(changeStaffNicknameUseCase.changeNickname(any())).willReturn(new ChangeStaffNicknameResult("푸른바람"));

            mockMvc.perform(nicknameRequest(" 푸른바람 ").with(loggedInAs(JUNGHOON)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"nickname\":\"푸른바람\"}", JsonCompareMode.STRICT));

            ArgumentCaptor<ChangeStaffNicknameCommand> command = ArgumentCaptor.forClass(ChangeStaffNicknameCommand.class);
            verify(changeStaffNicknameUseCase).changeNickname(command.capture());
            assertThat(command.getValue().account()).isEqualTo(JUNGHOON);
            assertThat(command.getValue().nickname()).isEqualTo(Nickname.of("푸른바람"));
        }

        @Test
        @DisplayName("다른 운영진이 쓰는 닉네임이면 409다")
        void takenNicknameIsConflict() throws Exception {
            given(changeStaffNicknameUseCase.changeNickname(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN));

            mockMvc.perform(nicknameRequest("푸른바람").with(loggedInAs(JUNGHOON)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("STAFF_NICKNAME_ALREADY_TAKEN", "이미 사용 중인 닉네임입니다."));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "   ", "　"})
        @DisplayName("닉네임이 비었으면 400이고 유스케이스를 부르지 않는다")
        void rejectsBlankNickname(String nickname) throws Exception {
            mockMvc.perform(nicknameRequest(nickname).with(loggedInAs(JUNGHOON)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("닉네임을 입력해 주세요."));

            verify(changeStaffNicknameUseCase, never()).changeNickname(any());
        }

        @Test
        @DisplayName("닉네임이 20자를 넘으면 400이고 유스케이스를 부르지 않는다 (확인 필요 #15)")
        void rejectsTooLongNickname() throws Exception {
            mockMvc.perform(nicknameRequest("가".repeat(21)).with(loggedInAs(JUNGHOON)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("닉네임은 20자 이하로 입력해 주세요."));

            verify(changeStaffNicknameUseCase, never()).changeNickname(any());
        }

        @Test
        @DisplayName("닉네임 20자는 받는다")
        void acceptsNicknameOfMaximumLength() throws Exception {
            given(changeStaffNicknameUseCase.changeNickname(any())).willReturn(new ChangeStaffNicknameResult("가".repeat(20)));

            mockMvc.perform(nicknameRequest("가".repeat(20)).with(loggedInAs(JUNGHOON))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("로그인하지 않았으면 401이다")
        void requiresLogin() throws Exception {
            mockMvc.perform(nicknameRequest("푸른바람"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"AUDIENCE", "ADMIN", "CHECKIN"})
        @DisplayName("운영진이 아닌 계정으로는 403이다 — 관객 계정으로는 운영진 영역에 들어갈 수 없다")
        void otherAccountKindIsDenied(AccountKind kind) throws Exception {
            mockMvc.perform(nicknameRequest("푸른바람").with(loggedInAs(kind, 7)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

            verify(changeStaffNicknameUseCase, never()).changeNickname(any());
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(put(NICKNAME_PATH).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("nickname", "푸른바람"))).with(loggedInAs(JUNGHOON)))
                    .andExpect(status().isForbidden());

            verify(changeStaffNicknameUseCase, never()).changeNickname(any());
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

    private MockHttpServletRequestBuilder nicknameRequest(String nickname) {
        return put(NICKNAME_PATH).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("nickname", nickname)))
                .with(csrf());
    }

    private String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }
}
