package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.ZoneRemoved;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 구역 삭제 — 들어오는 포트. 위저드 3단계에서 행사의 도면에서 구역을 지운다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 줄은 남기고 지운 시각을 적는다 — 취소 · 회수된 티켓이 원래 구역을 계속 가리킨다(docs/14). 수정 범위 확인은 {@link Event#removeZone}이 한다 —
 * 티켓이 있는 구역을 지울 수 없는 규칙은 #64가 더한다. 지운 구역은 재고에 알린다({@link ZoneRemoved}, #54). 운영진 조작 기록에 남는다.
 */
public interface RemoveZoneUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 도면에 없는(이미 지운)
     *                                   구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     * @throws RuleViolationException    지금 상태에서 구역을 지울 수 없으면
     */
    void remove(RemoveZoneCommand command);
}
