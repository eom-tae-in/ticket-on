package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 운영진 목록 조회 입력.
 *
 * @param actingStaff 보는 운영진과 주최자 — 그 주최자의 운영진이어야 한다
 */
public record GetStaffListQuery(ActingStaff actingStaff) {

    public GetStaffListQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
