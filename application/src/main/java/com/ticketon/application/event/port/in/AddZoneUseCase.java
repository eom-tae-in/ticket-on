package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.ZoneAdded;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 구역 추가 — 들어오는 포트. 위저드 3단계에서 행사의 도면에 지정형 또는 수량형 구역을 더한다(docs/02 "구역"). 수량형은 정원 입력만으로 끝나고,
 * 지정형은 블록 · 좌석 배정이 이어진다(#43). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 수정 범위 확인 · 진행 단계 · 마지막 수정 시각은 {@link Event#addZone}이 정한다. 더한 구역은 재고에 알린다({@link ZoneAdded}, #54). 운영진 조작
 * 기록에 남는다.
 */
public interface AddZoneUseCase {

    /**
     * @return 더한 구역
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    정원이 종류와 맞지 않거나({@link EventErrorCode#ZONE_CAPACITY_REQUIRED},
     *                                   {@link EventErrorCode#ZONE_CAPACITY_ONLY_FOR_QUANTITY}), 이름이 다른 구역 · 블록과 겹치거나
     *                                   ({@link EventErrorCode#ZONE_NAME_ALREADY_TAKEN}, {@link EventErrorCode#ZONE_NAME_TAKEN_BY_BLOCK}), 지금
     *                                   상태에서 도면을 고칠 수 없으면
     */
    ZoneResult add(AddZoneCommand command);
}
