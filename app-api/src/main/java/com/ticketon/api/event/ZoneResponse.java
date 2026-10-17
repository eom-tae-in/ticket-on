package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.ZoneResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 구역 한 줄 — 위저드 3단계의 구역 목록(docs/02 "구역"). 수정 요청은 이 칸 이름(종류 · id 빼고)으로 보낸다.
 *
 * @param zoneId    구역 id
 * @param kind      종류
 * @param name      이름
 * @param color     색
 * @param basePrice 기본가(원)
 * @param capacity  정원 — 수량형만
 */
@Schema(description = "구역")
public record ZoneResponse(
        @Schema(description = "구역 id", example = "31", requiredMode = REQUIRED)
        long zoneId,

        @Schema(description = "종류 — 지정형 / 수량형", requiredMode = REQUIRED)
        ZoneKindCode kind,

        @Schema(description = "이름 — 앞뒤 공백을 걷은 값", example = "스탠딩A", requiredMode = REQUIRED)
        String name,

        @Schema(description = "색 — #RRGGBB 대문자", example = "#30A46C", requiredMode = REQUIRED)
        String color,

        @Schema(description = "기본가(원)", example = "30000", requiredMode = REQUIRED)
        long basePrice,

        @Schema(description = "정원 — 수량형만. 지정형은 null(배정한 좌석이 자리다)", example = "300", requiredMode = NOT_REQUIRED,
                nullable = true)
        Integer capacity) {

    static ZoneResponse from(ZoneResult result) {
        return new ZoneResponse(result.zoneId(), ZoneKindCode.from(result.kind()), result.name(), result.color(), result.basePrice(),
                result.capacity());
    }
}
