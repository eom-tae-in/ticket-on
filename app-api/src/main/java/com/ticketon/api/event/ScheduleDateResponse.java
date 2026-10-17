package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.ScheduleDateResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 날짜 한 줄 — 그 날짜와 아래 회차(docs/02 "날짜와 회차").
 *
 * @param date                   날짜
 * @param operatingHours         그날 실제로 쓰는 운영 시간(종일형)
 * @param operatingHoursOverride 그날만 덮어쓴 운영 시간(종일형)
 * @param sessions               회차
 */
@Schema(description = "날짜")
public record ScheduleDateResponse(
        @Schema(description = "날짜 — 한국 날짜", example = "2026-05-03", requiredMode = REQUIRED)
        LocalDate date,

        @Schema(description = "그날 실제로 쓰는 운영 시간(종일형) — 덮어쓴 운영 시간이 있으면 그것, 없으면 기본 운영 시간. 회차형은 null",
                requiredMode = NOT_REQUIRED, nullable = true)
        OperatingHoursResponse operatingHours,

        @Schema(description = "그날만 덮어쓴 운영 시간(종일형). 기본 운영 시간을 따르거나 회차형이면 null", requiredMode = NOT_REQUIRED,
                nullable = true)
        OperatingHoursResponse operatingHoursOverride,

        @ArraySchema(arraySchema = @Schema(description = "회차 — 시작 시각 순. 종일형은 하나", requiredMode = REQUIRED))
        List<ScheduleSessionResponse> sessions) {

    static ScheduleDateResponse from(ScheduleDateResult result) {
        return new ScheduleDateResponse(result.date(), OperatingHoursResponse.from(result.operatingHours()),
                OperatingHoursResponse.from(result.operatingHoursOverride()),
                result.sessions().stream().map(ScheduleSessionResponse::from).toList());
    }
}
