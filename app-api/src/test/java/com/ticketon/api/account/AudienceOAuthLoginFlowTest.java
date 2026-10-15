package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.config.WebAddressConfiguration;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberUseCase;
import com.ticketon.application.account.port.in.GetMyAudienceAccountQuery;
import com.ticketon.application.account.port.in.GetMyAudienceAccountResult;
import com.ticketon.application.account.port.in.GetMyAudienceAccountUseCase;
import com.ticketon.application.account.port.in.LinkOAuthCommand;
import com.ticketon.application.account.port.in.LinkOAuthUseCase;
import com.ticketon.application.account.port.in.LogInWithOAuthCommand;
import com.ticketon.application.account.port.in.LogInWithOAuthResult;
import com.ticketon.application.account.port.in.LogInWithOAuthUseCase;
import com.ticketon.application.account.port.in.SignUpWithOAuthCommand;
import com.ticketon.application.account.port.in.SignUpWithOAuthResult;
import com.ticketon.application.account.port.in.SignUpWithOAuthUseCase;
import com.ticketon.application.account.port.in.UnlinkOAuthUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.RuleViolationException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관객 카카오·구글 로그인 흐름 — 보안 필터를 건 채 로그인 시작 → 가짜 제공자(토큰·사용자 정보) → 콜백 → 결과 → 가입·연결을 끝까지 돌린다.
 * 실제 카카오·구글 대신 {@link FakeOAuthProvider}가 답하고, 유스케이스는 흉내 낸다. 세션은 MockMvc의 세션으로 흉내 내고, DB에 저장되는 모습은
 * 통합 테스트(AudienceOAuthIntegrationTest)가 본다.
 * <p>
 * 브라우저가 제공자 화면에 다녀오는 부분은, 로그인 시작이 돌려준 제공자 주소에서 state를 꺼내 제공자가 돌려보내는 것처럼 콜백을 부르는 것으로
 * 흉내 낸다.
 */
@WebMvcTest(controllers = {AudienceOAuthController.class, AudienceOAuthLinkController.class, AudienceAccountController.class})
@Import({SecurityConfiguration.class, AudienceOAuthSecurityConfiguration.class, AudienceOAuthClientConfiguration.class,
        AudienceOAuthLoginHandler.class, WebAddressConfiguration.class, LoginTestController.class})
@DisplayName("관객 카카오·구글 로그인 흐름 (가짜 제공자)")
class AudienceOAuthLoginFlowTest {

    private static final FakeOAuthProvider PROVIDER = FakeOAuthProvider.start();

    private static final String AUTHORIZATION_PATH = "/api/auth/audience/oauth2/authorization/";

    private static final String CALLBACK_PATH = "/api/auth/audience/oauth2/callback/";

    private static final String RESULT_PATH = "/api/auth/audience/oauth2/result";

    private static final String SIGNUP_PATH = "/api/auth/audience/oauth2/signup";

    private static final String LINKS_PATH = "/api/audience/account/oauth-links/";

    private static final String ACCOUNT_PATH = "/api/audience/account";

    private static final String RESULT_PAGE = "http://localhost:3000/oauth/result";

    private static final String GOOGLE_USER = """
            {"sub":"109876543210","name":"한서윤","email":"seoyun@gmail.com","email_verified":true}""";

    private static final String KAKAO_USER_WITH_UNVERIFIED_EMAIL = """
            {"id":3141592653,"kakao_account":{"email":"seoyun@kakao.com","is_email_valid":true,"is_email_verified":false,
             "profile":{"nickname":"서윤이"}}}""";

    private static final OAuthIdentity SEOYUN_GOOGLE = new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210");

    private static final OAuthIdentity SEOYUN_KAKAO = new OAuthIdentity(OAuthProvider.KAKAO, "3141592653");

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, 7);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private LogInWithOAuthUseCase logInWithOAuthUseCase;

    @MockitoBean
    private SignUpWithOAuthUseCase signUpWithOAuthUseCase;

    @MockitoBean
    private LinkOAuthUseCase linkOAuthUseCase;

    @MockitoBean
    private UnlinkOAuthUseCase unlinkOAuthUseCase;

    @MockitoBean
    private GetMyAudienceAccountUseCase getMyAudienceAccountUseCase;

    @MockitoBean
    private ChangeAudiencePhoneNumberUseCase changeAudiencePhoneNumberUseCase;

    @DynamicPropertySource
    static void useFakeProvider(DynamicPropertyRegistry registry) {
        PROVIDER.registerProperties(registry);
    }

    @AfterAll
    static void stopFakeProvider() {
        PROVIDER.close();
    }

    @Nested
    @DisplayName("로그인 시작")
    class Start {

        @Test
        @DisplayName("카카오 로그인 시작은 제공자 로그인·동의 화면으로 보낸다 — 클라이언트 id, 콜백 주소, 동의 범위, state를 싣는다")
        void redirectsToProvider() throws Exception {
            MvcResult result = mockMvc.perform(get(AUTHORIZATION_PATH + "kakao").session(new MockHttpSession()))
                    .andExpect(status().isFound())
                    .andReturn();

            UriComponents location = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build();
            assertThat(location.getScheme() + "://" + location.getHost() + ":" + location.getPort() + location.getPath())
                    .isEqualTo(PROVIDER.baseUrl() + "/kakao/authorize");
            assertThat(location.getQueryParams().getFirst("client_id")).isEqualTo(FakeOAuthProvider.KAKAO_CLIENT_ID);
            assertThat(location.getQueryParams().getFirst("response_type")).isEqualTo("code");
            assertThat(decoded(location.getQueryParams().getFirst("redirect_uri")))
                    .isEqualTo("http://localhost/api/auth/audience/oauth2/callback/kakao");
            assertThat(location.getQueryParams().getFirst("scope")).isEqualTo("account_email");
            assertThat(location.getQueryParams().getFirst("state")).isNotBlank();
        }

        @Test
        @DisplayName("구글은 OpenID Connect 없이 profile·email 범위만 받는다")
        void googleScopes() throws Exception {
            MvcResult result = mockMvc.perform(get(AUTHORIZATION_PATH + "google").session(new MockHttpSession()))
                    .andExpect(status().isFound())
                    .andReturn();

            assertThat(decoded(UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build()
                    .getQueryParams().getFirst("scope"))).isEqualTo("profile email");
        }

        @Test
        @DisplayName("모르는 제공자는 없는 주소(404)다")
        void unknownProvider() throws Exception {
            mockMvc.perform(get(AUTHORIZATION_PATH + "naver"))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("NOT_FOUND", "요청한 주소를 찾을 수 없습니다."));
        }
    }

    @Nested
    @DisplayName("연결되지 않은 제공자 계정 — 추가 정보 입력을 세션에 두고 가입")
    class NewAudience {

        @Test
        @DisplayName("구글이 확인한 이메일이면 결과는 SIGNUP_REQUIRED, 이름·이메일을 미리 채우고 이메일 칸은 받지 않는다 — 로그인되지 않는다")
        void googleSignUpRequired() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.SignUpRequired());
            MockHttpSession session = new MockHttpSession();

            completeProviderLogin(session, "google", "google-code-1", GOOGLE_USER, "/events/12/booking")
                    .andExpect(redirectedUrl(RESULT_PAGE));

            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("SIGNUP_REQUIRED"))
                    .andExpect(jsonPath("$.provider").value("GOOGLE"))
                    .andExpect(jsonPath("$.returnPath").value("/events/12/booking"))
                    .andExpect(jsonPath("$.pendingSignUp.name").value("한서윤"))
                    .andExpect(jsonPath("$.pendingSignUp.email").value("seoyun@gmail.com"))
                    .andExpect(jsonPath("$.pendingSignUp.emailRequired").value(false))
                    .andExpect(jsonPath("$.error").doesNotExist());
            ArgumentCaptor<LogInWithOAuthCommand> command = ArgumentCaptor.forClass(LogInWithOAuthCommand.class);
            verify(logInWithOAuthUseCase).logIn(command.capture());
            assertThat(command.getValue().identity()).isEqualTo(SEOYUN_GOOGLE);
            assertThat(command.getValue().providerVerifiedEmail()).contains(EmailAddress.of("seoyun@gmail.com"));
            assertNotLoggedIn(session);
        }

        @Test
        @DisplayName("구글 토큰 요청은 Basic 인증으로 클라이언트를 밝힌다")
        void googleTokenRequestUsesBasicAuthentication() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.SignUpRequired());

            completeProviderLogin(new MockHttpSession(), "google", "google-code-basic", GOOGLE_USER, null);

            assertThat(PROVIDER.tokenRequests()).filteredOn(request -> "google-code-basic".equals(request.form().get("code")))
                    .singleElement()
                    .satisfies(request -> {
                        assertThat(request.authorization()).startsWith("Basic ");
                        assertThat(request.form()).doesNotContainKey("client_secret");
                    });
        }

        @Test
        @DisplayName("카카오가 확인하지 않은 이메일은 확인된 이메일로 넘기지 않고, 이메일 칸을 받는다(미리 채움) — 토큰 요청은 본문에 시크릿을 싣는다")
        void kakaoUnverifiedEmail() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.SignUpRequired());
            MockHttpSession session = new MockHttpSession();

            completeProviderLogin(session, "kakao", "kakao-code-1", KAKAO_USER_WITH_UNVERIFIED_EMAIL, null);

            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(jsonPath("$.outcome").value("SIGNUP_REQUIRED"))
                    .andExpect(jsonPath("$.provider").value("KAKAO"))
                    .andExpect(jsonPath("$.returnPath").value("/"))
                    .andExpect(jsonPath("$.pendingSignUp.name").doesNotExist())
                    .andExpect(jsonPath("$.pendingSignUp.email").value("seoyun@kakao.com"))
                    .andExpect(jsonPath("$.pendingSignUp.emailRequired").value(true));
            ArgumentCaptor<LogInWithOAuthCommand> command = ArgumentCaptor.forClass(LogInWithOAuthCommand.class);
            verify(logInWithOAuthUseCase).logIn(command.capture());
            assertThat(command.getValue().identity()).isEqualTo(SEOYUN_KAKAO);
            assertThat(command.getValue().providerVerifiedEmail()).isEmpty();
            assertThat(PROVIDER.tokenRequests()).filteredOn(request -> "kakao-code-1".equals(request.form().get("code")))
                    .singleElement()
                    .satisfies(request -> assertThat(request.form()).containsEntry("client_id", FakeOAuthProvider.KAKAO_CLIENT_ID)
                            .containsEntry("client_secret", FakeOAuthProvider.KAKAO_CLIENT_SECRET));
        }

        @Test
        @DisplayName("추가 정보를 내면 세션의 제공자 계정과 제공자가 확인한 이메일로 가입하고 바로 로그인된다 — 요청에 적은 이메일은 보지 않는다")
        void signUpWithProviderVerifiedEmail() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.SignUpRequired());
            given(signUpWithOAuthUseCase.signUp(any())).willReturn(new SignUpWithOAuthResult(7, "seoyun@gmail.com", true));
            MockHttpSession session = new MockHttpSession();
            completeProviderLogin(session, "google", "google-code-2", GOOGLE_USER, "/events/12/booking");
            String sessionIdBeforeSignUp = session.getId();

            mockMvc.perform(signUpRequest(signUpBody("someone-else@example.com")).session(session))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.loggedIn").value(true))
                    .andExpect(jsonPath("$.email").value("seoyun@gmail.com"))
                    .andExpect(jsonPath("$.returnPath").value("/events/12/booking"));

            ArgumentCaptor<SignUpWithOAuthCommand> command = ArgumentCaptor.forClass(SignUpWithOAuthCommand.class);
            verify(signUpWithOAuthUseCase).signUp(command.capture());
            assertThat(command.getValue().identity()).isEqualTo(SEOYUN_GOOGLE);
            assertThat(command.getValue().emailVerifiedByProvider()).isTrue();
            assertThat(command.getValue().personalInformation().email()).isEqualTo(EmailAddress.of("seoyun@gmail.com"));
            assertThat(command.getValue().personalInformation().birthDate()).isEqualTo(LocalDate.of(1998, 4, 2));
            assertThat(command.getValue().consentChoices().missingRequiredItems()).isEmpty();
            assertThat(session.getId()).isNotEqualTo(sessionIdBeforeSignUp);
            assertLoggedInAs(session, SEOYUN);
            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("OAUTH_LOGIN_NOT_IN_PROGRESS", OAuthLoginErrorCode.OAUTH_LOGIN_NOT_IN_PROGRESS.message()));
        }

        @Test
        @DisplayName("같은 대기로 두 번 가입할 수 없다 — 가입을 마치면 추가 정보 입력 대기가 지워진다")
        void signUpOnlyOnce() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.SignUpRequired());
            given(signUpWithOAuthUseCase.signUp(any())).willReturn(new SignUpWithOAuthResult(7, "seoyun@gmail.com", true));
            MockHttpSession session = new MockHttpSession();
            completeProviderLogin(session, "google", "google-code-3", GOOGLE_USER, null);
            mockMvc.perform(signUpRequest(signUpBody(null)).session(session)).andExpect(status().isCreated());

            mockMvc.perform(signUpRequest(signUpBody(null)).session(session))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("OAUTH_LOGIN_NOT_IN_PROGRESS", OAuthLoginErrorCode.OAUTH_LOGIN_NOT_IN_PROGRESS.message()));
        }

        @Test
        @DisplayName("제공자가 확인한 이메일이 없으면 적은 이메일로 가입하고, 인증 메일을 보냈으므로 로그인되지 않는다")
        void signUpWithEnteredEmail() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.SignUpRequired());
            given(signUpWithOAuthUseCase.signUp(any())).willReturn(new SignUpWithOAuthResult(8, "seoyun@example.com", false));
            MockHttpSession session = new MockHttpSession();
            completeProviderLogin(session, "kakao", "kakao-code-2", KAKAO_USER_WITH_UNVERIFIED_EMAIL, null);

            mockMvc.perform(signUpRequest(signUpBody("seoyun@example.com")).session(session))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.loggedIn").value(false))
                    .andExpect(jsonPath("$.email").value("seoyun@example.com"));

            ArgumentCaptor<SignUpWithOAuthCommand> command = ArgumentCaptor.forClass(SignUpWithOAuthCommand.class);
            verify(signUpWithOAuthUseCase).signUp(command.capture());
            assertThat(command.getValue().identity()).isEqualTo(SEOYUN_KAKAO);
            assertThat(command.getValue().emailVerifiedByProvider()).isFalse();
            assertThat(command.getValue().personalInformation().email()).isEqualTo(EmailAddress.of("seoyun@example.com"));
            assertNotLoggedIn(session);
        }

        @Test
        @DisplayName("제공자가 확인한 이메일이 없는데 이메일을 비우면 400이고 이메일 칸에 문구가 붙는다")
        void enteredEmailIsRequired() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.SignUpRequired());
            MockHttpSession session = new MockHttpSession();
            completeProviderLogin(session, "kakao", "kakao-code-3", KAKAO_USER_WITH_UNVERIFIED_EMAIL, null);

            mockMvc.perform(signUpRequest(signUpBody(null)).session(session))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("이메일을 입력해 주세요."));
            verifyNoInteractions(signUpWithOAuthUseCase);
        }

        @Test
        @DisplayName("가입 규칙에 걸리면 409이고(번호 하나에 계정 하나 등) 추가 정보 입력 대기는 남는다 — 고쳐서 다시 낼 수 있다")
        void ruleViolationKeepsPendingSignUp() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.SignUpRequired());
            given(signUpWithOAuthUseCase.signUp(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED));
            MockHttpSession session = new MockHttpSession();
            completeProviderLogin(session, "google", "google-code-4", GOOGLE_USER, null);

            mockMvc.perform(signUpRequest(signUpBody(null)).session(session))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("PHONE_NUMBER_ALREADY_REGISTERED", "이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요."));
            mockMvc.perform(get(RESULT_PATH).session(session)).andExpect(jsonPath("$.outcome").value("SIGNUP_REQUIRED"));
        }

        @Test
        @DisplayName("추가 정보 입력 대기가 없는 세션의 가입은 404다")
        void signUpWithoutPendingSignUp() throws Exception {
            mockMvc.perform(signUpRequest(signUpBody("seoyun@example.com")).session(new MockHttpSession()))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("OAUTH_LOGIN_NOT_IN_PROGRESS", OAuthLoginErrorCode.OAUTH_LOGIN_NOT_IN_PROGRESS.message()));
        }
    }

    @Nested
    @DisplayName("연결된 제공자 계정 — 로그인")
    class LinkedAudience {

        @Test
        @DisplayName("결과는 LOGGED_IN이고 관객 주체로 세션이 열린다 — 세션 id가 바뀌고, 시작할 때 보낸 경로로 돌아간다")
        void logsIn() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.LoggedIn(SEOYUN));
            MockHttpSession session = new MockHttpSession();
            String state = startLogin(session, "google", "/events/12/booking");
            String sessionIdBeforeCallback = session.getId();

            callback(session, "google", "google-code-5", GOOGLE_USER, state).andExpect(redirectedUrl(RESULT_PAGE));

            assertThat(session.getId()).isNotEqualTo(sessionIdBeforeCallback);
            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(jsonPath("$.outcome").value("LOGGED_IN"))
                    .andExpect(jsonPath("$.returnPath").value("/events/12/booking"))
                    .andExpect(jsonPath("$.pendingSignUp").doesNotExist());
            assertLoggedInAs(session, SEOYUN);
        }

        @Test
        @DisplayName("같은 사이트 안의 경로가 아닌 돌아갈 경로는 관객 첫 화면(/)으로 바뀐다 — 열린 리다이렉트 방지")
        void rejectsForeignReturnPath() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.LoggedIn(SEOYUN));
            MockHttpSession session = new MockHttpSession();

            completeProviderLogin(session, "google", "google-code-6", GOOGLE_USER, "//evil.example/phishing");

            mockMvc.perform(get(RESULT_PATH).session(session)).andExpect(jsonPath("$.returnPath").value("/"));
        }

        @Test
        @DisplayName("세션에는 제공자 인증 정보가 아니라 관객 주체만 남는다")
        void storesOnlyAudiencePrincipal() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.LoggedIn(SEOYUN));
            MockHttpSession session = new MockHttpSession();

            completeProviderLogin(session, "google", "google-code-7", GOOGLE_USER, null);

            SecurityContext context = (SecurityContext) session.getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertThat(context.getAuthentication().getName()).isEqualTo("AUDIENCE:7");
            assertThat(context.getAuthentication()).isNotInstanceOf(OAuth2AuthenticationToken.class);
        }
    }

    @Nested
    @DisplayName("같은 이메일의 관객 계정 — 자동으로 연결하지 않는다 (docs/01 \"계정 연동\")")
    class SameEmail {

        @Test
        @DisplayName("결과는 FAILED이고 \"기존 계정으로 로그인한 뒤 구글 계정을 연결해 주세요\"를 돌려준다 — 로그인되지 않고 가입 대기도 없다")
        void refusesAutomaticLink() throws Exception {
            given(logInWithOAuthUseCase.logIn(any()))
                    .willThrow(new RuleViolationException(AccountErrorCode.GOOGLE_SIGNUP_EMAIL_ALREADY_REGISTERED));
            MockHttpSession session = new MockHttpSession();

            completeProviderLogin(session, "google", "google-code-8", GOOGLE_USER, null);

            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("FAILED"))
                    .andExpect(jsonPath("$.error.code").value("GOOGLE_SIGNUP_EMAIL_ALREADY_REGISTERED"))
                    .andExpect(jsonPath("$.error.message")
                            .value("이 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인한 뒤 구글 계정을 연결해 주세요."))
                    .andExpect(jsonPath("$.pendingSignUp").doesNotExist());
            assertNotLoggedIn(session);
            mockMvc.perform(signUpRequest(signUpBody(null)).session(session)).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("제공자에서 로그인을 마치지 못함 — 한 가지 문구, 까닭은 로그에만")
    class ProviderFailure {

        @Test
        @DisplayName("관객이 동의를 취소해 제공자가 access_denied로 돌려보내면 FAILED이고 유스케이스를 부르지 않는다")
        void cancelledByAudience() throws Exception {
            MockHttpSession session = new MockHttpSession();
            String state = startLogin(session, "kakao", null);

            mockMvc.perform(get(CALLBACK_PATH + "kakao").param("error", "access_denied").param("state", state).session(session))
                    .andExpect(redirectedUrl(RESULT_PAGE));

            expectLoginFailed(session, "KAKAO");
            verifyNoInteractions(logInWithOAuthUseCase);
        }

        @Test
        @DisplayName("시작할 때와 다른 state로 돌아오면 FAILED다 — 남이 만든 콜백 주소로 로그인되지 않는다")
        void stateMismatch() throws Exception {
            MockHttpSession session = new MockHttpSession();
            startLogin(session, "google", null);
            PROVIDER.willAuthenticate("google-code-9", GOOGLE_USER);

            mockMvc.perform(get(CALLBACK_PATH + "google").param("code", "google-code-9").param("state", "forged-state")
                    .session(session)).andExpect(redirectedUrl(RESULT_PAGE));

            expectLoginFailed(session, "GOOGLE");
            verifyNoInteractions(logInWithOAuthUseCase);
        }

        @Test
        @DisplayName("제공자가 인가 코드를 토큰으로 바꿔 주지 않으면 FAILED다")
        void tokenExchangeFails() throws Exception {
            MockHttpSession session = new MockHttpSession();
            String state = startLogin(session, "google", null);

            mockMvc.perform(get(CALLBACK_PATH + "google").param("code", "unknown-code").param("state", state).session(session))
                    .andExpect(redirectedUrl(RESULT_PAGE));

            expectLoginFailed(session, "GOOGLE");
        }

        @Test
        @DisplayName("로그인을 시작하지 않은 세션으로 콜백이 오면 제공자 없이 FAILED다")
        void callbackWithoutStart() throws Exception {
            MockHttpSession session = new MockHttpSession();

            mockMvc.perform(get(CALLBACK_PATH + "google").param("code", "any").param("state", "any").session(session))
                    .andExpect(redirectedUrl(RESULT_PAGE));

            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(jsonPath("$.outcome").value("FAILED"))
                    .andExpect(jsonPath("$.provider").doesNotExist())
                    .andExpect(jsonPath("$.error.code").value("OAUTH_LOGIN_FAILED"));
        }

        @Test
        @DisplayName("진행 중인 로그인이 없는 세션의 결과는 404다")
        void noResult() throws Exception {
            mockMvc.perform(get(RESULT_PATH))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("OAUTH_LOGIN_NOT_IN_PROGRESS", OAuthLoginErrorCode.OAUTH_LOGIN_NOT_IN_PROGRESS.message()));
        }

        private void expectLoginFailed(MockHttpSession session, String provider) throws Exception {
            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(jsonPath("$.outcome").value("FAILED"))
                    .andExpect(jsonPath("$.provider").value(provider))
                    .andExpect(jsonPath("$.error.code").value("OAUTH_LOGIN_FAILED"))
                    .andExpect(jsonPath("$.error.message").value("로그인을 마치지 못했습니다. 다시 시도해 주세요."));
            assertNotLoggedIn(session);
        }
    }

    @Nested
    @DisplayName("마이페이지 연결 — 로그인한 관객이 직접")
    class Link {

        @Test
        @DisplayName("연결 시작 → 제공자 → 콜백이면 LINKED이고, 로그인한 관객 계정에 그 제공자 계정을 연결한다 — 로그인 주체는 그대로다")
        void linksToLoggedInAccount() throws Exception {
            MockHttpSession session = loggedInSession(SEOYUN);
            String authorizationPath = startLink(session, "KAKAO", "/my/account");

            String state = stateOf(mockMvc.perform(get(authorizationPath).session(session)).andReturn());
            callback(session, "kakao", "kakao-link-1", KAKAO_USER_WITH_UNVERIFIED_EMAIL, state)
                    .andExpect(redirectedUrl(RESULT_PAGE));

            assertThat(authorizationPath).isEqualTo(AUTHORIZATION_PATH + "kakao");
            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(jsonPath("$.outcome").value("LINKED"))
                    .andExpect(jsonPath("$.provider").value("KAKAO"))
                    .andExpect(jsonPath("$.returnPath").value("/my/account"));
            ArgumentCaptor<LinkOAuthCommand> command = ArgumentCaptor.forClass(LinkOAuthCommand.class);
            verify(linkOAuthUseCase).link(command.capture());
            assertThat(command.getValue()).isEqualTo(new LinkOAuthCommand(SEOYUN, SEOYUN_KAKAO));
            verifyNoInteractions(logInWithOAuthUseCase);
            assertLoggedInAs(session, SEOYUN);
        }

        @Test
        @DisplayName("연결 규칙에 걸리면(다른 관객 계정에 연결된 카카오 계정) FAILED와 그 까닭을 돌려주고, 로그인은 그대로다")
        void linkRejected() throws Exception {
            willThrow(new RuleViolationException(AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED))
                    .given(linkOAuthUseCase).link(any());
            MockHttpSession session = loggedInSession(SEOYUN);
            String state = stateOf(mockMvc.perform(get(startLink(session, "KAKAO", null)).session(session)).andReturn());

            callback(session, "kakao", "kakao-link-2", KAKAO_USER_WITH_UNVERIFIED_EMAIL, state);

            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(jsonPath("$.outcome").value("FAILED"))
                    .andExpect(jsonPath("$.error.code").value("OAUTH_IDENTITY_ALREADY_LINKED"))
                    .andExpect(jsonPath("$.error.message").value(AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED.message()));
            assertLoggedInAs(session, SEOYUN);
        }

        @Test
        @DisplayName("연결을 시작한 뒤 로그아웃했다면 돌아와도 연결하지 않고 로그인이 필요하다고 알린다")
        void requiresSameLogin() throws Exception {
            MockHttpSession session = loggedInSession(SEOYUN);
            String state = stateOf(mockMvc.perform(get(startLink(session, "GOOGLE", null)).session(session)).andReturn());
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

            callback(session, "google", "google-link-1", GOOGLE_USER, state);

            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(jsonPath("$.outcome").value("FAILED"))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
            verify(linkOAuthUseCase, never()).link(any());
        }

        @Test
        @DisplayName("연결을 시작한 뒤 같은 브라우저에서 다른 관객으로 다시 로그인했다면 돌아와도 어느 계정에도 연결하지 않는다 — 남의 계정에 내 제공자 계정이 붙지 않게")
        void doesNotLinkToAccountLoggedInAfterStart() throws Exception {
            AccountRef otherAudience = AccountRef.of(AccountKind.AUDIENCE, 8);
            MockHttpSession session = loggedInSession(SEOYUN);
            String state = stateOf(mockMvc.perform(get(startLink(session, "GOOGLE", "/my/account")).session(session))
                    .andReturn());
            mockMvc.perform(post("/api/auth/test/login").param("kind", otherAudience.kind().name())
                    .param("id", String.valueOf(otherAudience.id())).with(csrf()).session(session))
                    .andExpect(status().isNoContent());

            callback(session, "google", "google-link-3", GOOGLE_USER, state).andExpect(redirectedUrl(RESULT_PAGE));

            mockMvc.perform(get(RESULT_PATH).session(session))
                    .andExpect(jsonPath("$.outcome").value("FAILED"))
                    .andExpect(jsonPath("$.provider").value("GOOGLE"))
                    .andExpect(jsonPath("$.returnPath").value("/my/account"))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."));
            verify(linkOAuthUseCase, never()).link(any());
            verifyNoInteractions(logInWithOAuthUseCase);
            assertLoggedInAs(session, otherAudience);
        }

        @Test
        @DisplayName("다른 제공자로 시작하면 연결 의도는 버려지고 로그인으로 시작한다")
        void intentForOtherProviderIsDiscarded() throws Exception {
            given(logInWithOAuthUseCase.logIn(any())).willReturn(new LogInWithOAuthResult.LoggedIn(SEOYUN));
            MockHttpSession session = loggedInSession(SEOYUN);
            startLink(session, "KAKAO", null);

            completeProviderLogin(session, "google", "google-link-2", GOOGLE_USER, null);

            mockMvc.perform(get(RESULT_PATH).session(session)).andExpect(jsonPath("$.outcome").value("LOGGED_IN"));
            verify(linkOAuthUseCase, never()).link(any());
        }

        @Test
        @DisplayName("연결 시작은 관객으로 로그인해야 한다")
        void startRequiresLogin() throws Exception {
            mockMvc.perform(post(LINKS_PATH + "KAKAO").with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }
    }

    /** 로그인을 시작하고, 가짜 제공자가 이 코드로 이 사용자를 인증하게 한 뒤 제공자가 돌려보내는 것처럼 콜백을 부른다. */
    private ResultActions completeProviderLogin(MockHttpSession session, String registrationId, String code,
            String userInfoJson, String returnPath) throws Exception {
        String state = startLogin(session, registrationId, returnPath);
        return callback(session, registrationId, code, userInfoJson, state);
    }

    /** 로그인을 시작하고 제공자 주소에 실린 state를 돌려준다. */
    private String startLogin(MockHttpSession session, String registrationId, String returnPath) throws Exception {
        MockHttpServletRequestBuilder request = get(AUTHORIZATION_PATH + registrationId).session(session);
        if (returnPath != null) {
            request.param("returnPath", returnPath);
        }
        return stateOf(mockMvc.perform(request).andExpect(status().isFound()).andReturn());
    }

    private ResultActions callback(MockHttpSession session, String registrationId, String code, String userInfoJson,
            String state) throws Exception {
        PROVIDER.willAuthenticate(code, userInfoJson);
        return mockMvc.perform(get(CALLBACK_PATH + registrationId).param("code", code).param("state", state).session(session))
                .andExpect(status().isFound());
    }

    private String startLink(MockHttpSession session, String provider, String returnPath) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (returnPath != null) {
            body.put("returnPath", returnPath);
        }
        MvcResult result = mockMvc.perform(post(LINKS_PATH + provider).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("authorizationPath").asString();
    }

    private MockHttpSession loggedInSession(AccountRef account) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/test/login").param("kind", account.kind().name())
                .param("id", String.valueOf(account.id())).with(csrf()).session(session)).andExpect(status().isNoContent());
        return session;
    }

    private static String stateOf(MvcResult startResult) {
        String location = startResult.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        return decoded(UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams().getFirst("state"));
    }

    private static String decoded(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private MockHttpServletRequestBuilder signUpRequest(Map<String, Object> body) {
        return post(SIGNUP_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }

    /** 추가 정보 입력 — 필수 동의 셋에 동의하고 마케팅은 동의하지 않는다. 이메일이 null이면 칸을 비운다. */
    private static Map<String, Object> signUpBody(String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "한서윤");
        body.put("birthDate", "1998-04-02");
        if (email != null) {
            body.put("email", email);
        }
        body.put("phoneNumber", "010-1234-5678");
        body.put("termsOfServiceAgreed", true);
        body.put("personalInformationCollectionAgreed", true);
        body.put("minimumAgeConfirmed", true);
        body.put("marketingAgreed", false);
        return body;
    }

    private void assertLoggedInAs(MockHttpSession session, AccountRef account) throws Exception {
        given(getMyAudienceAccountUseCase.getMyAccount(any()))
                .willReturn(new GetMyAudienceAccountResult("한서윤", "seoyun@example.com", "01012345678", false, List.of()));
        mockMvc.perform(get(ACCOUNT_PATH).session(session)).andExpect(status().isOk());
        ArgumentCaptor<GetMyAudienceAccountQuery> query = ArgumentCaptor.forClass(GetMyAudienceAccountQuery.class);
        verify(getMyAudienceAccountUseCase, atLeastOnce()).getMyAccount(query.capture());
        assertThat(query.getValue().account()).isEqualTo(account);
    }

    private void assertNotLoggedIn(MockHttpSession session) throws Exception {
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        mockMvc.perform(get(ACCOUNT_PATH).session(session)).andExpect(status().isUnauthorized());
    }
}
