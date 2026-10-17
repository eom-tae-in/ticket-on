package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 회차 일시 변경 — 들어오는 포트. 회차의 날짜와 (회차형이면) 시작 · 종료 시각을 바꾼다. 회차는 그대로라 재고 · 티켓도 그대로 따라간다(docs/04
 * "기존 회차의 날짜를 고칩니다"). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 수정 범위 · 예매가 열린 뒤 시작 1주일 전 잠금 · 비어 있던 종료 시각 채우기 · 진행 단계 · 마지막 수정 시각은 {@link Event#changeSessionDateTime}이
 * 정한다. 같은 값이면 아무것도 바뀌지 않는다. 티켓이 있는 회차의 알림 · 공제 면제는 #105가 한다. 바뀌면 운영진 조작 기록에 남는다.
 */
public interface ChangeSessionDateTimeUseCase {

    /**
     * @return 저장한 뒤의 일정 전체
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}),
     *                                   일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException    회차 방식을 고르지 않았으면({@link EventErrorCode#SESSION_MODE_NOT_CHOSEN}), 종일형인데 시각을 적었거나 회차형인데
     *                                   시작 시각이 없으면({@link EventErrorCode#ALL_DAY_SINGLE_SESSION_PER_DATE},
     *                                   {@link EventErrorCode#SESSION_START_TIME_REQUIRED}), 옮겨 갈 날짜 · 시각이 겹치면, 시작 1주일 전이 지났으면
     *                                   ({@link EventErrorCode#SESSION_TIME_EDIT_LOCKED}), 지금 상태에서 회차 일시를 고칠 수 없으면
     */
    EventScheduleResult change(ChangeSessionDateTimeCommand command);
}
