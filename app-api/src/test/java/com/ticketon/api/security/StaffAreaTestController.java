package com.ticketon.api.security;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.StaffAction;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시험 전용 컨트롤러 — 주최자 하나의 일을 하는 주소에서 {@link CurrentStaff}로 받은 운영진 · 주최자를 그대로 돌려주거나, 운영진 권한 확인
 * 유스케이스에 넘긴다.
 * <p>
 * {@link TestComponent}라 컴포넌트 스캔에 잡히지 않는다. 쓰는 테스트가 {@code @Import}로 넣는다.
 */
@TestComponent
@RestController
class StaffAreaTestController {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;

    StaffAreaTestController(CheckStaffPermissionUseCase checkStaffPermissionUseCase) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
    }

    /** 받은 값을 "주체 이름 → 주최자 id"로 돌려준다. */
    @GetMapping(CurrentStaffArgumentResolver.ORGANIZER_PATH + "/test")
    String actingStaff(@CurrentStaff ActingStaff actingStaff) {
        return actingStaff.account().principalName() + " → " + actingStaff.organizerId();
    }

    /** 경로의 다른 자원 id와 함께 받는다. */
    @GetMapping(CurrentStaffArgumentResolver.ORGANIZER_PATH + "/test/events/{eventId}")
    String actingStaffWithResource(@CurrentStaff ActingStaff actingStaff, @PathVariable long eventId) {
        return actingStaff.account().principalName() + " → " + actingStaff.organizerId() + " / " + eventId;
    }

    /** 운영진 권한 확인 유스케이스에 그대로 넘기고, 확인한 역할을 돌려준다. */
    @GetMapping(CurrentStaffArgumentResolver.ORGANIZER_PATH + "/test/actions/{action}")
    String checkPermission(@CurrentStaff ActingStaff actingStaff, @PathVariable StaffAction action) {
        StaffPermissionResult permission =
                checkStaffPermissionUseCase.check(new CheckStaffPermissionQuery(actingStaff, action));
        return permission.role().name();
    }

    /** 검표 영역은 검표 계정도 들어오지만 {@link CurrentStaff}는 운영진만 받는다. */
    @GetMapping("/api/checkin/organizers/{organizerId}/test")
    String checkinAreaActingStaff(@CurrentStaff ActingStaff actingStaff) {
        return actingStaff.account().principalName() + " → " + actingStaff.organizerId();
    }

    /** 경로에 주최자 id가 없는 주소에 붙인 실수. */
    @GetMapping("/api/organizer/test/without-organizer-id")
    String withoutOrganizerId(@CurrentStaff ActingStaff actingStaff) {
        return "unreachable";
    }

    /** {@link CurrentStaff}를 지원하지 않는 타입에 붙인 실수. */
    @GetMapping(CurrentStaffArgumentResolver.ORGANIZER_PATH + "/test/wrong-type")
    String wrongType(@CurrentStaff AccountRef account) {
        return "unreachable";
    }
}
