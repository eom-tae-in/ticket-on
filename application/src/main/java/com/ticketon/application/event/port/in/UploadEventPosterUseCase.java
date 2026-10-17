package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 행사 포스터 올리기 — 들어오는 포트. 위저드 1단계의 필수 항목이지만 행사를 만든 뒤 따로 올린다(docs/03 "기본 정보", "행사 만들기 위저드"). 운영진
 * 누구나 올린다({@link StaffAction#EDIT_EVENT}). 처음 올리기와 바꾸기가 같다.
 * <p>
 * 파일은 추측할 수 없는 열쇠를 이름으로 파일 저장소에 두고, 바꾸기 전 포스터의 파일은 커밋된 뒤 지운다. 포스터를 고칠 수 있는지는 지금 상태의
 * 수정 범위로 확인하고, 진행 단계 · 마지막 수정 시각은 1단계 저장처럼 다룬다({@link Event#changePoster}). 운영진 조작 기록에 남는다.
 */
public interface UploadEventPosterUseCase {

    /**
     * @return 올린 뒤의 위저드 값 — 포스터의 공개 주소를 담는다
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    지금 상태에서 포스터를 고칠 수 없으면 — 판매가 끝난 뒤
     *                                   ({@link EventErrorCode#EDIT_LOCKED_AFTER_SALES_CLOSED}), 지난 행사
     *                                   ({@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    EventWizardResult upload(UploadEventPosterCommand command);
}
