package com.ticketon.api.admin;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static com.ticketon.api.security.TestLogin.loggedInAsAdmin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.admin.port.in.IssueAdminAccountCommand;
import com.ticketon.application.admin.port.in.IssueAdminAccountResult;
import com.ticketon.application.admin.port.in.IssueAdminAccountUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 계정 발급 API — 최고 관리자 전용 경로가 등급으로 갈리는지(관리자 403), 요청 → Command 변환, 입력 검증(400), 오류 응답을 본다.
 * 유스케이스는 흉내 낸다. 계정 저장과 조작 기록은 유스케이스 테스트와 통합 테스트가 본다.
 */
@WebMvcTest(controllers = AdminAccountController.class)
@Import(SecurityConfiguration.class)
@DisplayName("관리자 계정 발급 API — 최고 관리자만")
class AdminAccountControllerTest {

    private static final String ISSUE_PATH = "/api/admin/super/admin-accounts";

    private static final AccountRef DOHYUN = AccountRef.of(AccountKind.ADMIN, 1);

    private static final String NEW_ADMIN_EMAIL = "new-admin@ticket-on.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private IssueAdminAccountUseCase issueAdminAccountUseCase;

    @Nested
    @DisplayName("최고 관리자가 발급한다")
    class SuperAdmin {

        @Test
        @DisplayName("201에 발급한 계정의 이메일·등급을 돌려주고, 유스케이스는 로그인한 최고 관리자와 요청 값(이메일은 앞뒤 공백을 걷음)을 받는다")
        void issuesAdminAccount() throws Exception {
            given(issueAdminAccountUseCase.issue(any()))
                    .willReturn(new IssueAdminAccountResult(3, NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

            mockMvc.perform(issueRequest(issueBody("  " + NEW_ADMIN_EMAIL + " ", "new-admin-1234", "ADMIN"))
                            .with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isCreated())
                    .andExpect(content().json("{\"email\":\"new-admin@ticket-on.example\",\"grade\":\"ADMIN\"}",
                            JsonCompareMode.STRICT));

            ArgumentCaptor<IssueAdminAccountCommand> command = ArgumentCaptor.forClass(IssueAdminAccountCommand.class);
            verify(issueAdminAccountUseCase).issue(command.capture());
            assertThat(command.getValue().issuer()).isEqualTo(DOHYUN);
            assertThat(command.getValue().email()).isEqualTo(EmailAddress.of(NEW_ADMIN_EMAIL));
            assertThat(command.getValue().password().value()).isEqualTo("new-admin-1234");
            assertThat(command.getValue().grade()).isEqualTo(AdminGrade.ADMIN);
        }

        @Test
        @DisplayName("최고 관리자 등급으로도 발급한다")
        void issuesSuperAdminAccount() throws Exception {
            given(issueAdminAccountUseCase.issue(any()))
                    .willReturn(new IssueAdminAccountResult(3, NEW_ADMIN_EMAIL, AdminGrade.SUPER_ADMIN));

            mockMvc.perform(issueRequest(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "SUPER_ADMIN"))
                            .with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.grade").value("SUPER_ADMIN"));

            ArgumentCaptor<IssueAdminAccountCommand> command = ArgumentCaptor.forClass(IssueAdminAccountCommand.class);
            verify(issueAdminAccountUseCase).issue(command.capture());
            assertThat(command.getValue().grade()).isEqualTo(AdminGrade.SUPER_ADMIN);
        }

        @Test
        @DisplayName("같은 이메일의 관리자 계정이 있으면 409 '이미 가입된 이메일입니다.'")
        void duplicateEmailIsConflict() throws Exception {
            given(issueAdminAccountUseCase.issue(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED));

            mockMvc.perform(issueRequest(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "ADMIN"))
                            .with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("ADMIN_EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다."));
        }

        @Test
        @DisplayName("같은 이메일로 동시에 발급해 진 쪽도 409 같은 코드다")
        void lostRaceIsConflict() throws Exception {
            given(issueAdminAccountUseCase.issue(any()))
                    .willThrow(new ConflictException(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED));

            mockMvc.perform(issueRequest(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "ADMIN"))
                            .with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("ADMIN_EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다."));
        }

        @Test
        @DisplayName("세션은 최고 관리자인데 저장소의 등급이 아니면 유스케이스가 막아 403 SUPER_ADMIN_ONLY_ACTION이다")
        void useCasePermissionDeniedIsForbidden() throws Exception {
            given(issueAdminAccountUseCase.issue(any()))
                    .willThrow(new PermissionDeniedException(AccountErrorCode.SUPER_ADMIN_ONLY_ACTION));

            mockMvc.perform(issueRequest(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "ADMIN"))
                            .with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("SUPER_ADMIN_ONLY_ACTION", "최고 관리자만 할 수 있는 일입니다."));
        }

        @ParameterizedTest(name = "{0} = \"{1}\" → {2}")
        @CsvSource(delimiter = '|', value = {
                "email    | ''       | 이메일을 입력해 주세요.",
                "email    | '　'     | 이메일을 입력해 주세요.",
                "email    | new      | 이메일 주소를 확인해 주세요.",
                "password | ''       | 비밀번호는 8자 이상 입력해 주세요.",
                "password | 1234567  | 비밀번호는 8자 이상 입력해 주세요."})
        @DisplayName("칸이 비었거나(전각 공백만 넣은 것 포함) 형식이 틀리면 400이고 유스케이스를 부르지 않는다")
        void rejectsInvalidField(String field, String value, String message) throws Exception {
            Map<String, Object> body = issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "ADMIN");
            body.put(field, value);

            mockMvc.perform(issueRequest(body).with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

            verify(issueAdminAccountUseCase, never()).issue(any());
        }

        @Test
        @DisplayName("비밀번호·등급을 빼면 400이고 유스케이스를 부르지 않는다")
        void rejectsMissingPasswordAndGrade() throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("email", NEW_ADMIN_EMAIL);

            mockMvc.perform(issueRequest(body).with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(2)));

            verify(issueAdminAccountUseCase, never()).issue(any());
        }

        @Test
        @DisplayName("없는 등급 이름이면 400이고 유스케이스를 부르지 않는다")
        void rejectsUnknownGrade() throws Exception {
            mockMvc.perform(issueRequest(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "OWNER"))
                            .with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isBadRequest());

            verify(issueAdminAccountUseCase, never()).issue(any());
        }
    }

    @Nested
    @DisplayName("최고 관리자가 아니면 막힌다 — 유스케이스를 부르지 않는다")
    class NotSuperAdmin {

        @Test
        @DisplayName("관리자가 발급하려 하면 403이다 (docs/01 \"관리자 2등급\" — 관리자 계정 발급은 최고 관리자만)")
        void adminIsForbidden() throws Exception {
            mockMvc.perform(issueRequest(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "ADMIN"))
                            .with(loggedInAsAdmin(2, AdminGrade.ADMIN)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

            verify(issueAdminAccountUseCase, never()).issue(any());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(AccountKind.class)
        @DisplayName("등급 없이 로그인한 주체(관리자 종류 포함)는 403이다")
        void accountWithoutGradeIsForbidden(AccountKind kind) throws Exception {
            mockMvc.perform(issueRequest(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "ADMIN")).with(loggedInAs(kind, 7)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

            verify(issueAdminAccountUseCase, never()).issue(any());
        }

        @Test
        @DisplayName("로그인하지 않았으면 401이다")
        void requiresLogin() throws Exception {
            mockMvc.perform(issueRequest(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "ADMIN")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 최고 관리자도 403이다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(ISSUE_PATH).contentType(MediaType.APPLICATION_JSON)
                            .content(json(issueBody(NEW_ADMIN_EMAIL, "new-admin-1234", "ADMIN")))
                            .with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isForbidden());

            verify(issueAdminAccountUseCase, never()).issue(any());
        }
    }

    private static Map<String, Object> issueBody(String email, String password, String grade) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("grade", grade);
        return body;
    }

    private MockHttpServletRequestBuilder issueRequest(Map<String, Object> body) {
        return post(ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(json(body)).with(csrf());
    }

    private String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }
}
