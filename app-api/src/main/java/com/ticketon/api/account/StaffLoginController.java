package com.ticketon.api.account;

import com.ticketon.api.security.ReturnPath;
import com.ticketon.api.security.SessionLogin;
import com.ticketon.application.account.port.in.LogInStaffResult;
import com.ticketon.application.account.port.in.LogInStaffUseCase;
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
 * 운영진 로그인·로그아웃 — 로그인 없이 여는 /api/auth 아래에 둔다(api/security).
 * <p>
 * 관객 로그인과 같은 방식이다. 로그인은 유스케이스로 계정을 확인한 뒤 {@link SessionLogin}으로 세션을 연다 — 주체는 {@code STAFF:id}다.
 * 한 세션에는 주체 하나만 로그인하므로, 관객으로 로그인한 세션에서 운영진으로 로그인하면 운영진이 관객을 대신한다.
 * 로그아웃은 지금 세션 하나만 끊는다.
 */
@RestController
@RequestMapping("/api/auth/staff")
@Tag(name = "운영진 로그인", description = "주최자 쪽 로그인·로그아웃 — 세션 쿠키 (docs/01 \"같은 이메일로 양쪽 가입\", docs/16 \"로그인\")")
public class StaffLoginController {

    /** 돌아갈 경로가 없거나 받지 않는 경로일 때 갈 곳 — 주최 첫 화면(web/src/app/organizer). */
    static final String ORGANIZER_HOME_PATH = "/organizer";

    private final LogInStaffUseCase logInStaffUseCase;
    private final SessionLogin sessionLogin;

    public StaffLoginController(LogInStaffUseCase logInStaffUseCase, SessionLogin sessionLogin) {
        this.logInStaffUseCase = logInStaffUseCase;
        this.sessionLogin = sessionLogin;
    }

    @Operation(summary = "로그인", description = "이메일·비밀번호로 주최자 쪽 로그인을 해 세션 쿠키를 받는다. 이메일 인증을 마쳐야 로그인된다. "
            + "실패는 까닭(없는 이메일·틀린 비밀번호·인증 전·탈퇴)과 상관없이 409 STAFF_LOGIN_FAILED 하나다. 같은 이메일의 관객 계정으로는 로그인되지 않는다. "
            + "로그인 전에 받은 CSRF 토큰은 버려지므로 다음 상태 변경 요청 전에 새 토큰을 받는다.")
    @PostMapping("/login")
    public LogInStaffResponse logIn(@Valid @RequestBody LogInStaffRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LogInStaffResult result = logInStaffUseCase.logIn(request.toCommand());
        sessionLogin.logIn(result.account(), httpRequest, httpResponse);
        return new LogInStaffResponse(ReturnPath.sameSiteOf(request.returnPath()).orElse(ORGANIZER_HOME_PATH));
    }

    @Operation(summary = "로그아웃", description = "지금 세션만 끝낸다. 같은 계정이 다른 기기에서 로그인한 세션은 남는다. 로그인하지 않았어도 204다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logOut(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        sessionLogin.logOut(httpRequest, httpResponse);
    }
}
