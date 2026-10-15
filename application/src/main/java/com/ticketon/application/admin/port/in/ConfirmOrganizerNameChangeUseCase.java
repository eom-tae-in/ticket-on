package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 주최자명 변경 사후 확인 — 들어오는 포트. 소유자가 주최자명을 바꾸면 관리자 처리 목록에 '주최자명 변경(사후 확인)'이 쌓이고(docs/12 "관리자
 * 대상 — 주최자명 변경 (사후 확인용)", docs/13 "처리 목록"), 관리자가 확인하면 목록에서 빠진다. 소유자가 바로 바꾼 일이라 이름을 되돌리지
 * 않는다 — 유명한 단체를 흉내 내는 이름이면 신고 · 정지 같은 다른 조치로 처리한다(docs/01 "주최자명을 소유자가 바로 바꿀 수 있게 한 이유").
 * 관리자 · 최고 관리자 모두 하고, 관리자 조작 기록에 남는다.
 */
public interface ConfirmOrganizerNameChangeUseCase {

    /**
     * @throws NotFoundException      없는 주최자명 변경 이력이면({@link OrganizerErrorCode#ORGANIZER_NAME_CHANGE_NOT_FOUND})
     * @throws RuleViolationException 이미 확인했으면({@link OrganizerErrorCode#ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED})
     * @throws ConflictException      다른 관리자가 거의 동시에 먼저 확인했으면({@link OrganizerErrorCode#ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED})
     */
    void confirm(ConfirmOrganizerNameChangeCommand command);
}
