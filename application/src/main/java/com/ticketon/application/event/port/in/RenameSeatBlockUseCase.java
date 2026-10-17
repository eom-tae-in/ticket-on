package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 블록 이름 변경 — 들어오는 포트. 판매 중에도 고치고 이미 팔린 표의 표기도 따라 바뀐다 — 이력은 남기지 않는다(docs/02 "판매 중에도 블록 이름을
 * 고칠 수 있습니다"). 좌석 id와 번호는 그대로다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 같은 이름이면 아무것도 바뀌지 않는다.
 * 팔린 좌석이 있는 블록의 "예매자에게 알릴까요?" 창은 #110이 더한다.
 */
public interface RenameSeatBlockUseCase {

    /**
     * @return 바꾼 뒤의 블록과 그 좌석
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 도면에 없는(지운) 블록이면
     *                                   ({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException    이름이 다른 블록 · 구역과 겹치거나, 이름이 바뀌는데 지금 상태에서 블록 이름을 고칠 수 없으면
     */
    SeatBlockResult rename(RenameSeatBlockCommand command);
}
