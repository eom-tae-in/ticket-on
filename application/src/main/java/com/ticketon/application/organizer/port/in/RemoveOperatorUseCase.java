package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 운영자 내보내기 — 들어오는 포트. 소유자가 운영자를 내보내면 그 사람의 소속이 끝나고(이력은 남는다), 그 사람에게 메일([주최])이 간다
 * (docs/01 "권한 두 가지 — 운영진 초대·내보내기", docs/12 "주최자 대상 — 운영진에서 내보내짐 (그 사람에게)"). 내보내진 사람은 그 주최자의
 * 주최 관리에 더는 들어가지 못한다. 운영진 조작 기록에 남는다.
 */
public interface RemoveOperatorUseCase {

    /**
     * @throws PermissionDeniedException 내보내는 사람이 그 주최자의 소유자가 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#OWNER_ONLY_ACTION},
     *                                   {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws RuleViolationException    내보낼 사람이 그 주최자의 운영자가 아니면 — 소유자 자신 포함({@link OrganizerErrorCode#NOT_AN_OPERATOR})
     * @throws ConflictException         그 사이 그 사람이 먼저 나갔으면({@link OrganizerErrorCode#MEMBERSHIP_ALREADY_ENDED})
     */
    void remove(RemoveOperatorCommand command);
}
