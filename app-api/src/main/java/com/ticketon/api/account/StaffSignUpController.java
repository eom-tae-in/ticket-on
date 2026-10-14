package com.ticketon.api.account;

import com.ticketon.application.account.port.in.GetRandomNicknameUseCase;
import com.ticketon.application.account.port.in.SignUpStaffUseCase;
import com.ticketon.application.account.port.in.VerifyStaffEmailUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영진 로그인 가입·이메일 인증·랜덤 닉네임 — 로그인 없이 여는 /api/auth 아래에 둔다(api/security).
 * <p>
 * 랜덤 닉네임은 가입 화면과 로그인한 뒤의 내 프로필 화면이 함께 쓰므로 로그인 없이 연다.
 */
@RestController
@RequestMapping("/api/auth/staff")
@Tag(name = "운영진 가입", description = "주최자 쪽 로그인 가입과 이메일 인증, 랜덤 닉네임 (docs/01 \"수집 정보 — 주최자 운영진\")")
public class StaffSignUpController {

    private final SignUpStaffUseCase signUpStaffUseCase;
    private final VerifyStaffEmailUseCase verifyStaffEmailUseCase;
    private final GetRandomNicknameUseCase getRandomNicknameUseCase;

    public StaffSignUpController(SignUpStaffUseCase signUpStaffUseCase, VerifyStaffEmailUseCase verifyStaffEmailUseCase,
            GetRandomNicknameUseCase getRandomNicknameUseCase) {
        this.signUpStaffUseCase = signUpStaffUseCase;
        this.verifyStaffEmailUseCase = verifyStaffEmailUseCase;
        this.getRandomNicknameUseCase = getRandomNicknameUseCase;
    }

    @Operation(summary = "가입", description = "이름·닉네임·이메일·비밀번호로 주최자 쪽 로그인을 만든다. 인증 대기 계정이 생기고 그 이메일로 "
            + "[주최] 인증 링크를 보낸다. 같은 이메일의 관객 계정이 있어도 가입되고 서로 연결하지 않는다. "
            + "같은 이메일의 운영진 로그인이 있으면 409 STAFF_EMAIL_ALREADY_REGISTERED, 다른 운영진이 쓰는 닉네임이면 409 STAFF_NICKNAME_ALREADY_TAKEN.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignUpStaffResponse signUp(@Valid @RequestBody SignUpStaffRequest request) {
        return SignUpStaffResponse.from(signUpStaffUseCase.signUp(request.toCommand()));
    }

    @Operation(summary = "이메일 인증 확인", description = "인증 메일 링크의 token으로 인증을 마친다. 인증 대기 계정이 활동 상태가 되어 로그인할 수 있다.")
    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyStaffEmailRequest request) {
        verifyStaffEmailUseCase.verifyEmail(request.toCommand());
    }

    @Operation(summary = "랜덤 닉네임", description = "[랜덤 생성] 버튼 — 지금 다른 운영진이 쓰지 않는 형용사 + 자연물 조합(예: 조용한고래)을 하나 준다. "
            + "쥐어 두지 않는다. 조합을 모두 쓰고 있으면 409 RANDOM_NICKNAME_EXHAUSTED.")
    @GetMapping("/random-nickname")
    public RandomNicknameResponse getRandomNickname() {
        return RandomNicknameResponse.from(getRandomNicknameUseCase.getRandomNickname());
    }
}
