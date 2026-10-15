package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 주최자명 변경 — 들어오는 포트. 소유자만 바꾸고({@link StaffAction#RENAME_ORGANIZER}) 관리자 확인 없이 바로 적용된다(docs/01 "공개 페이지 —
 * 주최자명 변경", docs/11 "구조", docs/13 "주최자명 변경 ※ 소유자만. 바로 적용, 이력 남음 · 운영진·관리자에게 알림").
 * <p>
 * 바꾸면 이력(언제, 무엇에서 무엇으로, 누가)이 남고, 운영진 전원에게 '주최자명 변경' 메일이 가고(docs/12 "주최자 대상"), 관리자 처리 목록에
 * '주최자명 변경(사후 확인)'이 쌓이고(docs/12 "관리자 대상"), 운영진 조작 기록에 남는다. 지금 이름과 같으면 아무것도 바꾸지 않고 이력 · 메일 ·
 * 기록도 없다.
 */
public interface RenameOrganizerUseCase {

    /**
     * @return 바꾼 뒤의 주최자명
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나, 운영자이거나, 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#OWNER_ONLY_ACTION},
     *                                   {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    RenameOrganizerResult rename(RenameOrganizerCommand command);
}
