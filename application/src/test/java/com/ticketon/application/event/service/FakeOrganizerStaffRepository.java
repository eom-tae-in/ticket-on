package com.ticketon.application.event.service;

import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.StaffAffiliations;
import com.ticketon.domain.organizer.StaffMembership;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메모리 운영진 명단 저장소 — 권한 확인이 읽는 명단과, 잠금을 기다리는 사이 내보내진 일을 흉내 내는 소속 끝내기만 한다. 소속이 없는 주최자는 빈
 * 명단이다.
 */
final class FakeOrganizerStaffRepository implements OrganizerStaffRepository {

    private final Map<Long, List<StaffMembership>> membershipsByOrganizer = new LinkedHashMap<>();

    /** 초대 수락 같은 기능을 거치지 않고 그 명단을 바로 둔다. */
    void store(OrganizerStaff staff) {
        membershipsByOrganizer.put(staff.organizerId(), new ArrayList<>(staff.memberships()));
    }

    @Override
    public OrganizerStaff findByOrganizerId(long organizerId) {
        return OrganizerStaff.restore(organizerId, membershipsByOrganizer.getOrDefault(organizerId, List.of()));
    }

    @Override
    public void saveEnded(StaffMembership endedMembership) {
        List<StaffMembership> memberships = membershipsByOrganizer.get(endedMembership.organizerId());
        for (int index = 0; index < memberships.size(); index++) {
            StaffMembership membership = memberships.get(index);
            if (membership.isActive() && membership.staffAccountId() == endedMembership.staffAccountId()) {
                memberships.set(index, endedMembership);
                return;
            }
        }
        throw new IllegalArgumentException("끝낼 지금 소속이 없습니다: " + endedMembership);
    }

    @Override
    public void addOpened(OrganizerStaff openedStaff) {
        throw new UnsupportedOperationException("행사 유스케이스는 명단을 열지 않습니다");
    }

    @Override
    public void addJoinedOperator(StaffMembership joinedMembership) {
        throw new UnsupportedOperationException("행사 유스케이스는 운영자를 들이지 않습니다");
    }

    @Override
    public void saveOwnershipTransfer(OwnershipTransfer transfer) {
        throw new UnsupportedOperationException("행사 유스케이스는 소유자를 넘기지 않습니다");
    }

    @Override
    public StaffAffiliations findAffiliationsOf(long staffAccountId) {
        throw new UnsupportedOperationException("행사 유스케이스는 소속 목록을 읽지 않습니다");
    }
}
