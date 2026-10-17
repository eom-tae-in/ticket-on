package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.EntranceResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 입구 한 줄 — 위저드 6단계의 입구 목록(docs/02 "입구 (게이트)").
 *
 * @param entranceId 입구 id
 * @param name       이름
 */
@Schema(description = "입구")
public record EntranceResponse(
        @Schema(description = "입구 id", example = "12", requiredMode = REQUIRED)
        long entranceId,

        @Schema(description = "이름 — 앞뒤 공백을 걷은 값", example = "정문", requiredMode = REQUIRED)
        String name) {

    static EntranceResponse from(EntranceResult result) {
        return new EntranceResponse(result.entranceId(), result.name());
    }
}
