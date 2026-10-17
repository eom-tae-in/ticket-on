package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ticketon.application.event.port.in.OperatingHoursResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

/**
 * 운영 시간 — 종일형 회차의 시간. 시각은 "HH:mm".
 *
 * @param openTime      운영 시작
 * @param closeTime     운영 종료
 * @param lastEntryTime 적은 마지막 입장 — 비워 두었으면 null(운영 종료와 같게 처리)
 */
@Schema(description = "운영 시간 (종일형)")
public record OperatingHoursResponse(
        @Schema(description = "운영 시작", example = "10:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9]$", requiredMode = REQUIRED)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime openTime,

        @Schema(description = "운영 종료", example = "18:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9]$", requiredMode = REQUIRED)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime closeTime,

        @Schema(description = "마지막 입장 — 비워 두었으면 null(운영 종료와 같게 처리)", example = "17:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                requiredMode = NOT_REQUIRED, nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime lastEntryTime) {

    static OperatingHoursResponse from(OperatingHoursResult result) {
        return result == null ? null : new OperatingHoursResponse(result.openTime(), result.closeTime(), result.lastEntryTime());
    }
}
