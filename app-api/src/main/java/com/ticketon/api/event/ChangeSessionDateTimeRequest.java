package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.ChangeSessionDateTimeCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 회차 일시 변경 요청 — 회차 일시는 날짜까지다(docs/04 "예매가 열린 뒤(판매 중) 수정 범위 — 회차 일시"). 회차형은 날짜와 시작(필수) · 종료(선택)
 * 시각을, 종일형은 날짜만 보낸다 — 회차 방식과 맞는지는 유스케이스가 본다(409). 시각은 "HH:mm"(분 단위, {@link WholeMinute}), 종료는 시작보다
 * 늦게({@link SessionTimeOrder}) — 400.
 *
 * @param date      새 날짜
 * @param startTime 회차형의 새 시작 시각
 * @param endTime   회차형의 새 종료 시각 — 선택
 */
@Schema(description = "회차 일시 변경")
@SessionTimeOrder
public record ChangeSessionDateTimeRequest(
        @Schema(description = "새 날짜 — 한국 날짜. 다른 날짜면 회차가 그 날짜로 옮겨 간다(같은 회차)", example = "2026-03-14",
                requiredMode = REQUIRED)
        @NotNull(message = "회차 날짜를 입력해 주세요.")
        LocalDate date,

        @Schema(description = "회차형의 새 시작 시각 — 회차형은 필수(비우면 409 SESSION_START_TIME_REQUIRED), 종일형은 비운다(적으면 409 "
                + "ALL_DAY_SINGLE_SESSION_PER_DATE)", example = "19:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:00)?$", requiredMode = NOT_REQUIRED, nullable = true)
        @WholeMinute
        LocalTime startTime,

        @Schema(description = "회차형의 새 종료 시각 — 선택. 시작보다 늦게. 종일형은 비운다", example = "21:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:00)?$",
                requiredMode = NOT_REQUIRED, nullable = true)
        @WholeMinute
        LocalTime endTime) implements SessionTimeFields {

    ChangeSessionDateTimeCommand toCommand(ActingStaff actingStaff, long eventId, long sessionId) {
        return new ChangeSessionDateTimeCommand(actingStaff, eventId, sessionId, date, startTime, endTime);
    }
}
