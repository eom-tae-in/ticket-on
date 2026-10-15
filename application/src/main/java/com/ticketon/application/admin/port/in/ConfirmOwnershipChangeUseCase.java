package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 소유자 변경 사후 확인 — 들어오는 포트. 소유자가 바뀌면 관리자 처리 목록에 '소유자 변경'이 쌓이고(docs/12 "소유자가 바뀌면 관리자에게도
 * 알립니다"), 관리자가 확인하면 목록에서 빠진다. 관리자를 거치지 않고 이미 바뀐 일이라 되돌리지 않는다 — 사칭 · 가로채기가 의심되면 신고 ·
 * 정지 같은 다른 조치로 처리한다. 관리자 · 최고 관리자 모두 하고, 관리자 조작 기록에 남는다.
 */
public interface ConfirmOwnershipChangeUseCase {

    /**
     * @throws NotFoundException      없는 소유자 변경 이력이면({@link OrganizerErrorCode#OWNERSHIP_CHANGE_NOT_FOUND})
     * @throws RuleViolationException 이미 확인했으면({@link OrganizerErrorCode#OWNERSHIP_CHANGE_ALREADY_CONFIRMED}), 소유자 이전 신청을
     *                                승인해 바꾼 건이면({@link OrganizerErrorCode#OWNERSHIP_CHANGE_NOT_CONFIRMABLE} — 처리 목록에 쌓이지 않는다)
     * @throws ConflictException      다른 관리자가 거의 동시에 먼저 확인했으면({@link OrganizerErrorCode#OWNERSHIP_CHANGE_ALREADY_CONFIRMED})
     */
    void confirm(ConfirmOwnershipChangeCommand command);
}
