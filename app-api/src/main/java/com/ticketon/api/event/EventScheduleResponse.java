package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.EventScheduleResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 행사의 일정 — 위저드 2단계 전체(회차 방식 · 기본 운영 시간 · 날짜 · 회차). 회차의 가격 조정도 함께 담는다(위저드 화면에서는 4단계에 보인다).
 *
 * @param eventId               행사 id
 * @param sessionMode           회차 방식
 * @param defaultOperatingHours 종일형의 기본 운영 시간
 * @param dates                 날짜
 */
@Schema(description = "행사의 일정 (위저드 2단계)")
public record EventScheduleResponse(
        @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
        long eventId,

        @Schema(description = "회차 방식 — 아직 고르지 않았으면 null", requiredMode = NOT_REQUIRED, nullable = true)
        SessionModeCode sessionMode,

        @Schema(description = "기본 운영 시간 — 종일형만. 회차형이거나 회차 방식을 고르지 않았으면 null", requiredMode = NOT_REQUIRED,
                nullable = true)
        OperatingHoursResponse defaultOperatingHours,

        @ArraySchema(arraySchema = @Schema(description = "날짜 — 날짜 순. 없으면 빈 목록", requiredMode = REQUIRED))
        List<ScheduleDateResponse> dates) {

    static EventScheduleResponse from(EventScheduleResult result) {
        return new EventScheduleResponse(result.eventId(), SessionModeCode.from(result.sessionMode()),
                OperatingHoursResponse.from(result.defaultOperatingHours()),
                result.dates().stream().map(ScheduleDateResponse::from).toList());
    }
}
