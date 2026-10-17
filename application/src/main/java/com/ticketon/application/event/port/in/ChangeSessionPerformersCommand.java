package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.SessionPerformers;
import java.util.Objects;

/**
 * 회차 출연 입력 — 자유 입력 한 줄("김배우 · 박배우")(docs/02 "출연 (선택)").
 *
 * @param actingStaff 고치는 운영진과 주최자
 * @param eventId     행사 id
 * @param sessionId   회차 id
 * @param performers  새 출연. 지우려면 null
 */
public record ChangeSessionPerformersCommand(ActingStaff actingStaff, long eventId, long sessionId,
        SessionPerformers performers) {

    public ChangeSessionPerformersCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
