package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 블록 회전 — 들어오는 포트. 부채꼴 객석을 만들려고 블록을 돌린다(docs/02 "에디터는 블록 단위"). 좌석은 블록 안의 자리 · 번호 그대로 함께 돌아가고
 * 좌석 id도 그대로다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 같은 각도면 아무것도 바뀌지 않는다.
 */
public interface RotateSeatBlockUseCase {

    /**
     * @return 돌린 뒤의 블록과 그 좌석
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 도면에 없는(지운) 블록이면
     *                                   ({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException    각도가 바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     */
    SeatBlockResult rotate(RotateSeatBlockCommand command);
}
