package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffPermission;

/**
 * 운영진 권한 확인 — 들어오는 포트. 주최 관리 기능의 서비스가 일을 하기 전에 부른다(organizer는 ⓪층이라 행사 · 초대권 · 검표 · 행사 변경
 * 등 모든 층의 서비스가 부를 수 있다). "이 운영진이 이 주최자에 지금 소속돼 있고, 그 역할로 이 일을 할 수 있고, 주최자가 활동 중인지"를 이
 * 한 곳에서 판정한다({@link StaffPermission#check}, docs/01 "권한 두 가지").
 * <pre>{@code
 * @Transactional
 * public IssueInvitationsResult issue(IssueInvitationsCommand command) {
 *     Instant now = clock.instant();
 *     StaffPermission permission = checkStaffPermissionUseCase.check(
 *             new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.MANAGE_INVITATIONS));
 *     Session session = sessionRepository.findById(command.sessionId())
 *             .filter(found -> permission.covers(found.organizerId()))   // 다른 주최자의 회차면 없는 것으로
 *             .orElseThrow(() -> new NotFoundException(...));
 *     ... 발권 ...
 *     recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, 대상 종류, 대상 id, "초대권 10장 발권", now));
 * }
 * }</pre>
 * 경로의 주최자 id는 요청한 사람이 바꿀 수 있다. 그래서 주최자 안의 자원을 다룰 때는 그 자원이 확인한 주최자의 것인지도 본다
 * ({@link StaffPermission#covers}).
 */
public interface CheckStaffPermissionUseCase {

    /**
     * @return 확인한 권한 — 그 사람의 지금 역할을 담는다. 조작을 마친 뒤 조작 기록({@link RecordStaffOperationUseCase})에 그대로 넘긴다.
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니면 — 없는 주최자도 같다({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}),
     *                                   운영자가 소유자만 하는 일을 하려 하면({@link OrganizerErrorCode#OWNER_ONLY_ACTION}),
     *                                   주최자가 활동 중이 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    StaffPermission check(CheckStaffPermissionQuery query);
}
