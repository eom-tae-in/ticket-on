package com.ticketon.application.organizer.query;

import java.time.Instant;
import java.util.Objects;

/**
 * 한 사람이 받고 아직 답하지 않은 운영자 초대 하나(docs/01 "운영자 초대") — 그 사람의 운영진 로그인 이메일로 와서 유효 기간이 남은 초대.
 *
 * @param invitationId  초대 id — 수락 · 거절할 때 쓴다
 * @param organizerId   초대한 주최자 id
 * @param organizerName 초대한 주최자명
 * @param invitedAt     보낸 일시 — 다시 보냈으면 다시 보낸 일시
 * @param expiresAt     이 순간부터 답할 수 없다
 */
public record ReceivedOrganizerInvitation(long invitationId, long organizerId, String organizerName, Instant invitedAt,
        Instant expiresAt) {

    public ReceivedOrganizerInvitation {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(invitedAt, "invitedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
