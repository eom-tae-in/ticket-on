package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 내 행사 목록 조회 입력 — 주최 관리에 들어와 그 주최자의 행사를 볼 때.
 *
 * @param actingStaff 보는 운영진과 주최자
 */
public record GetOrganizerEventListQuery(ActingStaff actingStaff) {

    public GetOrganizerEventListQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
