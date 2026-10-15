package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffPermission;
import com.ticketon.domain.organizer.StaffRole;

/**
 * 운영진 권한 확인 결과 — 그 운영진이 지금 그 주최자에서 그 일을 해도 된다({@link CheckStaffPermissionUseCase}). 조작을 마친 뒤 조작 기록
 * ({@link RecordStaffOperationCommand})에 그대로 넘긴다 — 누가 어느 주최자에서 어떤 역할로 무슨 일을 했는지가 확인한 그대로 남는다.
 * <p>
 * 판정 규칙은 도메인({@link StaffPermission})이 갖는다. 이 값을 만들 때도 같은 규칙으로 확인한다 — 역할로 할 수 없는 일을 담은 결과는 만들 수
 * 없다.
 *
 * @param organizerId    주최자 id
 * @param staffAccountId 운영진 계정 id
 * @param role           확인할 때의 그 사람의 역할
 * @param action         확인한 일
 */
public record StaffPermissionResult(long organizerId, long staffAccountId, StaffRole role, StaffAction action) {

    /**
     * @throws IllegalArgumentException 1보다 작은 id이거나, 역할로 할 수 없는 일이면({@link StaffPermission})
     */
    public StaffPermissionResult {
        new StaffPermission(organizerId, staffAccountId, role, action);
    }

    /** 도메인의 확인 결과를 옮긴다. */
    public static StaffPermissionResult from(StaffPermission permission) {
        return new StaffPermissionResult(permission.organizerId(), permission.staffAccountId(), permission.role(),
                permission.action());
    }

    /**
     * 그 자원이 확인한 주최자의 것인지({@link StaffPermission#covers}). 다른 주최자의 자원이면 이 주최자 안에 없는 것으로 다룬다 — 경로의
     * 주최자 id만 자기 주최자로 바꿔 다른 주최자의 자원에 닿는 길을 막는다.
     *
     * @param resourceOrganizerId 그 자원이 속한 주최자의 id
     */
    public boolean covers(long resourceOrganizerId) {
        return new StaffPermission(organizerId, staffAccountId, role, action).covers(resourceOrganizerId);
    }
}
