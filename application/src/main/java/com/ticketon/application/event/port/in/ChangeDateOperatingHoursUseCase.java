package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 날짜별 운영 시간 — 들어오는 포트. 종일형 날짜 하나의 운영 시간을 덮어쓰거나, 덮어쓴 것을 지워 기본 운영 시간을 따르게 한다(docs/02 "날짜마다
 * 운영 시간이 다를 수 있습니다" — 주말은 늦게까지). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 수정 범위 확인(그날 회차의 시간 수정) · 진행 단계 · 마지막 수정 시각은 {@link Event#changeDateOperatingHours}가 정한다. 같은 값이면 아무것도
 * 바뀌지 않는다. 바뀌면 운영진 조작 기록에 남는다.
 */
public interface ChangeDateOperatingHoursUseCase {

    /**
     * @return 저장한 뒤의 일정 전체
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}),
     *                                   일정에 없는 날짜면({@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND})
     * @throws RuleViolationException    회차 방식을 고르지 않았으면({@link EventErrorCode#SESSION_MODE_NOT_CHOSEN}), 회차형이면
     *                                   ({@link EventErrorCode#OPERATING_HOURS_ONLY_FOR_ALL_DAY}), 바뀌는데 지금 상태에서 그날 회차의 시간을 고칠
     *                                   수 없으면({@link EventErrorCode#SESSION_TIME_EDIT_LOCKED} 등)
     */
    EventScheduleResult change(ChangeDateOperatingHoursCommand command);
}
