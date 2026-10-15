package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 운영자 초대 거절 — 들어오는 포트. 그 주최자에는 아무것도 바뀌지 않고 받은 초대에서 빠진다. 운영진이 아닌 사람의 일이라 운영진 조작 기록에는
 * 남지 않고, 초대에 답한 일시와 사람이 남는다.
 */
public interface DeclineStaffInvitationUseCase {

    /**
     * @throws NotFoundException      없는 초대이거나 로그인한 운영진의 이메일로 온 초대가 아니면({@link OrganizerErrorCode#STAFF_INVITATION_NOT_FOUND})
     * @throws RuleViolationException 이미 답했거나({@link OrganizerErrorCode#STAFF_INVITATION_ALREADY_ANSWERED}), 유효 기간이 지났으면
     *                                ({@link OrganizerErrorCode#STAFF_INVITATION_EXPIRED})
     * @throws ConflictException      같은 초대에 거의 동시에 다른 답이 먼저 저장됐으면
     */
    void decline(AnswerStaffInvitationCommand command);
}
