package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.AnswerStaffInvitationCommand;
import com.ticketon.application.organizer.port.out.StaffInvitationRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffInvitation;
import java.util.Objects;

/**
 * 받은 초대에 답하는 사람과 그 초대 — 수락 · 거절이 함께 쓴다.
 * <p>
 * 로그인한 운영진 로그인의 이메일로 온 초대만 찾는다({@link StaffInvitation#isAddressedTo}). 없는 초대와 남의 이메일로 온 초대는 같은 답
 * ({@link OrganizerErrorCode#STAFF_INVITATION_NOT_FOUND})을 준다 — 초대 id를 바꿔 넣어 보며 남의 초대가 있는지 알아낼 수 없게.
 * <p>
 * <b>잠금 순서는 계정 줄 → 초대 줄이다</b>({@link StaffAccountRepository#findByIdForUpdate}의 잠금 순서).
 * <ol>
 *   <li>답하는 사람의 계정 줄을 잠그고 읽는다. 운영진 로그인 탈퇴도 같은 줄을 맨 먼저 잠그므로, 탈퇴가 커밋되기 전에 들어온 수락은 탈퇴가 끝날
 *       때까지 기다렸다가 탈퇴한 계정을 읽고 멈춘다 — 탈퇴한 계정에 지금 소속이 남지 않는다. 탈퇴하면 세션이 모두 끊기지만, 탈퇴 직전에 들어온
 *       요청이 탈퇴 뒤에 처리될 수 있다.</li>
 *   <li>초대 줄을 잠그고 읽는다({@link StaffInvitationRepository#findByIdForUpdate}). 같은 초대에 거의 동시에 답하면 늦은 쪽은 먼저 답한
 *       쪽이 커밋한 뒤의 초대(이미 답함)와 그 뒤의 명단을 읽는다.</li>
 * </ol>
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
     * @throws NotFoundException      없는 초대이거나 그 사람의 이메일로 온 초대가 아니면({@link OrganizerErrorCode#STAFF_INVITATION_NOT_FOUND})
     * @throws RuleViolationException 답하는 운영진 로그인이 이미 탈퇴했으면({@link AccountErrorCode#STAFF_WITHDRAWN})
     */
    static AnsweringInvitee of(StaffAccountRepository accountRepository, StaffInvitationRepository invitationRepository,
            AnswerStaffInvitationCommand command) {
        long staffAccountId = command.account().id();
        StaffAccount account = accountRepository.findByIdForUpdate(staffAccountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 운영진 계정이 없습니다: " + staffAccountId));
        if (account.isWithdrawn()) {
            throw new RuleViolationException(AccountErrorCode.STAFF_WITHDRAWN, "탈퇴한 운영진 " + staffAccountId + "의 초대 답");
        }
        EmailAddress email = account.personalInformation().email();
        StaffInvitation invitation = invitationRepository.findByIdForUpdate(command.invitationId())
                .filter(found -> found.isAddressedTo(email))
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.STAFF_INVITATION_NOT_FOUND,
                        "초대 " + command.invitationId() + ", 운영진 " + staffAccountId));
        return new AnsweringInvitee(staffAccountId, email, invitation);
    }
}
