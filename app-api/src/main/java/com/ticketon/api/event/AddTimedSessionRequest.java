package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.TimedSessionTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 회차형 회차 추가 요청 — 날짜 · 시작 시각(필수) · 종료 시각(선택, 러닝타임)(docs/02 "회차의 시간 정보" — "3/14 · 19:00 시작 ~ 21:00 종료"). 시각은
 * "HH:mm"(분 단위, {@link WholeMinute}), 종료는 시작보다 늦게({@link SessionTimeOrder}) — 400.
 *
 * @param date      회차 날짜
 * @param startTime 시작 시각
 * @param endTime   종료 시각 — 선택
 */
@Schema(description = "회차형 회차 추가 (위저드 2단계)")
@SessionTimeOrder
public record AddTimedSessionRequest(
        @Schema(description = "회차 날짜 — 한국 날짜. 일정에 없는 날짜면 날짜가 함께 생긴다", example = "2026-03-14", requiredMode = REQUIRED)
        @NotNull(message = "회차 날짜를 입력해 주세요.")
        LocalDate date,

        @Schema(description = "시작 시각 — 분 단위. 같은 날짜에 같은 시각의 회차는 둘일 수 없다", example = "19:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:00)?$",
                requiredMode = REQUIRED)
        @NotNull(message = "시작 시각을 입력해 주세요.")
        @WholeMinute
        LocalTime startTime,

        @Schema(description = "종료 시각 — 선택(러닝타임). 시작보다 늦게. 비워 두면 회차 시작 1주일 전부터 입력을 요구한다. 자정을 넘겨 끝나는 회차는 "
                + "아직 받지 않는다(확인 필요)", example = "21:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:00)?$", requiredMode = NOT_REQUIRED, nullable = true)
        @WholeMinute
        LocalTime endTime) implements SessionTimeFields {

    AddTimedSessionCommand toCommand(ActingStaff actingStaff, long eventId) {
        return new AddTimedSessionCommand(actingStaff, eventId, date, TimedSessionTime.of(startTime, endTime));
    }
}
