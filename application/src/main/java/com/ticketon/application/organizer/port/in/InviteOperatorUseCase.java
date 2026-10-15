package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 운영자 초대 — 들어오는 포트. 소유자가 이메일로 운영자를 초대하고, 받는 사람에게 초대 메일([주최])을 보낸다(docs/01 "운영자 초대",
 * docs/12 "주최자 대상 — 운영자 초대 (받는 사람에게)").
 * <p>
 * 같은 이메일로 보낸 초대가 유효 기간이 지난 채 답을 기다리고 있으면 그 초대를 다시 열어 보낸다. 운영진 조작 기록에 남는다.
 */
public interface InviteOperatorUseCase {

    /**
     * @throws PermissionDeniedException 초대하는 사람이 그 주최자의 소유자가 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#OWNER_ONLY_ACTION},
     *                                   {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws RuleViolationException    그 이메일의 사람이 이미 그 주최자의 운영진이거나({@link OrganizerErrorCode#ALREADY_ORGANIZER_STAFF}),
     *                                   같은 이메일로 보낸 초대가 아직 유효하면({@link OrganizerErrorCode#STAFF_INVITATION_ALREADY_PENDING})
     * @throws ConflictException         같은 이메일로 거의 동시에 초대해 한쪽이 먼저 저장됐으면
     */
    InviteOperatorResult invite(InviteOperatorCommand command);
}
