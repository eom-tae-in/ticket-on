package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ticketon.application.event.port.in.ScheduleSessionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

/**
 * 회차 한 줄 — 위저드 2단계의 회차 목록(docs/02 "회차의 시간 정보", "출연 (선택)", docs/05 "회차별 가격 조정"). 시각은 "HH:mm".
 *
 * @param sessionId       회차 id
 * @param startTime       회차형의 시작 시각
 * @param endTime         회차형의 종료 시각
 * @param performers      출연
 * @param priceAdjustment 가격 조정
 * @param cancelled       취소된 회차인지
 */
@Schema(description = "회차")
public record ScheduleSessionResponse(
        @Schema(description = "회차 id", example = "101", requiredMode = REQUIRED)
        long sessionId,

        @Schema(description = "회차형의 시작 시각. 종일형은 null — 그 날짜의 운영 시간을 쓴다", example = "19:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                requiredMode = NOT_REQUIRED, nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime startTime,

        @Schema(description = "회차형의 종료 시각. 비워 두었거나 종일형이면 null", example = "21:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9]$", requiredMode = NOT_REQUIRED,
                nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime endTime,

        @Schema(description = "출연 — 적지 않았으면 null", example = "김배우 · 박배우", requiredMode = NOT_REQUIRED, nullable = true)
        String performers,

        @Schema(description = "가격 조정", requiredMode = REQUIRED)
        SessionPriceAdjustmentResponse priceAdjustment,

        @Schema(description = "취소된 회차인지 — 취소된 회차도 일정에 남아 같은 자리를 차지한다", example = "false", requiredMode = REQUIRED)
        boolean cancelled) {

    static ScheduleSessionResponse from(ScheduleSessionResult result) {
        return new ScheduleSessionResponse(result.sessionId(), result.startTime(), result.endTime(), result.performers(),
                SessionPriceAdjustmentResponse.from(result.priceAdjustment()), result.cancelled());
    }
}
