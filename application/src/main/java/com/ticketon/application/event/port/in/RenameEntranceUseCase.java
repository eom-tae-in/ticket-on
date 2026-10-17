package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 입구 이름 변경 — 들어오는 포트. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 지금과 같은 이름이면 아무것도 바뀌지 않는다
 * ({@link Event#renameEntrance}). 바뀌었으면 운영진 조작 기록에 남는다.
 */
public interface RenameEntranceUseCase {

    /**
     * @return 바꾼 뒤의 입구
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 목록에 없는(지운)
     *                                   입구면({@link EventErrorCode#ENTRANCE_NOT_FOUND})
     * @throws RuleViolationException    다른 입구가 쓰는 이름이거나({@link EventErrorCode#ENTRANCE_NAME_ALREADY_TAKEN}), 지난 행사면
     *                                   ({@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    EntranceResult rename(RenameEntranceCommand command);
}
