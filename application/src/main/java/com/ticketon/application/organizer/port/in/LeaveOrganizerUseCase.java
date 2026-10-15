package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 이 주최자에서 나가기 — 들어오는 포트. 운영자는 언제든 나가고, 소속은 끝나도 이력으로 남는다(docs/01 "탈퇴와 주최자 닫기", docs/13
 * "운영진 관리 — 이 주최자에서 나가기 ※ 운영자"). 다른 주최자의 소속은 그대로다. 운영진 조작 기록에 남는다.
 */
public interface LeaveOrganizerUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws RuleViolationException    소유자면 — 먼저 소유자를 넘기거나 주최자를 닫아야 한다({@link OrganizerErrorCode#OWNER_MUST_HAND_OVER_FIRST})
     * @throws ConflictException         그 사이 소유자가 먼저 내보냈으면({@link OrganizerErrorCode#MEMBERSHIP_ALREADY_ENDED})
     */
    void leave(LeaveOrganizerCommand command);
}
