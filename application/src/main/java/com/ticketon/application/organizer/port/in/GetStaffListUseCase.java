package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 운영진 목록 조회 — 들어오는 포트. 그 주최자의 운영진 누구나 본다(docs/13 "운영진 관리 — 운영진 목록"). 소유자는 이 목록에서 내보낼 운영자를
 * 고른다. 보기만 하므로 운영진 조작 기록에 남지 않는다.
 */
public interface GetStaffListUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    StaffListResult getStaffList(GetStaffListQuery query);
}
