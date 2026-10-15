package com.ticketon.domain.organizer;

import com.ticketon.domain.error.PermissionDeniedException;
import java.util.Objects;

/**
 * 운영진 권한 확인 한 건 — 그 운영진이 지금 그 주최자에서 그 일을 해도 된다(docs/01 "권한 두 가지").
 * <p>
 * 모든 주최 관리 기능이 같은 방식으로 "이 사람이 이 주최자에서 이 일을 할 수 있나"를 확인한다({@link #check}). 셋을 본다.
 * <ol>
 *   <li><b>지금 소속</b> — 그 주최자에 지금 소속된 운영진인가. 소속된 적이 없거나 이미 나왔으면 막는다. 나온 주최자의 정산·현황은 볼 수
 *       없다(docs/01 "운영진 프로필 — 지난 소속"). 한 사람이 여러 주최자에 소속돼도 권한은 주최자마다 따로다 — 다른 주최자의 운영진도
 *       막는다.</li>
 *   <li><b>역할</b> — 운영자는 소유자만 하는 일(배상 · 정산 계좌 · 운영진 관리 등)을 못 한다({@link StaffRole#can}).</li>
 *   <li><b>주최자 상태</b> — 활동 중인 주최자에서만 운영한다. 심사 중 · 닫힘 · 영구 정지된 주최자에는 원래 지금 소속이 남지 않지만
 *       (승인 때 소속이 시작되고, 닫거나 영구 정지하면 운영진이 빠진다 — docs/01), 소속 줄이 남아 있더라도 막는다.</li>
 * </ol>
 * 소속을 먼저 본다 — 소속되지 않은 사람에게는 그 주최자의 상태를 알려 주지 않는다.
 * <p>
 * 확인을 통과하면 이 값이 남는다. 조작을 마친 뒤 운영진 조작 기록({@link StaffOperationRecord})을 이 값으로 만든다 — 누가 어느 주최자에서
 * 어떤 역할로 무슨 일을 했는지가 확인한 그대로 남는다. 주최자 안의 자원(행사 · 회차 · 초대권 등)을 다룰 때는 그 자원이 이 주최자의 것인지도
 * 본다({@link #covers}).
 * <p>
 * <b>정지 1~3단계의 제한은 여기서 보지 않는다.</b> 정지는 주최자 상태가 아니라 주최자에 걸리는 정지 기록이고, 정지 중에도 주최자는
 * 활동이다({@link OrganizerStatus}). 막히는 일(새 행사 만들기 · 작성 중 행사의 판매 시작, 관리자가 멈춘 판매의 재개)은 정지 기능이 그
 * 일을 하는 곳에서 정지 기록을 보고 막는다(docs/01 "정지 4단계").
 *
 * @param organizerId    주최자 id
 * @param staffAccountId 운영진 계정 id
 * @param role           확인할 때의 그 사람의 역할
 * @param action         확인한 일 — 그 역할로 할 수 있는 일이어야 한다
 */
public record StaffPermission(long organizerId, long staffAccountId, StaffRole role, StaffAction action) {

    public StaffPermission {
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        if (staffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + staffAccountId);
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(action, "action");
        if (!role.can(action)) {
            throw new IllegalArgumentException(role + "은 " + action + "을 할 수 없습니다");
        }
    }

    /**
     * 그 사람이 지금 그 주최자에서 그 일을 할 수 있는지 확인한다. 소속 → 역할 → 주최자 상태 순서로 보고, 처음 걸린 것으로 막는다.
     *
     * @param organizer      일하려는 주최자
     * @param staff          그 주최자의 운영진 명단 — 지난 소속까지
     * @param staffAccountId 일하려는 사람의 운영진 계정 id
     * @param action         하려는 일
     * @return 확인한 권한 — 그 사람의 지금 역할을 담는다
     * @throws PermissionDeniedException 지금 소속된 운영진이 아니면({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}), 운영자가 소유자만
     *                                   하는 일을 하려 하면({@link OrganizerErrorCode#OWNER_ONLY_ACTION}), 활동 중인 주최자가 아니면
     *                                   ({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws IllegalArgumentException  저장 전 주최자이거나, 명단이 다른 주최자의 것이면
     */
    public static StaffPermission check(Organizer organizer, OrganizerStaff staff, long staffAccountId,
            StaffAction action) {
        Objects.requireNonNull(organizer, "organizer");
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(action, "action");
        long organizerId = organizer.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 주최자의 권한은 확인하지 않습니다"));
        if (staff.organizerId() != organizerId) {
            throw new IllegalArgumentException(
                    "주최자 " + organizerId + "의 권한을 다른 주최자(" + staff.organizerId() + ")의 명단으로 확인하려 했습니다");
        }
        staff.requirePermission(staffAccountId, action);
        if (!organizer.isActive()) {
            throw new PermissionDeniedException(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE,
                    "주최자 " + organizerId + ", 상태: " + organizer.status() + ", 운영진 " + staffAccountId);
        }
        return new StaffPermission(organizerId, staffAccountId, staff.roleOf(staffAccountId).orElseThrow(), action);
    }

    /**
     * 그 자원이 이 주최자의 것인지 — 행사 · 회차 · 초대권처럼 주최자 안의 자원을 다룰 때 본다. 다른 주최자의 자원이면 이 주최자 안에 없는 것으로
     * 다룬다. 경로의 주최자 id만 자기 주최자로 바꿔 다른 주최자의 자원에 닿는 길을 막는다.
     *
     * @param resourceOrganizerId 그 자원이 속한 주최자의 id
     */
    public boolean covers(long resourceOrganizerId) {
        return organizerId == resourceOrganizerId;
    }
}
