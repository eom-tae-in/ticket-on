package com.ticketon.api.organizer;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffPermission;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시험 전용 조작 — 위 층의 주최 관리 서비스가 할 일을 그대로 한다. 한 트랜잭션 안에서 운영진 권한을 확인하고, (조작 자리), 확인한 권한으로
 * 조작 기록을 남긴다. 대상은 주최자 자신이고 요약은 "시험 조작 — 한 일"이다.
 * <p>
 * {@link TestComponent}라 컴포넌트 스캔에 잡히지 않는다. 쓰는 테스트가 {@code @Import}로 넣는다.
 */
@TestComponent
class StaffOperationTestService {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final Clock clock;

    StaffOperationTestService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.clock = clock;
    }

    @Transactional
    public StaffRole operate(ActingStaff actingStaff, StaffAction action, boolean failAfterRecording) {
        Instant now = clock.instant();
        StaffPermission permission = checkStaffPermissionUseCase.check(new CheckStaffPermissionQuery(actingStaff, action));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.ORGANIZER,
                actingStaff.organizerId(), "시험 조작 — " + action, now));
        if (failAfterRecording) {
            throw new IllegalStateException("기록을 남긴 뒤 조작이 실패했다");
        }
        return permission.role();
    }
}
