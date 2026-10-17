package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.ChangeSeatActivationCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * 좌석 여러 개 한 번에 끄기(비활성화) · 다시 켜기 요청 — 통로 · 기둥 같은 빈 자리(docs/02 "블록 안 개별 좌석 비활성화").
 *
 * @param seatIds 고른 좌석 id
 * @param active  켤지(true) 끌지(false)
 */
@Schema(description = "좌석 끄기 · 다시 켜기 (위저드 3단계 배치도 에디터)")
public record ChangeSeatActivationRequest(
        @ArraySchema(arraySchema = @Schema(description = SeatSelection.SEAT_IDS_DESCRIPTION, requiredMode = REQUIRED),
                schema = @Schema(example = "10001"))
        @NotEmpty(message = SeatSelection.SEAT_IDS_REQUIRED)
        List<@NotNull(message = SeatSelection.INVALID_ID) @Positive(message = SeatSelection.INVALID_ID) Long> seatIds,

        @Schema(description = "켤지(true) 끌지(false) — 꺼도 번호 · 구역 · 속성은 그대로이고 다시 켜면 같은 좌석이 돌아온다", example = "false",
                requiredMode = REQUIRED)
        @NotNull(message = "켤지 끌지 골라 주세요.")
        Boolean active) {

    ChangeSeatActivationCommand toCommand(ActingStaff actingStaff, long eventId) {
        return new ChangeSeatActivationCommand(actingStaff, eventId, SeatSelection.toSeatIds(seatIds), active);
    }
}
