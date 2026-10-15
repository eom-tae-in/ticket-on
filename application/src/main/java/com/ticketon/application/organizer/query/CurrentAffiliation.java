package com.ticketon.application.organizer.query;

import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.Objects;

/**
 * 한 사람의 지금 소속 하나 — 끝나지 않은 소속과 그 주최자명.
 *
 * @param organizerId   주최자 id
 * @param organizerName 주최자명
 * @param role          그 주최자에서의 역할
 * @param startedAt     지금 역할의 소속을 시작한 일시
 */
public record CurrentAffiliation(long organizerId, String organizerName, StaffRole role, Instant startedAt) {

    public CurrentAffiliation {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}
