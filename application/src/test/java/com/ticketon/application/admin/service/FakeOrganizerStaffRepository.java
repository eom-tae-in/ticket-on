package com.ticketon.application.admin.service;

import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffMembership;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 메모리 운영진 명단 저장소 — 주최자마다 소속을 저장한 순서로 갖는다. 명단은 한 번만 연다. */
final class FakeOrganizerStaffRepository implements OrganizerStaffRepository {

    private final Map<Long, List<StaffMembership>> membershipsByOrganizer = new LinkedHashMap<>();

    @Override
    public void addOpened(OrganizerStaff openedStaff) {
        if (membershipsByOrganizer.containsKey(openedStaff.organizerId())) {
            throw new IllegalArgumentException("이미 소속 줄이 있는 주최자입니다: " + openedStaff.organizerId());
        }
        membershipsByOrganizer.put(openedStaff.organizerId(), openedStaff.memberships());
    }

    @Override
    public OrganizerStaff findByOrganizerId(long organizerId) {
        return OrganizerStaff.restore(organizerId, membershipsByOrganizer.getOrDefault(organizerId, List.of()));
    }
}
