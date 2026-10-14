package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.ChangeStaffNicknameCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.Nickname;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 운영진 닉네임 변경 요청 — 새 닉네임 하나. 다른 운영진과 겹치는지는 변경 규칙이 본다(409).
 */
@Schema(description = "운영진 닉네임 변경 요청")
public record ChangeStaffNicknameRequest(
        @Schema(description = "새 닉네임. 앞뒤 공백을 걷고 20자 이하. [랜덤 생성]으로 받은 값을 넣어도 된다", example = "푸른바람", requiredMode = REQUIRED)
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @NicknameRule
        String nickname) {

    ChangeStaffNicknameCommand toCommand(AccountRef account) {
        return new ChangeStaffNicknameCommand(account, Nickname.of(nickname));
    }
}
