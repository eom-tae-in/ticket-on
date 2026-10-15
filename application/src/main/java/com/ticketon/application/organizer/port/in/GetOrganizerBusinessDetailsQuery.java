package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 주최자의 사업자 정보 · 정산 계좌 보기 입력.
 *
 * @param actingStaff 보는 운영진과 주최자 — 그 주최자의 소유자여야 한다
 */
public record GetOrganizerBusinessDetailsQuery(ActingStaff actingStaff) {

    public GetOrganizerBusinessDetailsQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
