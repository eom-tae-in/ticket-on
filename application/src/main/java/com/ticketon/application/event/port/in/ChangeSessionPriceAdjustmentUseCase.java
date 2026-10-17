package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 회차 가격 조정 — 들어오는 포트. 회차마다 구역 기본가에 더하거나 빼는 값(금액 ± 또는 퍼센트 ±)을 정한다(docs/05 "회차별 가격 조정"). 위저드
 * 화면에서는 4단계에 보이지만 회차에 붙는 값이라 회차와 함께 저장한다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 수정 범위 확인 · 진행 단계 · 마지막 수정 시각은 {@link Event#changeSessionPriceAdjustment}가 정한다. 같은 값이면 아무것도 바뀌지 않는다. 조정한
 * 가격의 계산은 #48이 한다. 바뀌면 운영진 조작 기록에 남는다.
 */
public interface ChangeSessionPriceAdjustmentUseCase {

    /**
     * @return 저장한 뒤의 일정 전체
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}),
     *                                   일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException    회차 방식을 고르지 않았으면({@link EventErrorCode#SESSION_MODE_NOT_CHOSEN}), 바뀌는데 지금 상태에서 가격을
     *                                   고칠 수 없으면
     */
    EventScheduleResult change(ChangeSessionPriceAdjustmentCommand command);
}
