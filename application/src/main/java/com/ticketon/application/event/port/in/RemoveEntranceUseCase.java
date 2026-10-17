package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 입구 삭제 — 들어오는 포트. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 줄은 남기고 지운 시각을 적는다 — 그 입구를 가리키는 입장 기록이
 * 남는다(docs/14). 운영진 조작 기록에 남는다.
 */
public interface RemoveEntranceUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 목록에 없는(이미 지운)
     *                                   입구면({@link EventErrorCode#ENTRANCE_NOT_FOUND})
     * @throws RuleViolationException    지난 행사면({@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    void remove(RemoveEntranceCommand command);
}
