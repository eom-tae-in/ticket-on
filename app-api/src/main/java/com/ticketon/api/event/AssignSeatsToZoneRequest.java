package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.AssignSeatsToZoneCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * 좌석 여러 개 한 번에 구역 배정(색칠) · 배정 풀기(색 지우기) 요청(docs/02 "좌석을 구역에 배정 (색칠)").
 *
 * @param seatIds 고른 좌석 id
 * @param zoneId  배정할 지정형 구역 — 비우면 배정을 푼다
 */
@Schema(description = "좌석 구역 배정 · 배정 풀기 (위저드 3단계 배치도 에디터)")
public record AssignSeatsToZoneRequest(
        @ArraySchema(arraySchema = @Schema(description = SeatSelection.SEAT_IDS_DESCRIPTION, requiredMode = REQUIRED),
                schema = @Schema(example = "10001"))
        @NotEmpty(message = SeatSelection.SEAT_IDS_REQUIRED)
        List<@NotNull(message = SeatSelection.INVALID_ID) @Positive(message = SeatSelection.INVALID_ID) Long> seatIds,

        @Schema(description = "배정할 지정형 구역 id — 비우거나 생략하면 배정을 푼다(색 지우기). 수량형 구역이면 409 QUANTITY_ZONE_HAS_NO_SEATS",
                example = "31", requiredMode = NOT_REQUIRED, nullable = true)
        @Positive(message = SeatSelection.INVALID_ID)
        Long zoneId) {

    AssignSeatsToZoneCommand toCommand(ActingStaff actingStaff, long eventId) {
        return new AssignSeatsToZoneCommand(actingStaff, eventId, SeatSelection.toSeatIds(seatIds), zoneId);
    }
}
