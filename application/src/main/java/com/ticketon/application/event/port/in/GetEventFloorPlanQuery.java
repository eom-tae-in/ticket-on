package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 배치도 에디터용 도면 전체 조회 입력.
 *
 * @param actingStaff 보는 운영진과 주최자
 * @param eventId     행사 id
 */
public record GetEventFloorPlanQuery(ActingStaff actingStaff, long eventId) {

    public GetEventFloorPlanQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
