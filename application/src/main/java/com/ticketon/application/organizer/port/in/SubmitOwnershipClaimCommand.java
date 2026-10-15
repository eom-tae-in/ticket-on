package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.OwnershipClaimReason;
import java.util.Objects;

/**
 * 소유자 이전 신청 입력.
 *
 * @param actingStaff 신청하는 운영진과 주최자 — 그 주최자의 운영자여야 한다
 * @param reason      소유자와 연락이 끊긴 사정
 */
public record SubmitOwnershipClaimCommand(ActingStaff actingStaff, OwnershipClaimReason reason) {

    public SubmitOwnershipClaimCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(reason, "reason");
    }
}
