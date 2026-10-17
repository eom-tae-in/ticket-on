package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 행사 일정 조회 — 들어오는 포트. 위저드 2단계를 열 때 회차 방식 · 기본 운영 시간 · 날짜 · 회차(시간 · 출연 · 가격 조정)를 한 번에 돌려준다
 * (docs/03 "행사 만들기 위저드" — 나갔다 들어오면 이어서 한다). 고칠 수 있는 사람(운영진 누구나, {@link StaffAction#EDIT_EVENT})이 본다.
 */
public interface GetEventScheduleUseCase {

    /**
     * @return 그 행사의 일정. 회차 방식을 아직 고르지 않았으면 회차 방식 · 기본 운영 시간이 비어 있고 날짜가 없다
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     */
    EventScheduleResult get(GetEventScheduleQuery query);
}
