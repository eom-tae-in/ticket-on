package com.ticketon.application.admin.service;

import com.ticketon.application.notification.port.out.OrganizerStaffContactPort;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.notification.OrganizerStaffContacts;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import java.util.List;
import java.util.Optional;

/**
 * 운영진 연락처 조회 — 메모리 명단 저장소의 지금 소속과 메모리 운영진 계정 저장소의 이메일로 펼친다(표의 조회와 같이 나간 운영진은 넣지 않고,
 * 지금 소유자가 없으면 비어 있다).
 */
final class FakeOrganizerStaffContactPort implements OrganizerStaffContactPort {

    private final FakeOrganizerStaffRepository staffRepository;
    private final FakeStaffAccountRepository staffAccountRepository;

    FakeOrganizerStaffContactPort(FakeOrganizerStaffRepository staffRepository,
            FakeStaffAccountRepository staffAccountRepository) {
        this.staffRepository = staffRepository;
        this.staffAccountRepository = staffAccountRepository;
    }

    @Override
    public Optional<OrganizerStaffContacts> findStaffContacts(long organizerId) {
        OrganizerStaff staff = staffRepository.findByOrganizerId(organizerId);
        if (staff.currentOwner().isEmpty()) {
            return Optional.empty();
        }
        List<EmailAddress> operators = staff.activeMemberships().stream()
                .filter(membership -> membership.role() == StaffRole.OPERATOR)
                .map(StaffMembership::staffAccountId)
                .map(this::emailOf)
                .toList();
        return Optional.of(new OrganizerStaffContacts(emailOf(staff.currentOwner().getAsLong()), operators));
    }

    private EmailAddress emailOf(long staffAccountId) {
        return staffAccountRepository.findById(staffAccountId).orElseThrow().personalInformation().email();
    }
}
