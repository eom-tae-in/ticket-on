package com.ticketon.application.organizer.port.in;

import java.time.Instant;
import java.util.Objects;

/**
 * 운영자 초대를 수락해 들어간 주최자 — 화면이 주최자 전환으로 바로 그 주최자를 고를 수 있게 한다.
 *
 * @param organizerId   들어간 주최자 id
 * @param organizerName 들어간 주최자명
 * @param joinedAt      운영자 소속을 시작한 일시
 */
public record AcceptStaffInvitationResult(long organizerId, String organizerName, Instant joinedAt) {

    public AcceptStaffInvitationResult {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
