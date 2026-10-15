package com.ticketon.application.organizer.port.in;

import java.time.Instant;
import java.util.Objects;

/**
 * 낸 소유자 이전 신청 — 심사 중으로 시작한다.
 *
 * @param ownershipClaimId 신청 id
 * @param submittedAt      신청 일시
 */
public record SubmitOwnershipClaimResult(long ownershipClaimId, Instant submittedAt) {

    public SubmitOwnershipClaimResult {
        Objects.requireNonNull(submittedAt, "submittedAt");
    }
}
