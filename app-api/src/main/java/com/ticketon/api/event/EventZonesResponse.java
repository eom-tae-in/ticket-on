package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.EventZonesResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 행사 도면의 구역 목록 — 지운 구역은 빠지고, 더한 순서다.
 *
 * @param eventId 행사 id
 * @param zones   구역
 */
@Schema(description = "행사의 구역 목록")
public record EventZonesResponse(
        @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
        long eventId,

        @ArraySchema(arraySchema = @Schema(description = "구역 — 더한 순서. 없으면 빈 목록", requiredMode = REQUIRED))
        List<ZoneResponse> zones) {

    static EventZonesResponse from(EventZonesResult result) {
        return new EventZonesResponse(result.eventId(), result.zones().stream().map(ZoneResponse::from).toList());
    }
}
