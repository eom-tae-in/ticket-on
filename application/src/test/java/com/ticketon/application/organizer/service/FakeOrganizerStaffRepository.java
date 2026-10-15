package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffMembership;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 메모리 운영진 명단 저장소 — 주최자마다 소속을 갖는다. 소속이 없는 주최자는 빈 명단이다. */
final class FakeOrganizerStaffRepository implements OrganizerStaffRepository {

    private final Map<Long, List<StaffMembership>> membershipsByOrganizer = new LinkedHashMap<>();

    @Override
    public void addOpened(OrganizerStaff openedStaff) {
        if (membershipsByOrganizer.containsKey(openedStaff.organizerId())) {
            throw new IllegalArgumentException("이미 소속 줄이 있는 주최자입니다: " + openedStaff.organizerId());
        }
        store(openedStaff);
    }

    /** 초대 수락 · 나가기 같은 기능을 거치지 않고 그 명단을 바로 둔다. */
    void store(OrganizerStaff staff) {
        membershipsByOrganizer.put(staff.organizerId(), staff.memberships());
    }

    @Override
    public OrganizerStaff findByOrganizerId(long organizerId) {
        return OrganizerStaff.restore(organizerId, membershipsByOrganizer.getOrDefault(organizerId, List.of()));
    }
}
