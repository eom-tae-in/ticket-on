package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.StaffAction;
import java.util.Objects;

/**
 * 운영진 권한 확인 입력.
 *
 * @param actingStaff 일하려는 운영진과 주최자
 * @param action      하려는 일
 */
public record CheckStaffPermissionQuery(ActingStaff actingStaff, StaffAction action) {

    public CheckStaffPermissionQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(action, "action");
    }
}
