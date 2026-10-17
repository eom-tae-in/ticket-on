package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 배치도 에디터용 도면 전체 조회 — 들어오는 포트. 위저드 3단계 에디터를 열 때 구역 · 블록 · 좌석 · 무대 위치를 한 번에 읽는다. 운영진 누구나 본다
 * ({@link StaffAction#EDIT_EVENT}).
 */
public interface GetEventFloorPlanUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     */
    FloorPlanResult get(GetEventFloorPlanQuery query);
}
