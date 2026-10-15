package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.RejectionReason;
import java.util.Objects;

/**
 * 주최자 신청 반려 입력.
 *
 * @param reviewer      반려하는 관리자 — 로그인한 관리자(ADMIN) 주체
 * @param applicationId 신청서 id
 * @param reason        반려 사유 — 템플릿 글이든 직접 입력한 글이든 신청한 사람에게 그대로 전달된다
 */
public record RejectOrganizerApplicationCommand(AccountRef reviewer, long applicationId, RejectionReason reason) {

    public RejectOrganizerApplicationCommand {
        Objects.requireNonNull(reviewer, "reviewer");
        Objects.requireNonNull(reason, "reason");
        if (reviewer.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("주최자 신청은 관리자가 반려합니다: " + reviewer.principalName());
        }
        if (applicationId < 1) {
            throw new IllegalArgumentException("신청서 id는 1 이상이어야 합니다: " + applicationId);
        }
    }
}
