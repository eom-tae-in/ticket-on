package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.UnlinkOAuthCommand;
import com.ticketon.application.account.port.in.UnlinkOAuthUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 마이페이지 카카오·구글 연동 API — 관객 영역 접근, 연결 시작의 응답, 해제의 Command 변환과 오류 응답을 본다. 유스케이스는 흉내 낸다.
 * 연결 시작 뒤 제공자를 다녀오는 흐름은 AudienceOAuthLoginFlowTest가 본다.
 */
@WebMvcTest(controllers = AudienceOAuthLinkController.class)
@Import(SecurityConfiguration.class)
@DisplayName("카카오·구글 연동 API")
class AudienceOAuthLinkControllerTest {

    private static final String LINKS_PATH = "/api/audience/account/oauth-links/";

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, 7);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UnlinkOAuthUseCase unlinkOAuthUseCase;

    @Nested
    @DisplayName("연결 시작")
    class StartLink {

        @Test
        @DisplayName("브라우저를 옮길 로그인 시작 주소를 돌려준다 — 본문 없이도 된다")
        void returnsAuthorizationPath() throws Exception {
            mockMvc.perform(post(LINKS_PATH + "GOOGLE").with(csrf()).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorizationPath").value("/api/auth/audience/oauth2/authorization/google"));
        }

        @Test
        @DisplayName("모르는 제공자 이름이면 400이다")
        void unknownProvider() throws Exception {
            mockMvc.perform(post(LINKS_PATH + "NAVER").with(csrf()).with(loggedInAs(SEOYUN))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"STAFF", "ADMIN", "CHECKIN"})
        @DisplayName("관객이 아닌 계정으로는 403이다")
        void otherAccountKindIsDenied(AccountKind kind) throws Exception {
            mockMvc.perform(post(LINKS_PATH + "KAKAO").with(csrf()).with(loggedInAs(kind, 7)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
        }

        @Test
        @DisplayName("CSRF 토큰이 없으면 403이다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(LINKS_PATH + "KAKAO").with(loggedInAs(SEOYUN))).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("연결 해제")
    class Unlink {

        @Test
        @DisplayName("로그인한 관객과 주소의 제공자로 해제하고 204로 끝난다")
        void unlinks() throws Exception {
            mockMvc.perform(delete(LINKS_PATH + "KAKAO").with(csrf()).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isNoContent());

            ArgumentCaptor<UnlinkOAuthCommand> command = ArgumentCaptor.forClass(UnlinkOAuthCommand.class);
            verify(unlinkOAuthUseCase).unlink(command.capture());
            assertThat(command.getValue()).isEqualTo(new UnlinkOAuthCommand(SEOYUN, OAuthProvider.KAKAO));
        }

        @Test
        @DisplayName("로그인 수단이 하나도 남지 않으면 409 LAST_LOGIN_METHOD — 비밀번호 찾기로 비밀번호를 정하라고 안내한다")
        void lastLoginMethod() throws Exception {
            willThrow(new RuleViolationException(AccountErrorCode.LAST_LOGIN_METHOD)).given(unlinkOAuthUseCase).unlink(any());

            mockMvc.perform(delete(LINKS_PATH + "KAKAO").with(csrf()).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("LAST_LOGIN_METHOD", "로그인할 방법이 하나도 남지 않아 연결을 해제할 수 없습니다. "
                            + "비밀번호 찾기로 비밀번호를 정하거나 다른 계정을 연결한 뒤 해제해 주세요."));
        }

        @Test
        @DisplayName("그 사이 다른 요청이 먼저 해제했으면 409 OAUTH_PROVIDER_NOT_LINKED다")
        void alreadyUnlinked() throws Exception {
            willThrow(new ConflictException(AccountErrorCode.OAUTH_PROVIDER_NOT_LINKED)).given(unlinkOAuthUseCase).unlink(any());

            mockMvc.perform(delete(LINKS_PATH + "GOOGLE").with(csrf()).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("OAUTH_PROVIDER_NOT_LINKED", "연결된 계정이 없습니다."));
        }

        @Test
        @DisplayName("로그인하지 않았으면 401이고 유스케이스를 부르지 않는다")
        void requiresLogin() throws Exception {
            mockMvc.perform(delete(LINKS_PATH + "KAKAO").with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
            verifyNoInteractions(unlinkOAuthUseCase);
        }

        @Test
        @DisplayName("모르는 제공자 이름이면 400이다")
        void unknownProvider() throws Exception {
            mockMvc.perform(delete(LINKS_PATH + "kakao").with(csrf()).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(unlinkOAuthUseCase);
        }
    }
}
