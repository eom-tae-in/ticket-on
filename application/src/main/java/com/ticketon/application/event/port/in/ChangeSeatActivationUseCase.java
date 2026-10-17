package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 좌석 끄기 · 다시 켜기 — 들어오는 포트. 고른 좌석 여러 개를 한 번에 통로 · 기둥 같은 빈 자리로 끄거나 다시 켠다(docs/02 "블록 안 개별 좌석
 * 비활성화"). 번호는 다시 매기지 않고 구역 · 속성은 그대로 둔다 — 다시 켜면 같은 좌석이 같은 번호로 돌아온다. 묶음 안에 도면에 없는 좌석이 하나라도
 * 있으면 아무것도 바꾸지 않는다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 바뀌는 좌석이 없으면 저장 · 기록이 없다. 티켓이 걸린 좌석을
 * 끌 수 없는 규칙은 #64가 더한다.
 */
public interface ChangeSeatActivationUseCase {

    /**
     * @return 고른 좌석들의 지금 값
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 도면에 없는 좌석이면
     *                                   ({@link EventErrorCode#SEAT_NOT_FOUND})
     * @throws RuleViolationException    바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     */
    SeatsResult change(ChangeSeatActivationCommand command);
}
