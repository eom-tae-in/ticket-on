package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.AddAllDayDateCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 종일형 날짜 추가 요청 — 날짜와 그 날짜의 회차 하나가 함께 생기고, 운영 시간은 기본 운영 시간을 따른다(docs/02 "회차형과 종일형").
 *
 * @param date 날짜
 */
@Schema(description = "종일형 날짜 추가 (위저드 2단계)")
public record AddAllDayDateRequest(
        @Schema(description = "날짜 — 한국 날짜", example = "2026-05-03", requiredMode = REQUIRED)
        @NotNull(message = "날짜를 입력해 주세요.")
        LocalDate date) {

    AddAllDayDateCommand toCommand(ActingStaff actingStaff, long eventId) {
        return new AddAllDayDateCommand(actingStaff, eventId, date);
    }
}
