package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.config.WebAddressConfiguration;
import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.LinkOAuthUseCase;
import com.ticketon.application.account.port.in.LogInWithOAuthUseCase;
import com.ticketon.application.account.port.in.SignUpWithOAuthUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 카카오·구글 키가 없을 때 — 서버(보안 필터·OAuth 설정)는 그대로 뜨고, 그 제공자의 로그인 시작 주소만 없는 주소(404)다.
 * 개발 기기의 환경 변수와 상관없이 키를 비운다.
 */
@WebMvcTest(controllers = AudienceOAuthController.class)
@Import({SecurityConfiguration.class, AudienceOAuthSecurityConfiguration.class, AudienceOAuthClientConfiguration.class,
        AudienceOAuthLoginHandler.class, WebAddressConfiguration.class})
@TestPropertySource(properties = {
        "ticketon.oauth.kakao.client-id=", "ticketon.oauth.kakao.client-secret=",
        "ticketon.oauth.google.client-id=", "ticketon.oauth.google.client-secret="})
@DisplayName("카카오·구글 키가 없을 때")
class AudienceOAuthWithoutKeysTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogInWithOAuthUseCase logInWithOAuthUseCase;

    @MockitoBean
    private SignUpWithOAuthUseCase signUpWithOAuthUseCase;

    @MockitoBean
    private LinkOAuthUseCase linkOAuthUseCase;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"kakao", "google"})
    @DisplayName("로그인 시작 주소는 404다 — 제공자로 보내지 않는다")
    void startIsNotFound(String registrationId) throws Exception {
        mockMvc.perform(get("/api/auth/audience/oauth2/authorization/" + registrationId))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("NOT_FOUND", "요청한 주소를 찾을 수 없습니다."));
    }
}
