package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 행사 구역 목록 조회 입력.
 *
 * @param actingStaff 보는 운영진과 주최자
 * @param eventId     행사 id
 */
public record GetEventZonesQuery(ActingStaff actingStaff, long eventId) {

    public GetEventZonesQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
