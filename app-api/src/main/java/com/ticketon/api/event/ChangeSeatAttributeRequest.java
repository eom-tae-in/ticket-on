package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.ChangeSeatAttributeCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * 좌석 여러 개 한 번에 속성 지정 요청 — 일반 · 휠체어 · 시야제한(docs/02 "좌석 속성").
 *
 * @param seatIds   고른 좌석 id
 * @param attribute 속성
 */
@Schema(description = "좌석 속성 지정 (위저드 3단계 배치도 에디터)")
public record ChangeSeatAttributeRequest(
        @ArraySchema(arraySchema = @Schema(description = SeatSelection.SEAT_IDS_DESCRIPTION, requiredMode = REQUIRED),
                schema = @Schema(example = "10001"))
        @NotEmpty(message = SeatSelection.SEAT_IDS_REQUIRED)
        List<@NotNull(message = SeatSelection.INVALID_ID) @Positive(message = SeatSelection.INVALID_ID) Long> seatIds,

        @Schema(description = "속성 — 일반 · 휠체어 · 시야제한. 판매 제외는 회차 단위라 여기 없다", requiredMode = REQUIRED)
        @NotNull(message = "좌석 속성을 골라 주세요.")
        SeatAttributeCode attribute) {

    ChangeSeatAttributeCommand toCommand(ActingStaff actingStaff, long eventId) {
        return new ChangeSeatAttributeCommand(actingStaff, eventId, SeatSelection.toSeatIds(seatIds), attribute.toSeatAttribute());
    }
}
