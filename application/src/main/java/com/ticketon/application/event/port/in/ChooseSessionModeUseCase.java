package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 회차 방식 선택 — 들어오는 포트. 위저드 2단계에서 회차형 / 종일형을 고르고, 종일형이면 행사 단위의 기본 운영 시간을 함께 넣는다(docs/02
 * "회차형과 종일형 — 회차 방식", "날짜마다 운영 시간이 다를 수 있습니다"). 처음 고르면 일정이 생기고, 다시 보내면 회차 방식 · 기본 운영 시간을
 * 고친다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 수정 범위 확인 · 진행 단계 · 마지막 수정 시각은 {@link Event#startSchedule} · {@link Event#changeSessionMode}가 정한다. 같은 값이면 아무것도
 * 바뀌지 않고 조작 기록도 남지 않는다. 바뀌면 운영진 조작 기록에 남는다.
 */
public interface ChooseSessionModeUseCase {

    /**
     * @return 저장한 뒤의 일정 전체
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    날짜가 있는데 회차 방식을 바꾸면({@link EventErrorCode#SESSION_MODE_LOCKED_BY_DATES}), 바뀌는데 지금 상태에서
     *                                   고칠 수 없으면(예매가 열린 뒤 따라 바뀌는 회차가 시작 1주일 전이면
     *                                   {@link EventErrorCode#SESSION_TIME_EDIT_LOCKED})
     */
    EventScheduleResult choose(ChooseSessionModeCommand command);
}
