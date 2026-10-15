package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.organizer.FilledText;
import com.ticketon.application.account.port.in.ChangeStaffProfileCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffIntroduction;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 프로필 편집 요청 — 닉네임과 한 줄 소개를 함께 보낸다(docs/13 "내 프로필 — 닉네임 · 한 줄 소개 편집"). 보낸 값으로 둘 다 바꾼다 — 소개를
 * 비우거나 보내지 않으면 소개를 지운다. 닉네임이 다른 운영진과 겹치는지는 변경 규칙이 본다(409).
 * <p>
 * 빈 칸은 도메인 값 객체와 같이 {@link String#strip()}으로 걷어 본다({@link FilledText}) — 전각 공백만 넣은 닉네임도 400이다.
 */
@Schema(description = "내 프로필 편집 요청")
public record ChangeStaffProfileRequest(
        @Schema(description = "새 닉네임. 앞뒤 공백을 걷고 20자 이하. [랜덤 생성]으로 받은 값을 넣어도 된다", example = "푸른바람", requiredMode = REQUIRED)
        @FilledText(message = "닉네임을 입력해 주세요.")
        @NicknameRule
        String nickname,

        @Schema(description = "한 줄 소개. 선택 — 비우거나 보내지 않으면 소개를 지운다. 앞뒤 공백을 걷고 50자 이하", example = "극단에서 연출하고 있습니다",
                requiredMode = NOT_REQUIRED, nullable = true)
        @IntroductionRule
        String introduction) {

    ChangeStaffProfileCommand toCommand(AccountRef account) {
        return new ChangeStaffProfileCommand(account, Nickname.of(nickname),
                StaffIntroduction.fromInput(introduction).orElse(null));
    }
}
