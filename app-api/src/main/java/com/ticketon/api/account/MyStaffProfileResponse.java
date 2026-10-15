package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.MyStaffProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 프로필 — 운영진 프로필에 공개되는 닉네임 · 한 줄 소개와 첫 글자 표시(docs/13 "내 프로필"). 화면은 이 값으로 편집 칸을 채운다. 이름 ·
 * 이메일은 공개되지 않아 담지 않는다.
 *
 * @param staffAccountId 운영진 계정 id — 공개 프로필 주소에 쓴다
 * @param nickname       닉네임
 * @param introduction   한 줄 소개. 적지 않았으면 null.
 * @param avatar         첫 글자 표시
 */
@Schema(description = "내 프로필")
public record MyStaffProfileResponse(
        @Schema(description = "운영진 계정 id — 공개 프로필(/api/public/staff-profiles/{staffAccountId})을 가리킬 때 쓴다", example = "7",
                requiredMode = REQUIRED)
        long staffAccountId,

        @Schema(description = "닉네임 — 앞뒤 공백을 걷어 저장된 그대로", example = "조용한고래", requiredMode = REQUIRED)
        String nickname,

        @Schema(description = "한 줄 소개 — 앞뒤 공백을 걷어 저장된 그대로. 적지 않았으면 null", example = "극단에서 연출하고 있습니다",
                requiredMode = NOT_REQUIRED, nullable = true)
        String introduction,

        @Schema(description = "사진 대신 보이는 첫 글자 표시", requiredMode = REQUIRED)
        InitialAvatarResponse avatar) {

    static MyStaffProfileResponse from(MyStaffProfileResult result) {
        return new MyStaffProfileResponse(result.staffAccountId(), result.nickname(), result.introduction(),
                new InitialAvatarResponse(result.initial(), result.paletteIndex()));
    }
}
