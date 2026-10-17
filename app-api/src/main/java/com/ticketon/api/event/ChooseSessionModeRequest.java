package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 회차 방식 선택 요청 — 위저드 2단계의 회차 방식과 (종일형이면) 기본 운영 시간(docs/02 "회차형과 종일형 — 회차 방식", "날짜마다 운영 시간이 다를 수
 * 있습니다"). 기본 운영 시간은 종일형일 때만, 종일형이면 꼭 받는다({@link OperatingHoursMatchSessionMode} — 400).
 *
 * @param sessionMode           회차 방식
 * @param defaultOperatingHours 종일형의 기본 운영 시간
 */
@Schema(description = "회차 방식 선택 (위저드 2단계)")
@OperatingHoursMatchSessionMode
public record ChooseSessionModeRequest(
        @Schema(description = "회차 방식 — 회차형(날짜 아래 회차 여러 개) / 종일형(날짜 아래 회차 하나). 날짜가 있으면 바꿀 수 없다",
                requiredMode = REQUIRED)
        @NotNull(message = "회차 방식을 골라 주세요.")
        SessionModeCode sessionMode,

        @Schema(description = "기본 운영 시간 — 종일형만 필수. 회차형은 비우거나 생략한다. 덮어쓰지 않은 날짜는 모두 이 운영 시간이다",
                requiredMode = NOT_REQUIRED, nullable = true)
        @Valid
        OperatingHoursRequest defaultOperatingHours) {

    ChooseSessionModeCommand toCommand(ActingStaff actingStaff, long eventId) {
        return new ChooseSessionModeCommand(actingStaff, eventId, sessionMode.toSessionMode(),
                defaultOperatingHours == null ? null : defaultOperatingHours.toOperatingHours());
    }
}
