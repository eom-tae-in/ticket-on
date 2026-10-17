package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.SeatsResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 좌석 여러 개를 한 번에 고친 뒤의 그 좌석들.
 *
 * @param eventId 행사 id
 * @param seats   고른 좌석들의 지금 값
 */
@Schema(description = "고친 뒤의 좌석들")
public record SeatsResponse(
        @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
        long eventId,

        @ArraySchema(arraySchema = @Schema(description = "고른 좌석들의 지금 값 — 블록 순서, 같은 블록은 열 · 번호 순서", requiredMode = REQUIRED))
        List<SeatResponse> seats) {

    static SeatsResponse from(SeatsResult result) {
        return new SeatsResponse(result.eventId(), result.seats().stream().map(SeatResponse::from).toList());
    }
}
