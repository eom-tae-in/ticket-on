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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.config.ValidationClockConfiguration;
import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.SignUpAudienceCommand;
import com.ticketon.application.account.port.in.SignUpAudienceResult;
import com.ticketon.application.account.port.in.SignUpAudienceUseCase;
import com.ticketon.application.account.port.in.VerifyAudienceEmailCommand;
import com.ticketon.application.account.port.in.VerifyAudienceEmailUseCase;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.ConsentItem;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관객 가입·이메일 인증 API — 입력 검증(400), Request → Command 변환, 도메인 오류 → 응답을 본다. 유스케이스는 흉내 낸다. DB 없이 돈다.
 */
@WebMvcTest(controllers = AudienceSignUpController.class)
@Import({SecurityConfiguration.class, ValidationClockConfiguration.class})
@DisplayName("관객 가입·이메일 인증 API")
class AudienceSignUpControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 10, 0);

    private static final String SIGNUP_PATH = "/api/auth/audience/signup";

    private static final String VERIFICATION_PATH = "/api/auth/audience/email-verification";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    /** 서비스 기준 시계 자리 — 입력 검증의 '오늘'도 이 시계를 본다(ValidationClockConfiguration). */
    @TestBean(name = "clock")
    private MutableClock clock;

    @MockitoBean
    private SignUpAudienceUseCase signUpAudienceUseCase;

    @MockitoBean
    private VerifyAudienceEmailUseCase verifyAudienceEmailUseCase;

    static MutableClock clock() {
        return MutableClock.at(NOW);
    }

    @BeforeEach
    void resetClock() {
        clock.moveTo(NOW);
    }

    @Nested
    @DisplayName("직접 가입")
    class SignUp {

        @BeforeEach
        void signUpSucceeds() {
            given(signUpAudienceUseCase.signUp(any())).willReturn(new SignUpAudienceResult(7, "seoyun@example.com"));
        }

        @Test
        @DisplayName("로그인 없이 가입하면 201과 인증 링크를 보낸 이메일을 돌려준다 — 계정 id는 내보내지 않는다")
        void returnsCreatedWithEmail() throws Exception {
            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isCreated())
                    .andExpect(content().json("{\"email\":\"seoyun@example.com\"}", true));
        }

        @Test
        @DisplayName("입력을 가입 정보·비밀번호·동의 항목으로 바꿔 유스케이스에 넘긴다 — 번호는 숫자만, 이메일은 앞뒤 공백을 걷는다")
        void convertsRequestToCommand() throws Exception {
            Map<String, Object> body = validBody();
            body.put("email", "  seoyun@example.com ");
            body.put("marketingAgreed", true);

            mockMvc.perform(signUpRequest(body)).andExpect(status().isCreated());

            ArgumentCaptor<SignUpAudienceCommand> command = ArgumentCaptor.forClass(SignUpAudienceCommand.class);
            verify(signUpAudienceUseCase).signUp(command.capture());
            assertThat(command.getValue().personalInformation()).isEqualTo(AudiencePersonalInformation.of("한서윤",
                    LocalDate.of(1998, 4, 2), "seoyun@example.com", "01012345678"));
            assertThat(command.getValue().password().value()).isEqualTo("seoyun1234");
            assertThat(command.getValue().consentChoices().agreedItems())
                    .containsExactlyInAnyOrder(ConsentItem.values());
        }

        @Test
        @DisplayName("동의하지 않은 항목은 동의 항목에서 빠진다 — 필수 동의 누락은 가입 규칙이 본다")
        void uncheckedConsentsAreNotAgreed() throws Exception {
            Map<String, Object> body = validBody();
            body.put("minimumAgeConfirmed", false);

            mockMvc.perform(signUpRequest(body)).andExpect(status().isCreated());

            ArgumentCaptor<SignUpAudienceCommand> command = ArgumentCaptor.forClass(SignUpAudienceCommand.class);
            verify(signUpAudienceUseCase).signUp(command.capture());
            assertThat(command.getValue().consentChoices().agreedItems())
                    .containsExactlyInAnyOrder(ConsentItem.TERMS_OF_SERVICE, ConsentItem.PERSONAL_INFORMATION_COLLECTION);
        }

        @Test
        @DisplayName("오늘 태어났다고 적어도 입력 검증은 통과한다 — 만 14세 미만 판단은 가입 규칙이 한다")
        void birthDateTodayPassesValidation() throws Exception {
            Map<String, Object> body = validBody();
            body.put("birthDate", "2026-10-15");

            mockMvc.perform(signUpRequest(body)).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("동의 칸을 빼고 보내면 본문을 읽지 못해 400이다 — 네 칸 모두 명세에서 필수라 화면은 늘 보낸다")
        void absentConsentIsUnreadable() throws Exception {
            Map<String, Object> body = validBody();
            body.remove("marketingAgreed");

            mockMvc.perform(signUpRequest(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(errorBody("INVALID_INPUT", "입력한 내용을 다시 확인해 주세요."));

            verify(signUpAudienceUseCase, never()).signUp(any());
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(SIGNUP_PATH).contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                    .andExpect(status().isForbidden());

            verify(signUpAudienceUseCase, never()).signUp(any());
        }
    }

    @Nested
    @DisplayName("입력 검증 — 400 INVALID_INPUT, 칸마다 문구")
    class Validation {

        @ParameterizedTest(name = "{0} = \"{1}\" → {2}")
        @CsvSource(delimiter = '|', value = {
                "name        | '   '                 | 이름을 입력해 주세요.",
                "email       | ''                    | 이메일을 입력해 주세요.",
                "email       | seoyun                | 이메일 주소를 확인해 주세요.",
                "email       | seo yun@example.com   | 이메일 주소를 확인해 주세요.",
                "phoneNumber | ''                    | 전화번호를 입력해 주세요.",
                "phoneNumber | 011-123-4567          | 010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.",
                "phoneNumber | 010-1234-567          | 010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.",
                "password    | abc1234               | 비밀번호는 8자 이상 입력해 주세요.",
                "password    | ''                    | 비밀번호는 8자 이상 입력해 주세요.",
                "birthDate   | 2026-10-16            | 생년월일을 확인해 주세요."})
        @DisplayName("칸이 비었거나 형식이 틀리면 그 칸과 문구를 알려 주고 유스케이스를 부르지 않는다")
        void rejectsInvalidField(String field, String value, String message) throws Exception {
            Map<String, Object> body = validBody();
            body.put(field, value);

            expectInvalidField(body, field, message);
        }

        @ParameterizedTest(name = "{0} 없음 → {1}")
        @CsvSource(delimiter = '|', value = {
                "name        | 이름을 입력해 주세요.",
                "birthDate   | 생년월일을 입력해 주세요.",
                "email       | 이메일을 입력해 주세요.",
                "phoneNumber | 전화번호를 입력해 주세요.",
                "password    | 비밀번호를 입력해 주세요."})
        @DisplayName("칸이 아예 없으면 '입력해 주세요'다")
        void rejectsMissingField(String field, String message) throws Exception {
            Map<String, Object> body = validBody();
            body.remove(field);

            expectInvalidField(body, field, message);
        }

        @Test
        @DisplayName("'오늘'은 서버 시계가 아니라 서비스 기준 시계(한국 시각)로 정한다 — 시계가 2020-01-01이면 2021년생은 미래다")
        void todayFollowsServiceClock() throws Exception {
            clock.moveTo(LocalDateTime.of(2020, 1, 1, 9, 0));
            Map<String, Object> body = validBody();
            body.put("birthDate", "2021-01-01");

            expectInvalidField(body, "birthDate", "생년월일을 확인해 주세요.");
        }

        @Test
        @DisplayName("이메일 주소는 254자까지다")
        void rejectsTooLongEmail() throws Exception {
            Map<String, Object> body = validBody();
            body.put("email", "a".repeat(243) + "@example.com");

            expectInvalidField(body, "email", "이메일 주소를 확인해 주세요.");
        }

        private void expectInvalidField(Map<String, Object> body, String field, String message) throws Exception {
            mockMvc.perform(signUpRequest(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(message));

            verify(signUpAudienceUseCase, never()).signUp(any());
        }
    }

    @Nested
    @DisplayName("가입 규칙에 막히면 409, 문구는 도메인 오류 그대로")
    class Rejections {

        @Test
        @DisplayName("가입된 번호·차단된 번호 — docs/01 문구")
        void phoneNumberAlreadyRegistered() throws Exception {
            given(signUpAudienceUseCase.signUp(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED));

            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("PHONE_NUMBER_ALREADY_REGISTERED", "이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요."));
        }

        @Test
        @DisplayName("동시 가입 경합에서 진 쪽도 같은 코드·문구다")
        void signupRaceLoserGetsSameCode() throws Exception {
            given(signUpAudienceUseCase.signUp(any()))
                    .willThrow(new ConflictException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED));

            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("PHONE_NUMBER_ALREADY_REGISTERED", "이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요."));
        }

        @Test
        @DisplayName("가입된 이메일")
        void emailAlreadyRegistered() throws Exception {
            given(signUpAudienceUseCase.signUp(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.AUDIENCE_EMAIL_ALREADY_REGISTERED));

            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("AUDIENCE_EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다."));
        }

        @Test
        @DisplayName("만 14세 미만")
        void underMinimumAge() throws Exception {
            given(signUpAudienceUseCase.signUp(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE));

            mockMvc.perform(signUpRequest(validBody()))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("AUDIENCE_UNDER_MINIMUM_AGE", "만 14세 미만은 가입할 수 없습니다."));
        }
    }

    @Nested
    @DisplayName("이메일 인증 확인")
    class EmailVerification {

        @Test
        @DisplayName("링크 값으로 인증을 마치면 204다")
        void returnsNoContent() throws Exception {
            mockMvc.perform(verificationRequest("{\"token\":\"verifyAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                    .andExpect(status().isNoContent());

            ArgumentCaptor<VerifyAudienceEmailCommand> command = ArgumentCaptor.forClass(VerifyAudienceEmailCommand.class);
            verify(verifyAudienceEmailUseCase).verifyEmail(command.capture());
            assertThat(command.getValue().token()).isEqualTo("verifyAAAAAAAAAAAAAAAAAAAAAAAAAA");
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
                    .given(verifyAudienceEmailUseCase).verifyEmail(any());

            mockMvc.perform(verificationRequest("{\"token\":\"unknownAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("EMAIL_VERIFICATION_LINK_NOT_FOUND", "인증 링크가 올바르지 않습니다."));
        }

        @Test
        @DisplayName("유효 시간이 지났으면 409다")
        void expiredTokenIsConflict() throws Exception {
            willThrow(new RuleViolationException(AccountErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED))
                    .given(verifyAudienceEmailUseCase).verifyEmail(any());

            mockMvc.perform(verificationRequest("{\"token\":\"verifyAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EMAIL_VERIFICATION_LINK_EXPIRED", "인증 링크의 유효 시간이 지났습니다."));
        }

        @Test
        @DisplayName("이미 인증했으면 409다")
        void alreadyVerifiedIsConflict() throws Exception {
            willThrow(new RuleViolationException(AccountErrorCode.EMAIL_ALREADY_VERIFIED))
                    .given(verifyAudienceEmailUseCase).verifyEmail(any());

            mockMvc.perform(verificationRequest("{\"token\":\"verifyAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EMAIL_ALREADY_VERIFIED", "이미 이메일 인증을 마친 계정입니다."));
        }

        private MockHttpServletRequestBuilder verificationRequest(String body) {
            return post(VERIFICATION_PATH).contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
        }
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "한서윤");
        body.put("birthDate", "1998-04-02");
        body.put("email", "seoyun@example.com");
        body.put("phoneNumber", "010-1234-5678");
        body.put("password", "seoyun1234");
        body.put("termsOfServiceAgreed", true);
        body.put("personalInformationCollectionAgreed", true);
        body.put("minimumAgeConfirmed", true);
        body.put("marketingAgreed", false);
        return body;
    }

    private MockHttpServletRequestBuilder signUpRequest(Map<String, Object> body) {
        return post(SIGNUP_PATH).contentType(MediaType.APPLICATION_JSON).content(json(body)).with(csrf());
    }

    private String json(Map<String, Object> body) {
        return jsonMapper.writeValueAsString(body);
    }
}
