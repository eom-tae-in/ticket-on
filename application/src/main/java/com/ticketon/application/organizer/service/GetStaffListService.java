package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.GetStaffListQuery;
import com.ticketon.application.organizer.port.in.GetStaffListUseCase;
import com.ticketon.application.organizer.port.in.StaffListResult;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.StaffListQueryPort;
import com.ticketon.domain.organizer.StaffAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 목록 조회 — 그 주최자의 운영진인지 확인한 뒤, 읽기 전용이라 도메인을 거치지 않고 조회 포트가 돌려준 칸을 옮긴다. 보기만 하므로
 * 조작 기록을 남기지 않는다.
 */
@Service
public class GetStaffListService implements GetStaffListUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final StaffListQueryPort staffListQueryPort;

    public GetStaffListService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            StaffListQueryPort staffListQueryPort) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.staffListQueryPort = staffListQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public StaffListResult getStaffList(GetStaffListQuery query) {
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(query.actingStaff(), StaffAction.VIEW_STAFF));
        return new StaffListResult(staffListQueryPort.currentStaffOf(permission.organizerId()).stream()
                .map(member -> new StaffListResult.Member(member.staffAccountId(), member.nickname(), member.role(),
                        member.since()))
                .toList());
    }
}
