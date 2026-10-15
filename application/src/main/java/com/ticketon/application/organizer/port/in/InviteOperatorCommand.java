package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.account.EmailAddress;
import java.util.Objects;

/**
 * 운영자 초대 입력.
 *
 * @param actingStaff  초대하는 운영진과 주최자 — 그 주최자의 소유자여야 한다
 * @param inviteeEmail 초대받는 이메일
 */
public record InviteOperatorCommand(ActingStaff actingStaff, EmailAddress inviteeEmail) {

    public InviteOperatorCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(inviteeEmail, "inviteeEmail");
    }
}
