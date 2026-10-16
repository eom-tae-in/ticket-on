package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 행사 위저드 값 조회 — 들어오는 포트. 나갔다 들어오면 이어서 한다(docs/03 "임시저장은 자동으로") — 저장된 단계의 값과 진행 단계를 돌려준다.
 * 5단계를 건너뛰었으면 처음 값이 담긴다. 행사를 고칠 수 있는 사람이 본다({@link StaffAction#EDIT_EVENT}, 운영진 누구나). 보기만 하므로 조작
 * 기록을 남기지 않는다.
 */
public interface GetEventWizardUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     */
    EventWizardResult get(GetEventWizardQuery query);
}
