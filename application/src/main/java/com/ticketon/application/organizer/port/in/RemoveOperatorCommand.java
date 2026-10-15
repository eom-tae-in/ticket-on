package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 운영자 내보내기 입력.
 *
 * @param actingStaff            내보내는 운영진과 주최자 — 그 주최자의 소유자여야 한다
 * @param operatorStaffAccountId 내보낼 운영자의 운영진 계정 id (1 이상)
 */
public record RemoveOperatorCommand(ActingStaff actingStaff, long operatorStaffAccountId) {

    public RemoveOperatorCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        if (operatorStaffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + operatorStaffAccountId);
        }
    }
}
