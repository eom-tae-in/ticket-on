package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 블록 이동 — 들어오는 포트. 좌석은 블록 안의 자리 · 번호 그대로 함께 옮겨지고 좌석 id도 그대로다. 운영진 누구나 한다
 * ({@link StaffAction#EDIT_EVENT}). 같은 위치면 아무것도 바뀌지 않는다. 티켓이 걸린 좌석이 있는 블록을 옮길 수 없는 규칙은 #64가 더한다.
 */
public interface MoveSeatBlockUseCase {

    /**
     * @return 옮긴 뒤의 블록과 그 좌석
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 도면에 없는(지운) 블록이면
     *                                   ({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException    위치가 바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     */
    SeatBlockResult move(MoveSeatBlockCommand command);
}
