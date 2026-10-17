package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.EventEntrancesResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 행사의 입구 목록 — 지운 입구는 빠지고, 더한 순서다.
 *
 * @param eventId   행사 id
 * @param entrances 입구
 */
@Schema(description = "행사의 입구 목록")
public record EventEntrancesResponse(
        @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
        long eventId,

        @ArraySchema(arraySchema = @Schema(description = "입구 — 더한 순서. 없으면 빈 목록", requiredMode = REQUIRED))
        List<EntranceResponse> entrances) {

    static EventEntrancesResponse from(EventEntrancesResult result) {
        return new EventEntrancesResponse(result.eventId(), result.entrances().stream().map(EntranceResponse::from).toList());
    }
}
