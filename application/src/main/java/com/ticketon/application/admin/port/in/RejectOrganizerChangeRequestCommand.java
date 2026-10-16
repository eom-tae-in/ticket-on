package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.RejectionReason;
import java.util.Objects;

/**
 * 정산 계좌 · 사업자 정보 변경 반려 입력.
 *
 * @param admin           반려하는 관리자 — 로그인한 관리자(ADMIN) 주체
 * @param changeRequestId 변경 요청 id (1 이상)
 * @param reason          반려 사유 — 소유자에게 메일로 그대로 전달된다
 */
public record RejectOrganizerChangeRequestCommand(AccountRef admin, long changeRequestId, RejectionReason reason) {

    public RejectOrganizerChangeRequestCommand {
        Objects.requireNonNull(admin, "admin");
        Objects.requireNonNull(reason, "reason");
        if (admin.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("변경 요청은 관리자가 반려합니다: " + admin.principalName());
        }
        if (changeRequestId < 1) {
            throw new IllegalArgumentException("변경 요청 id는 1 이상이어야 합니다: " + changeRequestId);
        }
    }
}
