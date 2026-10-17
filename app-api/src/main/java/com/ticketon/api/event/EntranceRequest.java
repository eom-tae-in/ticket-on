package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.domain.event.EntranceName;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 입구 추가 · 이름 변경 요청 — 위저드 6단계(docs/02 "입구 (게이트)"). 비었는지 · 길이는 도메인 값 객체와 같은 규칙으로 본다(400 —
 * {@link FilledText}, {@link EntranceNameRule}). 같은 이름의 입구가 있는지는 유스케이스가 본다(409).
 *
 * @param name 입구 이름
 */
@Schema(description = "입구 추가 · 이름 변경 (위저드 6단계)")
public record EntranceRequest(
        @Schema(description = "입구 이름 — 앞뒤 공백은 걷는다. 20자까지(확인 필요). 같은 행사의 다른 입구 이름과 겹칠 수 없다", example = "정문",
                requiredMode = REQUIRED)
        @FilledText(message = "입구 이름을 입력해 주세요.")
        @EntranceNameRule
        String name) {

    EntranceName toEntranceName() {
        return EntranceName.of(name);
    }
}
