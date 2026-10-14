package com.ticketon.api.account;

import com.ticketon.application.account.port.in.SignUpAudienceUseCase;
import com.ticketon.application.account.port.in.VerifyAudienceEmailUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관객 직접 가입과 이메일 인증 — 로그인 없이 여는 /api/auth 아래에 둔다(api/security).
 */
@RestController
@RequestMapping("/api/auth/audience")
@Tag(name = "관객 가입", description = "관객 직접 가입과 이메일 인증 (docs/01 \"가입 경로 — 직접 가입\")")
public class AudienceSignUpController {

    private final SignUpAudienceUseCase signUpAudienceUseCase;
    private final VerifyAudienceEmailUseCase verifyAudienceEmailUseCase;

    public AudienceSignUpController(SignUpAudienceUseCase signUpAudienceUseCase,
            VerifyAudienceEmailUseCase verifyAudienceEmailUseCase) {
        this.signUpAudienceUseCase = signUpAudienceUseCase;
        this.verifyAudienceEmailUseCase = verifyAudienceEmailUseCase;
    }

    @Operation(summary = "직접 가입", description = "이메일 + 비밀번호로 가입한다. 인증 대기 계정이 생기고 그 이메일로 인증 링크를 보낸다. "
            + "가입된 번호·차단된 번호는 같은 문구(PHONE_NUMBER_ALREADY_REGISTERED)로 막는다.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignUpAudienceResponse signUp(@Valid @RequestBody SignUpAudienceRequest request) {
        return SignUpAudienceResponse.from(signUpAudienceUseCase.signUp(request.toCommand()));
    }

    @Operation(summary = "이메일 인증 확인", description = "인증 메일 링크의 token으로 인증을 마친다. 인증 대기 계정이 활동 상태가 된다.")
    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyAudienceEmailRequest request) {
        verifyAudienceEmailUseCase.verifyEmail(request.toCommand());
    }
}
