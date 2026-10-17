package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 날짜 삭제 — 들어오는 포트. 위저드 2단계에서 날짜를 그 아래 회차와 함께 지운다. 지운 날짜 · 회차는 지운 시각과 함께 남는다. 운영진 누구나
 * 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 수정 범위 확인 · 진행 단계 · 마지막 수정 시각은 {@link Event#removeScheduleDate}가 정한다. 지운 회차마다 재고에 알린다
 * ({@link com.ticketon.domain.event.SessionRemoved}, #54). 운영진 조작 기록에 남는다.
 */
public interface RemoveScheduleDateUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}),
     *                                   일정에 없는(이미 지운) 날짜면({@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND})
     * @throws RuleViolationException    회차 방식을 고르지 않았으면({@link EventErrorCode#SESSION_MODE_NOT_CHOSEN}), 지금 상태에서 회차를 지울 수
     *                                   없으면
     */
    void remove(RemoveScheduleDateCommand command);
}
