package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 위저드 1단계(기본 정보) 저장 — 들어오는 포트. 단계를 넘길 때마다 부르는 자동 임시저장이다(docs/03 "임시저장은 자동으로"). 운영진 누구나 한다
 * ({@link StaffAction#EDIT_EVENT}).
 * <p>
 * <b>같은 값을 몇 번 저장해도 안전하다</b> — 바뀐 것이 없으면 저장도 조작 기록도 없고 마지막 수정 시각도 그대로다. 바뀐 항목은 지금 상태의 수정
 * 범위로 확인한다({@link Event#saveBasicInformationStep}). 두 사람이 거의 동시에 저장하면 차례로 처리된다 — 뒤의 저장이 앞의 저장이 끝난 값을
 * 읽는다.
 */
public interface SaveEventBasicInformationUseCase {

    /**
     * @return 저장한 뒤의 위저드 값
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    카테고리가 맞지 않거나, 바뀐 항목을 지금 상태에서 고칠 수 없으면
     */
    EventWizardResult save(SaveEventBasicInformationCommand command);
}
