package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.ZoneCapacityChanged;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 구역 수정 — 들어오는 포트. 위저드 3단계에서 구역 한 줄(이름 · 색 · 기본가 · 정원)을 고친다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 바뀌는 칸마다 지금 상태의 수정 범위를 본다({@link Event#changeZone}) — 같은 값을 몇 번 저장해도 안전하다. 정원은 저장된 구역의 종류로 본다 —
 * 지정형에 정원을 보내거나 수량형에서 정원을 비우면 막는다. 수량형의 정원이 바뀌면 재고에 알린다({@link ZoneCapacityChanged}, #54). 바뀌었으면
 * 운영진 조작 기록에 남는다.
 */
public interface ChangeZoneUseCase {

    /**
     * @return 고친 뒤의 구역
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 도면에 없는(지운) 구역이면
     *                                   ({@link EventErrorCode#ZONE_NOT_FOUND})
     * @throws RuleViolationException    정원이 종류와 맞지 않거나({@link EventErrorCode#ZONE_CAPACITY_REQUIRED},
     *                                   {@link EventErrorCode#ZONE_CAPACITY_ONLY_FOR_QUANTITY}), 이름이 다른 구역 · 블록과 겹치거나, 바뀌는
     *                                   칸을 지금 상태에서 고칠 수 없으면
     */
    ZoneResult change(ChangeZoneCommand command);
}
