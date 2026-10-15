package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.RejectionReason;
import java.util.Objects;

/**
 * 소유자 이전 신청 반려 입력.
 *
 * @param admin            반려하는 관리자 — 로그인한 관리자(ADMIN) 주체
 * @param ownershipClaimId 소유자 이전 신청 id (1 이상)
 * @param reason           반려 사유 — 운영진 전원에게 메일로 그대로 전달된다
 */
public record RejectOwnershipClaimCommand(AccountRef admin, long ownershipClaimId, RejectionReason reason) {

    public RejectOwnershipClaimCommand {
        Objects.requireNonNull(admin, "admin");
        Objects.requireNonNull(reason, "reason");
        if (admin.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("소유자 이전 신청은 관리자가 반려합니다: " + admin.principalName());
        }
        if (ownershipClaimId < 1) {
            throw new IllegalArgumentException("소유자 이전 신청 id는 1 이상이어야 합니다: " + ownershipClaimId);
        }
    }
}
