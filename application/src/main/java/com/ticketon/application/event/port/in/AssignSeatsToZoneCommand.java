package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;
import java.util.Set;

/**
 * 좌석 여러 개 한 번에 구역 배정(색칠) · 배정 풀기(색 지우기) 입력.
 *
 * @param actingStaff 칠하는 운영진과 주최자
 * @param eventId     행사 id
 * @param seatIds     고른 좌석 id
 * @param zoneId      배정할 지정형 구역. null이면 배정을 푼다
 */
public record AssignSeatsToZoneCommand(ActingStaff actingStaff, long eventId, Set<Long> seatIds, Long zoneId) {

    public AssignSeatsToZoneCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        seatIds = Set.copyOf(Objects.requireNonNull(seatIds, "seatIds"));
    }
}
