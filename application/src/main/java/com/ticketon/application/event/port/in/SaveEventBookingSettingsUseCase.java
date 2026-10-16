package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 위저드 5단계(공개 수준 · 예매 설정 · 취소 규정 조정) 저장 — 들어오는 포트. 5단계는 전부 처음 값이 있어 건너뛸 수 있다 — 건너뛰면 행사를 만들 때의
 * 처음 값 그대로다(docs/03 "5~6단계는 전부 기본값이 있으니 건너뛸 수 있습니다"). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 같은 값을 몇 번 저장해도 안전하다 — 바뀐 것이 없으면 저장도 조작 기록도 없다. 걸려 있던 암호를 다시 적어도 바뀐 것이 없다. 바뀐 항목은 지금
 * 상태의 수정 범위로 확인한다({@link Event#saveBookingStep}).
 */
public interface SaveEventBookingSettingsUseCase {

    /**
     * @return 저장한 뒤의 위저드 값
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    암호 보호인데 암호가 없으면({@link EventErrorCode#EVENT_PASSWORD_REQUIRED}), 바뀐 항목을 지금 상태에서
     *                                   고칠 수 없으면
     */
    EventWizardResult save(SaveEventBookingSettingsCommand command);
}
