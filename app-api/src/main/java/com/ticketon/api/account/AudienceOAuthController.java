package com.ticketon.api.account;

import com.ticketon.api.account.AudienceOAuthSession.PendingSignUp;
import com.ticketon.api.account.AudienceOAuthSession.Result;
import com.ticketon.api.security.SessionLogin;
import com.ticketon.application.account.port.in.SignUpWithOAuthResult;
import com.ticketon.application.account.port.in.SignUpWithOAuthUseCase;
import com.ticketon.domain.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관객 카카오·구글 로그인 결과와 추가 정보 입력 가입 — 로그인 없이 여는 /api/auth 아래에 둔다(api/security).
 * <p>
 * 흐름(docs/01 "가입 경로 — OAuth"):
 * <ol>
 *   <li>화면이 브라우저를 로그인 시작 주소 {@code GET /api/auth/audience/oauth2/authorization/{kakao|google}?returnPath=…}로 옮긴다
 *       (AudienceOAuthSecurityConfiguration). 제공자 로그인·동의를 거쳐 서버로 돌아오고, 서버는 브라우저를 화면의 결과 페이지
 *       {@code /oauth/result}로 보낸다.</li>
 *   <li>결과 페이지가 {@code GET /result}로 결과를 읽는다 — 로그인됨(세션 쿠키가 이미 실림), 추가 정보 입력 필요, 실패.</li>
 *   <li>추가 정보 입력이 필요하면 {@code POST /signup}으로 가입한다. 제공자 계정은 세션에 둔 '추가 정보 입력 대기'에서 가져온다 —
 *       화면이 제공자 계정을 보내지 않는다.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/auth/audience/oauth2")
@Tag(name = "관객 카카오·구글 로그인", description = "카카오·구글 로그인 결과와 추가 정보 입력 가입 (docs/01 \"가입 경로 — OAuth\", "
        + "docs/13 \"추가 정보 입력\"). 로그인 시작은 브라우저를 GET /api/auth/audience/oauth2/authorization/{kakao|google}?returnPath=… 로 "
        + "옮긴다 — 끝나면 화면의 /oauth/result로 돌아온다")
public class AudienceOAuthController {

    private final SignUpWithOAuthUseCase signUpWithOAuthUseCase;
    private final SessionLogin sessionLogin;

    public AudienceOAuthController(SignUpWithOAuthUseCase signUpWithOAuthUseCase, SessionLogin sessionLogin) {
        this.signUpWithOAuthUseCase = signUpWithOAuthUseCase;
        this.sessionLogin = sessionLogin;
    }

    @Operation(summary = "로그인 결과", description = "지금 세션의 마지막 카카오·구글 로그인·연결 결과. LOGGED_IN이면 이미 로그인돼 있다(세션 id가 바뀌었으니 "
            + "상태 변경 요청 전에 CSRF 토큰을 새로 받는다). SIGNUP_REQUIRED면 pendingSignUp으로 추가 정보 입력을 채우고 가입 API를 부른다. "
            + "FAILED면 error.message를 띄운다 — 같은 이메일의 관객 계정이 있으면 \"이 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인한 뒤 "
            + "구글 계정을 연결해 주세요.\"(카카오면 카카오)다. 진행 중인 로그인이 없으면(세션 만료 등) 404 OAUTH_LOGIN_NOT_IN_PROGRESS.")
    @GetMapping("/result")
    public OAuthLoginResultResponse result(HttpServletRequest httpRequest) {
        Result result = AudienceOAuthSession.result(httpRequest)
                .orElseThrow(() -> new NotFoundException(OAuthLoginErrorCode.OAUTH_LOGIN_NOT_IN_PROGRESS));
        return OAuthLoginResultResponse.of(result, AudienceOAuthSession.pendingSignUp(httpRequest));
    }

    @Operation(summary = "추가 정보 입력 가입", description = "SIGNUP_REQUIRED였던 카카오·구글 계정으로 가입하고 그 계정을 연결한다. 가입 규칙은 직접 가입과 "
            + "같다(만 14세 미만·필수 동의 누락·가입된 번호·차단된 번호는 409). 같은 이메일의 관객 계정이 있으면 409(연결 안내 문구). "
            + "제공자가 확인한 이메일이면 바로 로그인되고(loggedIn=true, 세션 쿠키가 실림), 아니면 적은 이메일로 인증 메일을 보낸다 — 인증을 마친 뒤 "
            + "카카오·구글로 다시 로그인한다. 이메일을 받아야 하는데 비었으면 400(fieldErrors.email). 추가 정보 입력 대기가 없으면 404 "
            + "OAUTH_LOGIN_NOT_IN_PROGRESS.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthSignUpResponse signUp(@Valid @RequestBody OAuthSignUpRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) throws BindException {
        PendingSignUp pendingSignUp = AudienceOAuthSession.pendingSignUp(httpRequest)
                .orElseThrow(() -> new NotFoundException(OAuthLoginErrorCode.OAUTH_LOGIN_NOT_IN_PROGRESS));
        request.requireEmailIfNeeded(pendingSignUp);
        SignUpWithOAuthResult result = signUpWithOAuthUseCase.signUp(request.toCommand(pendingSignUp));
        AudienceOAuthSession.finishSignUp(httpRequest);
        result.accountToLogIn().ifPresent(account -> sessionLogin.logIn(account, httpRequest, httpResponse));
        return new OAuthSignUpResponse(result.emailVerified(), result.email(), pendingSignUp.returnPath());
    }
}
