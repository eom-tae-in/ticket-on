package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 행사 일정 조회 입력 — 위저드 2단계를 열 때.
 *
 * @param actingStaff 보는 운영진과 주최자
 * @param eventId     행사 id
 */
public record GetEventScheduleQuery(ActingStaff actingStaff, long eventId) {

    public GetEventScheduleQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
