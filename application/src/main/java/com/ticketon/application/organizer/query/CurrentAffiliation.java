package com.ticketon.application.organizer.query;

import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.Objects;

/**
 * 한 사람의 지금 소속 하나 — 끝나지 않은 소속과 그 주최자명 · 주최자 상태.
 *
 * @param organizerId     주최자 id
 * @param organizerName   주최자명
 * @param role            그 주최자에서의 역할
 * @param startedAt       지금 역할의 소속을 시작한 일시
 * @param organizerStatus 그 주최자의 상태 — 지금 소속은 활동 중인 주최자에만 남지만(닫힘 · 영구 정지되면 운영진이 빠진다) 저장된 상태를
 *                        그대로 담는다
 */
public record CurrentAffiliation(long organizerId, String organizerName, StaffRole role, Instant startedAt,
        OrganizerStatus organizerStatus) {

    public CurrentAffiliation {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(organizerStatus, "organizerStatus");
    }
}
