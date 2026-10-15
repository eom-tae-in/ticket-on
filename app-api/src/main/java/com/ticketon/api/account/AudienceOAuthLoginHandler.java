package com.ticketon.api.account;

import com.ticketon.api.account.AudienceOAuthSession.PendingSignUp;
import com.ticketon.api.account.AudienceOAuthSession.Result;
import com.ticketon.api.account.AudienceOAuthSession.Start;
import com.ticketon.api.error.ApiErrorCode;
import com.ticketon.api.security.AccountAuthentication;
import com.ticketon.api.security.SessionLogin;
import com.ticketon.application.account.port.in.LinkOAuthCommand;
import com.ticketon.application.account.port.in.LinkOAuthUseCase;
import com.ticketon.application.account.port.in.LogInWithOAuthCommand;
import com.ticketon.application.account.port.in.LogInWithOAuthResult;
import com.ticketon.application.account.port.in.LogInWithOAuthUseCase;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 카카오·구글에서 돌아온 뒤 — 시작 목적대로 로그인·가입 대기·연결을 하고, 결과를 세션에 둔 뒤 브라우저를 화면의 결과 페이지
 * ({@value #RESULT_PAGE_PATH})로 보낸다. 화면은 결과 API(GET /api/auth/audience/oauth2/result)로 무엇이 됐는지 읽는다.
 * <p>
 * <b>제공자에서 로그인한 것은 우리 로그인이 아니다.</b> 제공자 인증 정보를 세션 주체로 저장하지 않는다(필터 묶음이 세션을 읽기만 함 —
 * {@link AudienceOAuthSecurityConfiguration}). 이 요청의 주체도 세션에 있던 로그인으로 되돌린다. 연결된 관객 계정이 있을 때만 {@link SessionLogin#logIn}으로 관객 주체(AUDIENCE:id)의 세션을
 * 연다 — 세션 id를 바꾸고, 이메일·비밀번호 로그인과 같은 주체 이름으로 묶인다.
 * <ul>
 *   <li>로그인: 연결된 계정이 있으면 로그인, 없으면 '추가 정보 입력 대기'를 세션에 둔다. 제공자가 확인한 이메일의 관객 계정이 이미 있으면
 *       자동으로 연결하지 않고 연동 안내로 실패한다(docs/01 "계정 연동").</li>
 *   <li>연결: 연결을 시작한 관객이 지금도 같은 주체로 로그인해 있어야 연결한다. 아니면 로그인이 필요하다고 실패한다.</li>
 * </ul>
 * 계정 규칙에 걸린 실패는 결과의 오류 문구로 화면에 보여 준다. 제공자와 주고받다 실패한 까닭(동의 취소, state 불일치, 제공자 오류)은
 * 로그에만 남기고 화면에는 한 가지 문구로 알린다.
 */
@Component
class AudienceOAuthLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    /** 카카오·구글 로그인을 마친 브라우저가 돌아갈 화면 경로 — 관객 화면의 OAuth 결과 페이지. */
    static final String RESULT_PAGE_PATH = "/oauth/result";

    private static final Logger log = LoggerFactory.getLogger(AudienceOAuthLoginHandler.class);

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    private final LogInWithOAuthUseCase logInWithOAuthUseCase;
    private final LinkOAuthUseCase linkOAuthUseCase;
    private final SessionLogin sessionLogin;
    private final SecurityContextRepository securityContextRepository;
    private final WebAddress webAddress;

    AudienceOAuthLoginHandler(LogInWithOAuthUseCase logInWithOAuthUseCase, LinkOAuthUseCase linkOAuthUseCase,
            SessionLogin sessionLogin, SecurityContextRepository securityContextRepository, WebAddress webAddress) {
        this.logInWithOAuthUseCase = logInWithOAuthUseCase;
        this.linkOAuthUseCase = linkOAuthUseCase;
        this.sessionLogin = sessionLogin;
        this.securityContextRepository = securityContextRepository;
        this.webAddress = webAddress;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        SecurityContext sessionContext = securityContextRepository.loadDeferredContext(request).get();
        securityContextHolderStrategy.setContext(sessionContext);
        OAuth2AuthenticationToken providerAuthentication = (OAuth2AuthenticationToken) authentication;
        OAuthProviderCode provider = OAuthProviderCode
                .fromRegistrationId(providerAuthentication.getAuthorizedClientRegistrationId())
                .orElseThrow(() -> new IllegalStateException("등록하지 않은 제공자로 로그인했습니다: "
                        + providerAuthentication.getAuthorizedClientRegistrationId()));
        Start start = AudienceOAuthSession.takeStart(request)
                .orElseGet(() -> Start.logIn(provider, AudienceLoginController.AUDIENCE_HOME_PATH));

        Result result = profileOf(provider, providerAuthentication)
                .map(profile -> switch (start.purpose()) {
                    case LOGIN -> logIn(request, response, profile, start.returnPath());
                    case LINK -> link(sessionContext, profile, start);
                })
                .orElseGet(() -> Result.failed(provider, start.returnPath(), OAuthLoginErrorCode.OAUTH_LOGIN_FAILED));
        finish(request, response, result);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        Optional<Start> start = AudienceOAuthSession.takeStart(request);
        @Nullable OAuthProviderCode provider = start.map(Start::provider).orElse(null);
        logFailure(provider, exception);
        String returnPath = start.map(Start::returnPath).orElse(AudienceLoginController.AUDIENCE_HOME_PATH);
        finish(request, response, Result.failed(provider, returnPath, OAuthLoginErrorCode.OAUTH_LOGIN_FAILED));
    }

    private Optional<AudienceOAuthProfile> profileOf(OAuthProviderCode provider, OAuth2AuthenticationToken authentication) {
        try {
            return Optional.of(AudienceOAuthProfile.from(provider, authentication.getPrincipal().getAttributes()));
        } catch (IllegalArgumentException exception) {
            log.warn("{} 사용자 정보를 읽지 못해 로그인을 마치지 못했습니다: {}", provider, exception.getMessage());
            return Optional.empty();
        }
    }

    private Result logIn(HttpServletRequest request, HttpServletResponse response, AudienceOAuthProfile profile,
            String returnPath) {
        try {
            LogInWithOAuthResult result = logInWithOAuthUseCase.logIn(
                    new LogInWithOAuthCommand(profile.identity(), profile.verifiedEmail()));
            return switch (result) {
                case LogInWithOAuthResult.LoggedIn loggedIn -> {
                    sessionLogin.logIn(loggedIn.account(), request, response);
                    yield Result.succeeded(OAuthLoginOutcome.LOGGED_IN, profile.provider(), returnPath);
                }
                case LogInWithOAuthResult.SignUpRequired signUpRequired -> {
                    AudienceOAuthSession.savePendingSignUp(request, PendingSignUp.of(profile, returnPath));
                    yield Result.succeeded(OAuthLoginOutcome.SIGNUP_REQUIRED, profile.provider(), returnPath);
                }
            };
        } catch (DomainException exception) {
            return rejected(profile.provider(), returnPath, exception);
        }
    }

    private Result link(SecurityContext sessionContext, AudienceOAuthProfile profile, Start start) {
        Optional<AccountRef> loggedInAccount = AccountAuthentication.accountOf(sessionContext.getAuthentication())
                .filter(account -> account.principalName().equals(start.linkingPrincipalName()));
        if (loggedInAccount.isEmpty()) {
            log.debug("{} 연결 실패 — 연결을 시작한 {}로 로그인해 있지 않음", profile.provider(), start.linkingPrincipalName());
            return Result.failed(profile.provider(), start.returnPath(), ApiErrorCode.UNAUTHENTICATED);
        }
        try {
            linkOAuthUseCase.link(new LinkOAuthCommand(loggedInAccount.get(), profile.identity()));
            return Result.succeeded(OAuthLoginOutcome.LINKED, profile.provider(), start.returnPath());
        } catch (DomainException exception) {
            return rejected(profile.provider(), start.returnPath(), exception);
        }
    }

    private static Result rejected(OAuthProviderCode provider, String returnPath, DomainException exception) {
        log.debug("{} 로그인·연결 거절: {}", provider, exception.getMessage());
        return Result.failed(provider, returnPath, exception.errorCode());
    }

    /** 관객이 제공자 화면에서 동의를 취소한 것은 흔한 일이라 info로, 그 밖의 실패는 warn으로 남긴다. */
    private static void logFailure(@Nullable OAuthProviderCode provider, AuthenticationException exception) {
        boolean cancelledByAudience = exception instanceof OAuth2AuthenticationException oauthException
                && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauthException.getError().getErrorCode());
        if (cancelledByAudience) {
            log.info("{} 로그인 — 관객이 동의를 취소함", provider);
        } else {
            log.warn("{} 로그인을 마치지 못했습니다: {}", provider, exception.getMessage());
        }
    }

    private void finish(HttpServletRequest request, HttpServletResponse response, Result result) throws IOException {
        AudienceOAuthSession.saveResult(request, result);
        response.sendRedirect(webAddress.page(RESULT_PAGE_PATH));
    }
}
