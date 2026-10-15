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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberCommand;
import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberResult;
import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberUseCase;
import com.ticketon.application.account.port.in.GetMyAudienceAccountUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 관객 전화번호 변경 API — 보안 필터를 건 채로 관객 영역인지, 요청이 Command로 바뀌는지, 입력 검증(400)과 규칙 위반·경합(409)을 본다.
 * 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = AudienceAccountController.class)
@Import(SecurityConfiguration.class)
@DisplayName("관객 전화번호 변경 API")
class AudienceAccountControllerTest {

    private static final String PHONE_NUMBER_PATH = "/api/audience/account/phone-number";

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, 7);

    private static final String ALREADY_REGISTERED_MESSAGE = "이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyAudienceAccountUseCase getMyAudienceAccountUseCase;

    @MockitoBean
    private ChangeAudiencePhoneNumberUseCase changeAudiencePhoneNumberUseCase;

    @Test
    @DisplayName("로그인한 관객과 숫자만 남긴 새 번호를 Command로 넘기고, 저장된 번호를 돌려준다")
    void convertsRequestToCommand() throws Exception {
        given(changeAudiencePhoneNumberUseCase.changePhoneNumber(any()))
                .willReturn(new ChangeAudiencePhoneNumberResult("01098765432"));

        mockMvc.perform(change("{\"phoneNumber\": \"010-9876-5432\"}").with(loggedInAs(SEOYUN)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"phoneNumber\": \"01098765432\"}", JsonCompareMode.STRICT));

        ArgumentCaptor<ChangeAudiencePhoneNumberCommand> command =
                ArgumentCaptor.forClass(ChangeAudiencePhoneNumberCommand.class);
        verify(changeAudiencePhoneNumberUseCase).changePhoneNumber(command.capture());
        assertThat(command.getValue())
                .isEqualTo(new ChangeAudiencePhoneNumberCommand(SEOYUN, PhoneNumber.of("01098765432")));
    }

    @Test
    @DisplayName("다른 계정이 쓰는 번호면 409에 docs/01 문구 그대로다")
    void numberOfAnotherAccountIsConflict() throws Exception {
        given(changeAudiencePhoneNumberUseCase.changePhoneNumber(any()))
                .willThrow(new RuleViolationException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED));

        mockMvc.perform(change("{\"phoneNumber\": \"010-9876-5432\"}").with(loggedInAs(SEOYUN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("PHONE_NUMBER_ALREADY_REGISTERED", ALREADY_REGISTERED_MESSAGE));
    }

    @Test
    @DisplayName("거의 동시에 같은 번호를 먼저 저장한 요청에 지면 같은 409 문구다")
    void lostRaceIsSameConflict() throws Exception {
        given(changeAudiencePhoneNumberUseCase.changePhoneNumber(any()))
                .willThrow(new ConflictException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED, "경합"));

        mockMvc.perform(change("{\"phoneNumber\": \"010-9876-5432\"}").with(loggedInAs(SEOYUN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("PHONE_NUMBER_ALREADY_REGISTERED", ALREADY_REGISTERED_MESSAGE));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"02-123-4567", "010-1234-567", "abc"})
    @DisplayName("010으로 시작하는 11자리가 아니면 400이고 유스케이스를 부르지 않는다")
    void invalidFormatIsBadRequest(String phoneNumber) throws Exception {
        mockMvc.perform(change("{\"phoneNumber\": \"" + phoneNumber + "\"}").with(loggedInAs(SEOYUN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phoneNumber"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요."));

        verify(changeAudiencePhoneNumberUseCase, never()).changePhoneNumber(any());
    }

    @Test
    @DisplayName("번호가 비면 400 — 가입과 같은 문구다")
    void blankIsBadRequest() throws Exception {
        mockMvc.perform(change("{\"phoneNumber\": \"\"}").with(loggedInAs(SEOYUN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("전화번호를 입력해 주세요."));

        verify(changeAudiencePhoneNumberUseCase, never()).changePhoneNumber(any());
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(change("{\"phoneNumber\": \"010-9876-5432\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"STAFF", "ADMIN", "CHECKIN"})
    @DisplayName("관객이 아닌 계정으로는 403이다")
    void otherAccountKindIsDenied(AccountKind kind) throws Exception {
        mockMvc.perform(change("{\"phoneNumber\": \"010-9876-5432\"}").with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verify(changeAudiencePhoneNumberUseCase, never()).changePhoneNumber(any());
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 403이다")
    void requiresCsrfToken() throws Exception {
        mockMvc.perform(put(PHONE_NUMBER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"010-9876-5432\"}")
                        .with(loggedInAs(SEOYUN)))
                .andExpect(status().isForbidden());

        verify(changeAudiencePhoneNumberUseCase, never()).changePhoneNumber(any());
    }

    private MockHttpServletRequestBuilder change(String body) {
        return put(PHONE_NUMBER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf());
    }
}
