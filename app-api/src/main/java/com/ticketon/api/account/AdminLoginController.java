package com.ticketon.api.account;

import com.ticketon.api.security.ReturnPath;
import com.ticketon.api.security.SessionLogin;
import com.ticketon.application.account.port.in.LogInAdminResult;
import com.ticketon.application.account.port.in.LogInAdminUseCase;
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
 * 관리자 로그인·로그아웃 — 로그인 없이 여는 /api/auth 아래에 둔다(api/security).
 * <p>
 * 관객·운영진 로그인과 같은 방식이다. 로그인은 유스케이스로 계정을 확인한 뒤 {@link SessionLogin#logInAdmin}으로 세션을 연다 — 주체는
 * {@code ADMIN:id}이고, 등급을 세션에 함께 담아 최고 관리자 전용 경로({@code /api/admin/super/**})를 가른다. 한 세션에는 주체 하나만
 * 로그인한다. 로그아웃은 지금 세션 하나만 끊는다.
 */
@RestController
@RequestMapping("/api/auth/admin")
@Tag(name = "관리자 로그인", description = "플랫폼 관리자 로그인·로그아웃 — 세션 쿠키 (docs/01 \"관리자 2등급\", docs/16 \"로그인\")")
public class AdminLoginController {

    /** 돌아갈 경로가 없거나 받지 않는 경로일 때 갈 곳 — 관리자 첫 화면(web/src/app/admin). */
    static final String ADMIN_HOME_PATH = "/admin";

    private final LogInAdminUseCase logInAdminUseCase;
    private final SessionLogin sessionLogin;

    public AdminLoginController(LogInAdminUseCase logInAdminUseCase, SessionLogin sessionLogin) {
        this.logInAdminUseCase = logInAdminUseCase;
        this.sessionLogin = sessionLogin;
    }

    @Operation(summary = "로그인", description = "이메일·비밀번호로 관리자 로그인을 해 세션 쿠키를 받는다. 응답의 grade로 최고 관리자인지 안다. "
            + "실패는 까닭(없는 이메일·틀린 비밀번호)과 상관없이 409 ADMIN_LOGIN_FAILED 하나다. 같은 이메일의 관객 계정·운영진 로그인으로는 "
            + "로그인되지 않는다. 로그인 전에 받은 CSRF 토큰은 버려지므로 다음 상태 변경 요청 전에 새 토큰을 받는다.")
    @PostMapping("/login")
    public LogInAdminResponse logIn(@Valid @RequestBody LogInAdminRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LogInAdminResult result = logInAdminUseCase.logIn(request.toCommand());
        sessionLogin.logInAdmin(result.account(), result.grade(), httpRequest, httpResponse);
        return new LogInAdminResponse(ReturnPath.sameSiteOf(request.returnPath()).orElse(ADMIN_HOME_PATH),
                AdminGradeCode.from(result.grade()));
    }

    @Operation(summary = "로그아웃", description = "지금 세션만 끝낸다. 같은 계정이 다른 기기에서 로그인한 세션은 남는다. 로그인하지 않았어도 204다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logOut(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        sessionLogin.logOut(httpRequest, httpResponse);
    }
}
