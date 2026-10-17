package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.SeatBlockAdded;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 블록 얹기 — 들어오는 포트. 위저드 3단계 배치도 에디터에서 이름 + 행 × 열 덩어리를 얹으면 격자의 모든 자리에 좌석이 생긴다(docs/02 "에디터는
 * 블록 단위"). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 예매가 열린 뒤에도 빈 블록은 더한다. 좌석이 생긴 것을 재고에 알리고
 * ({@link SeatBlockAdded}, #54) 운영진 조작 기록에 남는다.
 */
public interface AddSeatBlockUseCase {

    /**
     * @return 얹은 블록과 그 좌석(id가 붙은)
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    이름이 다른 블록 · 구역과 겹치거나({@link EventErrorCode#BLOCK_NAME_ALREADY_TAKEN},
     *                                   {@link EventErrorCode#BLOCK_NAME_TAKEN_BY_ZONE}), 지금 상태에서 도면을 고칠 수 없으면
     */
    SeatBlockResult add(AddSeatBlockCommand command);
}
