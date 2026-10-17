package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 종일형 날짜 추가 — 들어오는 포트. 위저드 2단계에서 종일형 행사에 날짜를 더한다 — 날짜와 그 날짜의 회차 하나가 함께 생기고 운영 시간은 기본
 * 운영 시간을 따른다(docs/02 "회차형과 종일형"). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 수정 범위 확인 · 진행 단계 · 마지막 수정 시각은 {@link Event#addAllDayDate}가 정한다. 생긴 회차는 재고에 알린다
 * ({@link com.ticketon.domain.event.SessionAdded}, #54). 운영진 조작 기록에 남는다.
 */
public interface AddAllDayDateUseCase {

    /**
     * @return 저장한 뒤의 일정 전체
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    회차 방식을 고르지 않았으면({@link EventErrorCode#SESSION_MODE_NOT_CHOSEN}), 회차형이면
     *                                   ({@link EventErrorCode#TIMED_DATE_NEEDS_SESSION}), 이미 있는 날짜면
     *                                   ({@link EventErrorCode#SCHEDULE_DATE_ALREADY_EXISTS}), 지금 상태에서 회차를 더할 수 없으면
     */
    EventScheduleResult add(AddAllDayDateCommand command);
}
