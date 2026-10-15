package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 소유자 넘기기 입력.
 *
 * @param actingStaff            넘기는 운영진과 주최자 — 그 주최자의 소유자여야 한다
 * @param newOwnerStaffAccountId 받을 사람의 운영진 계정 id (1 이상) — 그 주최자의 운영자여야 한다
 */
public record TransferOwnershipCommand(ActingStaff actingStaff, long newOwnerStaffAccountId) {

    public TransferOwnershipCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        if (newOwnerStaffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + newOwnerStaffAccountId);
        }
    }
}
