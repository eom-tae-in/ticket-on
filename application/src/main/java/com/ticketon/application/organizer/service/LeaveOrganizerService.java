package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.LeaveOrganizerCommand;
import com.ticketon.application.organizer.port.in.LeaveOrganizerUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이 주최자에서 나가기 — 나가기 규칙(운영자는 언제든, 소유자는 먼저 넘겨야 함, 이력은 남음)은 {@link OrganizerStaff#leave}가 보고, 여기서는
 * 운영진 권한을 확인하고 명단을 읽어 넘긴 뒤 끝난 소속을 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 소속 끝내기 → 조작 기록 순서. 그 사이 소유자가 먼저 내보냈으면 끝내기 저장에서 충돌로 멈춘다.
 */
@Service
public class LeaveOrganizerService implements LeaveOrganizerUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerStaffRepository staffRepository;
    private final Clock clock;

    public LeaveOrganizerService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerStaffRepository staffRepository,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffRepository = staffRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void leave(LeaveOrganizerCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.LEAVE_ORGANIZER));
        long staffAccountId = permission.staffAccountId();

        StaffMembership ended = staffRepository.findByOrganizerId(permission.organizerId()).leave(staffAccountId, now);

        staffRepository.saveEnded(ended);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                StaffOperationTargetKind.STAFF_MEMBER, staffAccountId, "주최자에서 나감", now));
    }
}
