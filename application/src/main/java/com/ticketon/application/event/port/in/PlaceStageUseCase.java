package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 무대 위치 지정 — 들어오는 포트. 배치도 위에 무대를 놓는다(docs/02 "에디터는 블록 단위" — 무대 위치 지정). 운영진 누구나 한다
 * ({@link StaffAction#EDIT_EVENT}). 같은 자리면 아무것도 바뀌지 않는다.
 */
public interface PlaceStageUseCase {

    /**
     * @return 정한 뒤의 무대 위치
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    위치가 바뀌는데 지금 상태에서 도면을 고칠 수 없으면
     */
    CanvasPointResult place(PlaceStageCommand command);
}
