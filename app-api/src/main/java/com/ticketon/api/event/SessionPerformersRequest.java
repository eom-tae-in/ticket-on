package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

import com.ticketon.application.event.port.in.ChangeSessionPerformersCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.SessionPerformers;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회차 출연 요청 — 자유 입력 한 줄(docs/02 "출연 (선택)"). 비우거나 공백뿐이면 출연을 지운다. 줄바꿈 · 길이는 {@link SessionPerformersRule}이
 * 본다(400).
 *
 * @param performers 출연
 */
@Schema(description = "회차 출연")
public record SessionPerformersRequest(
        @Schema(description = "출연 — 자유 입력 한 줄. 앞뒤 공백은 걷는다. 100자까지(확인 필요). 비우거나 null이면 출연을 지운다",
                example = "김배우 · 박배우", requiredMode = NOT_REQUIRED, nullable = true)
        @SessionPerformersRule
        String performers) {

    ChangeSessionPerformersCommand toCommand(ActingStaff actingStaff, long eventId, long sessionId) {
        return new ChangeSessionPerformersCommand(actingStaff, eventId, sessionId, SessionPerformers.fromInput(performers).orElse(null));
    }
}
