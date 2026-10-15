package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 받은 운영자 초대에 답하는 입력 — 수락 · 거절.
 *
 * @param account      답하는 사람 — 로그인한 운영진(STAFF). 그 운영진 로그인의 이메일로 온 초대여야 한다.
 * @param invitationId 받은 초대의 id (1 이상)
 */
public record AnswerStaffInvitationCommand(AccountRef account, long invitationId) {

    public AnswerStaffInvitationCommand {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("운영자 초대는 운영진 로그인으로만 답합니다: " + account.principalName());
        }
        if (invitationId < 1) {
            throw new IllegalArgumentException("초대 id는 1 이상이어야 합니다: " + invitationId);
        }
    }
}
