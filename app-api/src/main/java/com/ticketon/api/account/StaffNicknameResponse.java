package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.ChangeStaffNicknameResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영진 닉네임 변경 응답 — 저장된 닉네임. 화면은 이 값으로 닉네임 칸을 다시 채운다.
 *
 * @param nickname 지금 닉네임 — 앞뒤 공백을 걷어 저장된 그대로
 */
@Schema(description = "운영진 닉네임")
public record StaffNicknameResponse(
        @Schema(description = "지금 닉네임 — 앞뒤 공백을 걷어 저장된 그대로", example = "푸른바람", requiredMode = REQUIRED)
        String nickname) {

    static StaffNicknameResponse from(ChangeStaffNicknameResult result) {
        return new StaffNicknameResponse(result.nickname());
    }
}
