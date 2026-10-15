package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.config.DetachedExecutionConfiguration;
import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.RequestPasswordResetCommand;
import com.ticketon.application.account.port.in.RequestPasswordResetUseCase;
import com.ticketon.application.account.port.in.ResetPasswordCommand;
import com.ticketon.application.account.port.in.ResetPasswordUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비밀번호 찾기·재설정 API — 보안 필터를 건 채로 로그인 없이 열리는지, 입력 검증(400), 오류 응답을 본다. 유스케이스는 흉내 낸다.
 * <p>
 * <b>비밀번호 찾기는 가입 여부와 상관없이 같은 응답이다</b>(docs/01 "같은 이메일로 양쪽 가입"). 그 규칙을 여기서 고정한다: 응답은
 * 유스케이스가 끝나기 전에 나가고(걸리는 시간이 유스케이스가 하는 일에 따라 달라지지 않는다), 유스케이스가 실패해도 응답은 같다.
 * 작업 실행기는 Spring Boot가 만드는 진짜 실행기다. 계정이 있을 때·없을 때의 실제 응답 비교는 통합 테스트가 본다.
 */
@WebMvcTest(controllers = PasswordResetController.class)
@Import({SecurityConfiguration.class, DetachedExecutionConfiguration.class})
@DisplayName("비밀번호 찾기·재설정 API")
class PasswordResetControllerTest {

    private static final String AUDIENCE_REQUEST_PATH = "/api/auth/audience/password-reset-request";

    private static final String STAFF_REQUEST_PATH = "/api/auth/staff/password-reset-request";

    private static final String AUDIENCE_RESET_PATH = "/api/auth/audience/password-reset";

    private static final String STAFF_RESET_PATH = "/api/auth/staff/password-reset";

    private static final String TOKEN_VALUE = "resetAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @MockitoBean
    private ResetPasswordUseCase resetPasswordUseCase;

    @Nested
    @DisplayName("비밀번호 찾기 — 언제나 같은 응답")
    class RequestReset {

        @Test
        @DisplayName("로그인 없이 202, 본문 없음이고, 유스케이스는 관객 쪽(AUDIENCE)과 앞뒤 공백을 걷은 이메일을 받는다")
        void acceptsAndPassesCommand() throws Exception {
            MockHttpServletResponse response = mockMvc.perform(requestReset(AUDIENCE_REQUEST_PATH, "  Seoyun@Example.com "))
                    .andExpect(status().isAccepted())
                    .andReturn().getResponse();

            assertThat(response.getContentAsString()).isEmpty();
            ArgumentCaptor<RequestPasswordResetCommand> command = ArgumentCaptor.forClass(RequestPasswordResetCommand.class);
            verify(requestPasswordResetUseCase, timeout(5_000)).requestReset(command.capture());
            assertThat(command.getValue())
                    .isEqualTo(new RequestPasswordResetCommand(AccountKind.AUDIENCE, EmailAddress.of("Seoyun@Example.com")));
        }

        @Test
        @DisplayName("주최자 경로는 운영진 로그인 쪽(STAFF)으로 넘기고, 응답은 관객 쪽과 똑같다")
        void staffPathPassesStaffKind() throws Exception {
            MockHttpServletResponse audience = mockMvc.perform(requestReset(AUDIENCE_REQUEST_PATH, "junghoon@example.com"))
                    .andReturn().getResponse();
            MockHttpServletResponse staff = mockMvc.perform(requestReset(STAFF_REQUEST_PATH, "junghoon@example.com"))
                    .andReturn().getResponse();

            assertSameResponse(staff, audience);
            ArgumentCaptor<RequestPasswordResetCommand> command = ArgumentCaptor.forClass(RequestPasswordResetCommand.class);
            verify(requestPasswordResetUseCase, timeout(5_000).times(2)).requestReset(command.capture());
            assertThat(command.getAllValues()).extracting(RequestPasswordResetCommand::accountKind)
                    .containsExactlyInAnyOrder(AccountKind.AUDIENCE, AccountKind.STAFF);
        }

        @Test
        @DisplayName("응답은 유스케이스가 끝나기 전에 나간다 — 계정이 있을 때만 하는 일(링크 저장·메일 발송)이 응답 시간에 드러나지 않는다")
        void respondsBeforeUseCaseFinishes() throws Exception {
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean finished = new AtomicBoolean();
            willAnswer(invocation -> {
                release.await(5, TimeUnit.SECONDS);
                finished.set(true);
                return null;
            }).given(requestPasswordResetUseCase).requestReset(any());

            mockMvc.perform(requestReset(AUDIENCE_REQUEST_PATH, "seoyun@example.com")).andExpect(status().isAccepted());

            assertThat(finished).as("응답이 나갈 때 유스케이스는 아직 끝나지 않았다").isFalse();
            release.countDown();
            verify(requestPasswordResetUseCase, timeout(5_000)).requestReset(any());
        }

        @Test
        @DisplayName("유스케이스가 실패해도(메일 발송 실패 등) 응답은 성공했을 때와 똑같다 — 실패는 로그에만 남는다")
        void failureDoesNotChangeResponse() throws Exception {
            MockHttpServletResponse succeeded = mockMvc.perform(requestReset(AUDIENCE_REQUEST_PATH, "seoyun@example.com"))
                    .andReturn().getResponse();
            verify(requestPasswordResetUseCase, timeout(5_000)).requestReset(any());
            willThrow(new IllegalStateException("비밀번호 재설정 메일을 보내지 못해 새 링크를 되돌립니다"))
                    .given(requestPasswordResetUseCase).requestReset(any());

            MockHttpServletResponse failed = mockMvc.perform(requestReset(AUDIENCE_REQUEST_PATH, "seoyun@example.com"))
                    .andReturn().getResponse();

            verify(requestPasswordResetUseCase, timeout(5_000).times(2)).requestReset(any());
            assertSameResponse(failed, succeeded);
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이고 유스케이스를 부르지 않는다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(AUDIENCE_REQUEST_PATH).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "seoyun@example.com"))))
                    .andExpect(status().isForbidden());

            verify(requestPasswordResetUseCase, after(200).never()).requestReset(any());
        }

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource(delimiter = '|', value = {
                "''      | 이메일을 입력해 주세요.",
                "'　' | 이메일을 입력해 주세요.",
                "' 　 　 ' | 이메일을 입력해 주세요.",
                "seoyun  | 이메일 주소를 확인해 주세요."})
        @DisplayName("이메일이 비었거나(전각 공백 같은 유니코드 공백만 넣은 것 포함) 형식이 틀리면 400이고 유스케이스를 부르지 않는다 — 적은 값만 보고 정하므로 가입 여부와 상관없다")
        void rejectsInvalidEmail(String email, String message) throws Exception {
            mockMvc.perform(requestReset(STAFF_REQUEST_PATH, email))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

            verify(requestPasswordResetUseCase, after(200).never()).requestReset(any());
        }

        private MockHttpServletRequestBuilder requestReset(String path, String email) {
            return post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email)));
        }

        private void assertSameResponse(MockHttpServletResponse actual, MockHttpServletResponse expected)
                throws Exception {
            assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
            assertThat(actual.getContentAsString()).isEqualTo(expected.getContentAsString());
            assertThat(actual.getContentType()).isEqualTo(expected.getContentType());
            assertThat(actual.getHeaderNames()).containsExactlyInAnyOrderElementsOf(expected.getHeaderNames());
        }
    }

    @Nested
    @DisplayName("비밀번호 재설정")
    class Reset {

        @Test
        @DisplayName("로그인 없이 204이고, 유스케이스는 관객 쪽과 링크 값·새 비밀번호를 그대로 받는다")
        void resetsAudiencePassword() throws Exception {
            mockMvc.perform(reset(AUDIENCE_RESET_PATH, resetBody(TOKEN_VALUE, " new-password-5678 ")))
                    .andExpect(status().isNoContent());

            ArgumentCaptor<ResetPasswordCommand> command = ArgumentCaptor.forClass(ResetPasswordCommand.class);
            verify(resetPasswordUseCase).resetPassword(command.capture());
            assertThat(command.getValue().accountKind()).isEqualTo(AccountKind.AUDIENCE);
            assertThat(command.getValue().token()).isEqualTo(TOKEN_VALUE);
            assertThat(command.getValue().newPassword().value()).isEqualTo(" new-password-5678 ");
        }

        @Test
        @DisplayName("주최자 경로는 운영진 로그인 쪽(STAFF)으로 넘긴다")
        void resetsStaffPassword() throws Exception {
            mockMvc.perform(reset(STAFF_RESET_PATH, resetBody(TOKEN_VALUE, "new-password-5678")))
                    .andExpect(status().isNoContent());

            ArgumentCaptor<ResetPasswordCommand> command = ArgumentCaptor.forClass(ResetPasswordCommand.class);
            verify(resetPasswordUseCase).resetPassword(command.capture());
            assertThat(command.getValue().accountKind()).isEqualTo(AccountKind.STAFF);
        }

        @Test
        @DisplayName("발급한 적 없는 링크면 404 PASSWORD_RESET_LINK_NOT_FOUND")
        void unknownTokenIsNotFound() throws Exception {
            willThrow(new NotFoundException(AccountErrorCode.PASSWORD_RESET_LINK_NOT_FOUND))
                    .given(resetPasswordUseCase).resetPassword(any());

            mockMvc.perform(reset(AUDIENCE_RESET_PATH, resetBody(TOKEN_VALUE, "new-password-5678")))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("PASSWORD_RESET_LINK_NOT_FOUND", "비밀번호 재설정 링크가 올바르지 않습니다."));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"PASSWORD_RESET_LINK_ALREADY_USED", "PASSWORD_RESET_LINK_REPLACED",
                "PASSWORD_RESET_LINK_EXPIRED"})
        @DisplayName("쓴 링크·대체된 링크·유효 시간이 지난 링크는 409와 그 문구다")
        void unusableTokenIsConflict(String code) throws Exception {
            AccountErrorCode errorCode = AccountErrorCode.valueOf(code);
            willThrow(new RuleViolationException(errorCode)).given(resetPasswordUseCase).resetPassword(any());

            mockMvc.perform(reset(AUDIENCE_RESET_PATH, resetBody(TOKEN_VALUE, "new-password-5678")))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody(code, errorCode.message()));
        }

        @ParameterizedTest(name = "{0} = \"{1}\" → {2}")
        @CsvSource(delimiter = '|', value = {
                "token       | ''       | 비밀번호 재설정 링크가 올바르지 않습니다.",
                "newPassword | 1234567  | 비밀번호는 8자 이상 입력해 주세요.",
                "newPassword | ''       | 비밀번호는 8자 이상 입력해 주세요."})
        @DisplayName("링크 값이 비었거나 새 비밀번호가 규칙(8자 이상, 확인 필요)에 맞지 않으면 400이고 유스케이스를 부르지 않는다")
        void rejectsInvalidField(String field, String value, String message) throws Exception {
            Map<String, Object> body = resetBody(TOKEN_VALUE, "new-password-5678");
            body.put(field, value);

            mockMvc.perform(reset(STAFF_RESET_PATH, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

            verify(resetPasswordUseCase, never()).resetPassword(any());
        }

        @Test
        @DisplayName("새 비밀번호가 없으면 400 '비밀번호를 입력해 주세요.'")
        void rejectsMissingPassword() throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token", TOKEN_VALUE);

            mockMvc.perform(reset(AUDIENCE_RESET_PATH, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("비밀번호를 입력해 주세요."));
        }

        private MockHttpServletRequestBuilder reset(String path, Map<String, Object> body) {
            return post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(body));
        }

        private Map<String, Object> resetBody(String token, String newPassword) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token", token);
            body.put("newPassword", newPassword);
            return body;
        }
    }

    private String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }
}
