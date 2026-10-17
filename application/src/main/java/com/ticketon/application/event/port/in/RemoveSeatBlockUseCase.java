package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.SeatBlockRemoved;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 블록 삭제 — 들어오는 포트. 블록을 그 안의 좌석과 함께 지운다. 지운 블록 · 좌석은 지운 시각과 함께 기록으로 남는다 — 취소 · 회수된 티켓은 원래
 * 좌석을 그대로 가리킨다(docs/14). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 좌석이 빠진 것을 재고에 알리고({@link SeatBlockRemoved},
 * #54) 운영진 조작 기록에 남는다. 티켓이 걸린 좌석이 있는 블록을 지울 수 없는 규칙은 #64가 더한다.
 */
public interface RemoveSeatBlockUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 도면에 없는(이미 지운)
     *                                   블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException    지금 상태에서 도면을 고칠 수 없으면
     */
    void remove(RemoveSeatBlockCommand command);
}
