package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 주최자 공개 정보 편집 화면 조회 입력.
 *
 * @param actingStaff 보는 운영진과 주최자 — 그 주최자의 운영진이어야 한다
 */
public record GetOrganizerPublicProfileQuery(ActingStaff actingStaff) {

    public GetOrganizerPublicProfileQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
