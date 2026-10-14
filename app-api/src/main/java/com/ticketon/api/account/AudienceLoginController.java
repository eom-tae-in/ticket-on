package com.ticketon.api.account;

import com.ticketon.api.security.ReturnPath;
import com.ticketon.api.security.SessionLogin;
import com.ticketon.application.account.port.in.LogInAudienceResult;
import com.ticketon.application.account.port.in.LogInAudienceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관객 로그인·로그아웃 — 로그인 없이 여는 /api/auth 아래에 둔다(api/security).
 * <p>
 * 로그인은 유스케이스로 계정을 확인한 뒤 {@link SessionLogin}으로 세션을 연다. 세션 쿠키가 응답에 실린다.
 * 로그아웃은 지금 세션 하나만 끊는다. 같은 관객이 다른 기기에서 로그인한 세션은 남는다.
 */
@RestController
@RequestMapping("/api/auth/audience")
@Tag(name = "관객 로그인", description = "관객 로그인·로그아웃 — 세션 쿠키 (docs/01 \"가입 경로\", docs/16 \"로그인\")")
public class AudienceLoginController {

    /** 돌아갈 경로가 없거나 받지 않는 경로일 때 갈 곳 — 관객 첫 화면. 관객 화면은 주소에 영역 이름이 없다(web/src/app/(audience)). */
    static final String AUDIENCE_HOME_PATH = "/";

    private final LogInAudienceUseCase logInAudienceUseCase;
    private final SessionLogin sessionLogin;

    public AudienceLoginController(LogInAudienceUseCase logInAudienceUseCase, SessionLogin sessionLogin) {
        this.logInAudienceUseCase = logInAudienceUseCase;
        this.sessionLogin = sessionLogin;
    }

    @Operation(summary = "로그인", description = "이메일·비밀번호로 로그인해 세션 쿠키를 받는다. 이메일 인증을 마쳐야 로그인되고, 이용 정지 중에도 로그인된다. "
            + "실패는 까닭(없는 이메일·틀린 비밀번호·인증 전·탈퇴)과 상관없이 409 AUDIENCE_LOGIN_FAILED 하나다. "
            + "로그인 전에 받은 CSRF 토큰은 버려지므로 다음 상태 변경 요청 전에 새 토큰을 받는다.")
    @PostMapping("/login")
    public LogInAudienceResponse logIn(@Valid @RequestBody LogInAudienceRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LogInAudienceResult result = logInAudienceUseCase.logIn(request.toCommand());
        sessionLogin.logIn(result.account(), httpRequest, httpResponse);
        return new LogInAudienceResponse(ReturnPath.sameSiteOf(request.returnPath()).orElse(AUDIENCE_HOME_PATH));
    }

    @Operation(summary = "로그아웃", description = "지금 세션만 끝낸다. 같은 계정이 다른 기기에서 로그인한 세션은 남는다. 로그인하지 않았어도 204다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logOut(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        sessionLogin.logOut(httpRequest, httpResponse);
    }
}
