package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 좌석 구역 배정 — 들어오는 포트. 고른 좌석 여러 개를 한 번에 지정형 구역에 배정하거나(색칠) 배정을 푼다(색 지우기)(docs/02 "좌석을 구역에 배정
 * (색칠)"). 200석짜리 소극장이 5분 안에 만들어져야 해서 좌석을 묶음으로 다룬다. 묶음 안에 도면에 없는 좌석이 하나라도 있으면 아무것도 바꾸지 않는다.
 * 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 바뀌는 좌석이 없으면 저장 · 기록이 없다.
 */
public interface AssignSeatsToZoneUseCase {

    /**
     * @return 고른 좌석들의 지금 값
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 도면에 없는 구역 · 좌석이면
     *                                   ({@link EventErrorCode#ZONE_NOT_FOUND}, {@link EventErrorCode#SEAT_NOT_FOUND})
     * @throws RuleViolationException    수량형 구역이거나({@link EventErrorCode#QUANTITY_ZONE_HAS_NO_SEATS}), 바뀌는데 지금 상태에서 도면을 고칠
     *                                   수 없으면
     */
    SeatsResult assign(AssignSeatsToZoneCommand command);
}
