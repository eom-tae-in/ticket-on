package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffPermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 권한 확인 — 판정(소속 → 역할 → 주최자 상태)은 {@link StaffPermission#check}가 하고, 여기서는 주최자와 그 운영진 명단을 읽어 넘긴다.
 * <p>
 * <b>없는 주최자도 "이 주최자의 운영진이 아닙니다"로 막는다.</b> 주최자 id를 바꿔 넣어 보며 어느 주최자가 있는지 알아낼 수 없게, 있지만
 * 소속되지 않은 주최자와 같은 답을 준다. 부르는 서비스의 트랜잭션이 있으면 그 안에서 읽는다 — 확인과 조작이 같은 트랜잭션에 든다.
 */
@Service
public class CheckStaffPermissionService implements CheckStaffPermissionUseCase {

    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;

    public CheckStaffPermissionService(OrganizerRepository organizerRepository, OrganizerStaffRepository staffRepository) {
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StaffPermission check(CheckStaffPermissionQuery query) {
        ActingStaff actingStaff = query.actingStaff();
        Organizer organizer = organizerRepository.findById(actingStaff.organizerId())
                .orElseThrow(() -> new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF,
                        "없는 주최자 " + actingStaff.organizerId() + ", 운영진 " + actingStaff.staffAccountId()));
        OrganizerStaff staff = staffRepository.findByOrganizerId(actingStaff.organizerId());
        return StaffPermission.check(organizer, staff, actingStaff.staffAccountId(), query.action());
    }
}
