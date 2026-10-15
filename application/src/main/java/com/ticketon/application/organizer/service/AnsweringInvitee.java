package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.AnswerStaffInvitationCommand;
import com.ticketon.application.organizer.port.out.StaffInvitationRepository;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffInvitation;
import java.util.Objects;

/**
 * 받은 초대에 답하는 사람과 그 초대 — 수락 · 거절이 함께 쓴다.
 * <p>
 * 로그인한 운영진 로그인의 이메일로 온 초대만 찾는다({@link StaffInvitation#isAddressedTo}). 없는 초대와 남의 이메일로 온 초대는 같은 답
 * ({@link OrganizerErrorCode#STAFF_INVITATION_NOT_FOUND})을 준다 — 초대 id를 바꿔 넣어 보며 남의 초대가 있는지 알아낼 수 없게.
 *
 * @param staffAccountId 답하는 사람의 운영진 계정 id
 * @param email          답하는 사람의 운영진 로그인 이메일
 * @param invitation     그 이메일로 온 초대
 */
record AnsweringInvitee(long staffAccountId, EmailAddress email, StaffInvitation invitation) {

    AnsweringInvitee {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(invitation, "invitation");
    }

    /**
     * @throws NotFoundException 없는 초대이거나 그 사람의 이메일로 온 초대가 아니면({@link OrganizerErrorCode#STAFF_INVITATION_NOT_FOUND})
     */
    static AnsweringInvitee of(StaffAccountRepository accountRepository, StaffInvitationRepository invitationRepository,
            AnswerStaffInvitationCommand command) {
        long staffAccountId = command.account().id();
        EmailAddress email = accountRepository.findById(staffAccountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 운영진 계정이 없습니다: " + staffAccountId))
                .personalInformation()
                .email();
        StaffInvitation invitation = invitationRepository.findById(command.invitationId())
                .filter(found -> found.isAddressedTo(email))
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.STAFF_INVITATION_NOT_FOUND,
                        "초대 " + command.invitationId() + ", 운영진 " + staffAccountId));
        return new AnsweringInvitee(staffAccountId, email, invitation);
    }
}
