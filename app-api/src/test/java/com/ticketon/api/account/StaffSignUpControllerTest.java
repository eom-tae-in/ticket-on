package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.GetRandomNicknameResult;
import com.ticketon.application.account.port.in.GetRandomNicknameUseCase;
import com.ticketon.application.account.port.in.SignUpStaffCommand;
import com.ticketon.application.account.port.in.SignUpStaffResult;
import com.ticketon.application.account.port.in.SignUpStaffUseCase;
import com.ticketon.application.account.port.in.VerifyStaffEmailCommand;
import com.ticketon.application.account.port.in.VerifyStaffEmailUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * 운영진 로그인 가입·이메일 인증·랜덤 닉네임 API — 입력 검증(400), Request → Command 변환, 도메인 오류 → 응답을 본다.
 * 유스케이스는 흉내 낸다. DB 없이 돈다.
 */
@WebMvcTest(controllers = StaffSignUpController.class)
@Import(SecurityConfiguration.class)
@DisplayName("운영진 로그인 가입·이메일 인증·랜덤 닉네임 API")
class StaffSignUpControllerTest {

    private static final String SIGNUP_PATH = "/api/auth/staff/signup";

    private static final String VERIFICATION_PATH = "/api/auth/staff/email-verification";

    private static final String RANDOM_NICKNAME_PATH = "/api/auth/staff/random-nickname";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private SignUpStaffUseCase signUpStaffUseCase;

    @MockitoBean
    private VerifyStaffEmailUseCase verifyStaffEmailUseCase;

    @MockitoBean
    private GetRandomNicknameUseCase getRandomNicknameUseCase;

    @Nested
    @DisplayName("가입")
    class SignUp {

        @BeforeEach
        void signUpSucceeds() {
            given(signUpStaffUseCase.signUp(any())).willReturn(new SignUpStaffResult(7, "junghoon@example.com"));
        }

        @Test
        @DisplayName("로그인 없이 가입하면 201과 인증 링크를 보낸 이메일을 돌려준다 — 계정 id는 내보내지 않는다")
        void returnsCreatedWithEmail() throws Exception {
            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isCreated())
                    .andExpect(content().json("{\"email\":\"junghoon@example.com\"}", JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("입력을 가입 정보·닉네임·비밀번호로 바꿔 유스케이스에 넘긴다 — 이름·닉네임·이메일은 앞뒤 공백을 걷는다")
        void convertsRequestToCommand() throws Exception {
            Map<String, Object> body = validBody();
            body.put("name", " 박정훈 ");
            body.put("nickname", " 조용한고래 ");
            body.put("email", "  junghoon@example.com ");

            mockMvc.perform(signUpRequest(body)).andExpect(status().isCreated());

            ArgumentCaptor<SignUpStaffCommand> command = ArgumentCaptor.forClass(SignUpStaffCommand.class);
            verify(signUpStaffUseCase).signUp(command.capture());
            assertThat(command.getValue().personalInformation())
                    .isEqualTo(StaffPersonalInformation.of("박정훈", "junghoon@example.com"));
            assertThat(command.getValue().nickname()).isEqualTo(Nickname.of("조용한고래"));
            assertThat(command.getValue().password().value()).isEqualTo("junghoon1234");
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(SIGNUP_PATH).contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                    .andExpect(status().isForbidden());

            verify(signUpStaffUseCase, never()).signUp(any());
        }
    }

    @Nested
    @DisplayName("입력 검증 — 400 INVALID_INPUT, 칸마다 문구")
    class Validation {

        @ParameterizedTest(name = "{0} = \"{1}\" → {2}")
        @CsvSource(delimiter = '|', value = {
                "name     | '   '                    | 이름을 입력해 주세요.",
                "nickname | ''                       | 닉네임을 입력해 주세요.",
                "nickname | '   '                    | 닉네임을 입력해 주세요.",
                "nickname | '　'                 | 닉네임을 입력해 주세요.",
                "email    | ''                       | 이메일을 입력해 주세요.",
                "email    | junghoon                 | 이메일 주소를 확인해 주세요.",
                "email    | jung hoon@example.com    | 이메일 주소를 확인해 주세요.",
                "password | abc1234                  | 비밀번호는 8자 이상 입력해 주세요.",
                "password | ''                       | 비밀번호는 8자 이상 입력해 주세요."})
        @DisplayName("칸이 비었거나 형식이 틀리면 그 칸과 문구를 알려 주고 유스케이스를 부르지 않는다")
        void rejectsInvalidField(String field, String value, String message) throws Exception {
            Map<String, Object> body = validBody();
            body.put(field, value);

            expectInvalidField(body, field, message);
        }

        @ParameterizedTest(name = "{0} 없음 → {1}")
        @CsvSource(delimiter = '|', value = {
                "name     | 이름을 입력해 주세요.",
                "nickname | 닉네임을 입력해 주세요.",
                "email    | 이메일을 입력해 주세요.",
                "password | 비밀번호를 입력해 주세요."})
        @DisplayName("칸이 아예 없으면 '입력해 주세요'다")
        void rejectsMissingField(String field, String message) throws Exception {
            Map<String, Object> body = validBody();
            body.remove(field);

            expectInvalidField(body, field, message);
        }

        private void expectInvalidField(Map<String, Object> body, String field, String message) throws Exception {
            mockMvc.perform(signUpRequest(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

            verify(signUpStaffUseCase, never()).signUp(any());
        }
    }

    @Nested
    @DisplayName("가입 규칙에 막히면 409, 문구는 도메인 오류 그대로")
    class Rejections {

        @Test
        @DisplayName("같은 이메일의 운영진 로그인이 있음")
        void emailAlreadyRegistered() throws Exception {
            given(signUpStaffUseCase.signUp(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED));

            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("STAFF_EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다."));
        }

        @Test
        @DisplayName("다른 운영진이 쓰는 닉네임")
        void nicknameAlreadyTaken() throws Exception {
            given(signUpStaffUseCase.signUp(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN));

            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("STAFF_NICKNAME_ALREADY_TAKEN", "이미 사용 중인 닉네임입니다."));
        }

        @Test
        @DisplayName("동시 가입 경합에서 진 쪽도 같은 코드·문구다")
        void signupRaceLoserGetsSameCode() throws Exception {
            given(signUpStaffUseCase.signUp(any()))
                    .willThrow(new ConflictException(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN));

            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("STAFF_NICKNAME_ALREADY_TAKEN", "이미 사용 중인 닉네임입니다."));
        }
    }

    @Nested
    @DisplayName("이메일 인증 확인")
    class EmailVerification {

        @Test
        @DisplayName("링크 값으로 인증을 마치면 204다")
        void returnsNoContent() throws Exception {
            mockMvc.perform(verificationRequest("{\"token\":\"staffAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                    .andExpect(status().isNoContent());

            ArgumentCaptor<VerifyStaffEmailCommand> command = ArgumentCaptor.forClass(VerifyStaffEmailCommand.class);
            verify(verifyStaffEmailUseCase).verifyEmail(command.capture());
            assertThat(command.getValue().token()).isEqualTo("staffAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        }

        @Test
        @DisplayName("링크 값이 비었으면 400이다")
        void rejectsBlankToken() throws Exception {
            mockMvc.perform(verificationRequest("{\"token\":\" \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("token"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("인증 링크가 올바르지 않습니다."));
        }

        @Test
        @DisplayName("발급한 적 없는 링크면 404다")
        void unknownTokenIsNotFound() throws Exception {
            willThrow(new NotFoundException(AccountErrorCode.EMAIL_VERIFICATION_LINK_NOT_FOUND))
                    .given(verifyStaffEmailUseCase).verifyEmail(any());

            mockMvc.perform(verificationRequest("{\"token\":\"unknownAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("EMAIL_VERIFICATION_LINK_NOT_FOUND", "인증 링크가 올바르지 않습니다."));
        }

        @Test
        @DisplayName("유효 시간이 지난 링크면 409다")
        void expiredTokenIsConflict() throws Exception {
            willThrow(new RuleViolationException(AccountErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED))
                    .given(verifyStaffEmailUseCase).verifyEmail(any());

            mockMvc.perform(verificationRequest("{\"token\":\"staffAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EMAIL_VERIFICATION_LINK_EXPIRED", "인증 링크의 유효 시간이 지났습니다."));
        }
    }

    @Nested
    @DisplayName("랜덤 닉네임 — [랜덤 생성] 버튼")
    class RandomNickname {

        @Test
        @DisplayName("로그인·CSRF 토큰 없이 GET으로 받는다 — 가입 화면에서 쓴다")
        void returnsNicknameWithoutLogin() throws Exception {
            given(getRandomNicknameUseCase.getRandomNickname()).willReturn(new GetRandomNicknameResult("조용한고래"));

            mockMvc.perform(get(RANDOM_NICKNAME_PATH))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"nickname\":\"조용한고래\"}", JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("조합을 모두 쓰고 있으면 409다")
        void exhaustedIsConflict() throws Exception {
            given(getRandomNicknameUseCase.getRandomNickname())
                    .willThrow(new RuleViolationException(AccountErrorCode.RANDOM_NICKNAME_EXHAUSTED));

            mockMvc.perform(get(RANDOM_NICKNAME_PATH))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("RANDOM_NICKNAME_EXHAUSTED", "지금은 랜덤 닉네임을 만들 수 없습니다. 닉네임을 직접 정해 주세요."));
        }
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "박정훈");
        body.put("nickname", "조용한고래");
        body.put("email", "junghoon@example.com");
        body.put("password", "junghoon1234");
        return body;
    }

    private MockHttpServletRequestBuilder signUpRequest(Map<String, Object> body) {
        return post(SIGNUP_PATH).contentType(MediaType.APPLICATION_JSON).content(json(body)).with(csrf());
    }

    private MockHttpServletRequestBuilder verificationRequest(String body) {
        return post(VERIFICATION_PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
    }

    private String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }
}
