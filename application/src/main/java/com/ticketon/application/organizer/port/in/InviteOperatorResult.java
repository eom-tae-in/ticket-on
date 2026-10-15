package com.ticketon.application.organizer.port.in;

import java.time.Instant;
import java.util.Objects;

/**
 * 보낸 운영자 초대.
 *
 * @param invitationId 초대 id
 * @param inviteeEmail 초대받은 이메일 — 적은 그대로
 * @param invitedAt    보낸 일시
 * @param expiresAt    이 순간부터 받은 사람이 답할 수 없다
 */
public record InviteOperatorResult(long invitationId, String inviteeEmail, Instant invitedAt, Instant expiresAt) {

    public InviteOperatorResult {
        Objects.requireNonNull(inviteeEmail, "inviteeEmail");
        Objects.requireNonNull(invitedAt, "invitedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
