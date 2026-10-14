package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.GetRandomNicknameResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 랜덤 닉네임 응답 — [랜덤 생성] 버튼을 누르면 닉네임 칸에 채울 값.
 *
 * @param nickname 지금 아무도 쓰지 않는 형용사 + 자연물 조합
 */
@Schema(description = "랜덤 닉네임")
public record RandomNicknameResponse(
        @Schema(description = "지금 다른 운영진이 쓰지 않는 형용사 + 자연물 조합. 쥐어 두지 않으므로 가입·변경할 때 겹치면 409가 될 수 있다",
                example = "조용한고래", requiredMode = REQUIRED)
        String nickname) {

    static RandomNicknameResponse from(GetRandomNicknameResult result) {
        return new RandomNicknameResponse(result.nickname());
    }
}
