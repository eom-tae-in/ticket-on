package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 운영자 초대 수락 — 들어오는 포트. 초대받은 이메일의 운영진 로그인으로 수락하면 그 주최자의 운영자 소속이 바로 시작된다(docs/01 "운영자
 * 초대" — 운영자는 심사를 받지 않는다). 운영진 조작 기록에 운영자로서 남는다.
 */
public interface AcceptStaffInvitationUseCase {

    /**
     * @throws NotFoundException      없는 초대이거나 로그인한 운영진의 이메일로 온 초대가 아니면({@link OrganizerErrorCode#STAFF_INVITATION_NOT_FOUND})
     * @throws RuleViolationException 이미 답했거나({@link OrganizerErrorCode#STAFF_INVITATION_ALREADY_ANSWERED}), 유효 기간이 지났거나
     *                                ({@link OrganizerErrorCode#STAFF_INVITATION_EXPIRED}), 주최자가 활동 중이 아니거나
     *                                ({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE}), 이미 그 주최자의 운영진이면
     *                                ({@link OrganizerErrorCode#ALREADY_ORGANIZER_STAFF})
     * @throws ConflictException      같은 초대를 거의 동시에 두 번 수락해 한쪽이 먼저 처리됐으면
     */
    AcceptStaffInvitationResult accept(AnswerStaffInvitationCommand command);
}
