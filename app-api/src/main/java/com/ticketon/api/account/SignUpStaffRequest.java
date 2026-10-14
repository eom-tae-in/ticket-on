package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.SignUpStaffCommand;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffPersonalInformation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 운영진 로그인 가입 요청 — 이름·닉네임·이메일·비밀번호, 전부 필수(docs/01 "수집 정보 — 주최자 운영진").
 * <p>
 * 닉네임이 다른 운영진과 겹치는지, 이메일로 만든 운영진 로그인이 이미 있는지는 가입 규칙이 본다(409).
 */
@Schema(description = "운영진 로그인 가입 요청")
public record SignUpStaffRequest(
        @Schema(description = "이름 — 공개 프로필에 나가지 않는다", example = "박정훈", requiredMode = REQUIRED)
        @NotBlank(message = "이름을 입력해 주세요.")
        String name,

        @Schema(description = "닉네임 — 공개 프로필에 실명 대신 보인다. 앞뒤 공백을 걷고 20자 이하. 다른 운영진과 겹칠 수 없다. [랜덤 생성]으로 받은 값을 넣어도 된다",
                example = "조용한고래", requiredMode = REQUIRED)
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @NicknameRule
        String nickname,

        @Schema(description = "이메일 — 운영진 로그인 아이디. 인증 링크를 이 주소로 보낸다. 같은 이메일의 관객 계정이 있어도 된다",
                example = "junghoon@example.com", requiredMode = REQUIRED)
        @NotBlank(message = "이메일을 입력해 주세요.")
        @EmailAddressFormat
        String email,

        @Schema(description = "비밀번호", requiredMode = REQUIRED)
        @NotNull(message = "비밀번호를 입력해 주세요.")
        @NewPasswordRule
        String password) {

    SignUpStaffCommand toCommand() {
        return new SignUpStaffCommand(StaffPersonalInformation.of(name, email), Nickname.of(nickname),
                new NewPassword(password));
    }

    @Override
    public String toString() {
        return "SignUpStaffRequest[nickname=" + nickname + ", email=" + email + ", password=****]";
    }
}
