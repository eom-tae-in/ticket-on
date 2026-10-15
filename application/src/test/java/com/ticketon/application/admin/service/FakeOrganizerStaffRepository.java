package com.ticketon.application.admin.service;

import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.StaffAffiliations;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메모리 운영진 명단 저장소 — 주최자마다 소속을 저장한 순서로 갖는다. 명단은 한 번만 연다. 소유자 이전 신청 승인의 역할 교대는 표처럼 넘긴 사람의
 * 소유자 줄과 받는 사람의 운영자 줄이 아직 끝나지 않았을 때만 한다.
 */
final class FakeOrganizerStaffRepository implements OrganizerStaffRepository {

    private final Map<Long, List<StaffMembership>> membershipsByOrganizer = new LinkedHashMap<>();

    @Override
    public void addOpened(OrganizerStaff openedStaff) {
        if (membershipsByOrganizer.containsKey(openedStaff.organizerId())) {
            throw new IllegalArgumentException("이미 소속 줄이 있는 주최자입니다: " + openedStaff.organizerId());
        }
        store(openedStaff);
    }

    /** 초대 수락 · 소유자 넘기기 같은 기능을 거치지 않고 그 명단을 바로 둔다. */
    void store(OrganizerStaff staff) {
        membershipsByOrganizer.put(staff.organizerId(), new ArrayList<>(staff.memberships()));
    }

    @Override
    public OrganizerStaff findByOrganizerId(long organizerId) {
        return OrganizerStaff.restore(organizerId, membershipsByOrganizer.getOrDefault(organizerId, List.of()));
    }

    @Override
    public void addJoinedOperator(StaffMembership joinedMembership) {
        throw new UnsupportedOperationException("관리자 기능에서는 운영자를 들이지 않습니다");
    }

    @Override
    public void saveEnded(StaffMembership endedMembership) {
        throw new UnsupportedOperationException("관리자 기능에서는 소속을 끝내지 않습니다");
    }

    @Override
    public void saveOwnershipTransfer(OwnershipTransfer transfer) {
        long organizerId = transfer.organizerId();
        List<StaffMembership> memberships = membershipsByOrganizer.getOrDefault(organizerId, new ArrayList<>());
        int previousOwnerIndex = activeIndexOf(memberships, transfer.previousOwnerStaffAccountId(), StaffRole.OWNER);
        if (previousOwnerIndex < 0) {
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED, "넘기기 경합(흉내)");
        }
        int newOwnerIndex = activeIndexOf(memberships, transfer.newOwnerStaffAccountId(), StaffRole.OPERATOR);
        if (newOwnerIndex < 0) {
            throw new ConflictException(OrganizerErrorCode.MEMBERSHIP_ALREADY_ENDED, "넘기기 경합(흉내)");
        }
        Instant transferredAt = transfer.transferredAt();
        StaffMembership previousOwner = memberships.get(previousOwnerIndex);
        StaffMembership newOwner = memberships.get(newOwnerIndex);
        memberships.set(previousOwnerIndex, ended(previousOwner, transferredAt));
        memberships.set(newOwnerIndex, ended(newOwner, transferredAt));
        memberships.add(new StaffMembership(organizerId, previousOwner.staffAccountId(), StaffRole.OPERATOR,
                transferredAt, null));
        memberships.add(new StaffMembership(organizerId, newOwner.staffAccountId(), StaffRole.OWNER, transferredAt, null));
    }

    @Override
    public StaffAffiliations findAffiliationsOf(long staffAccountId) {
        throw new UnsupportedOperationException("관리자 기능에서는 한 사람의 소속을 읽지 않습니다");
    }

    private static int activeIndexOf(List<StaffMembership> memberships, long staffAccountId, StaffRole role) {
        for (int index = 0; index < memberships.size(); index++) {
            StaffMembership membership = memberships.get(index);
            if (membership.isActive() && membership.staffAccountId() == staffAccountId && membership.role() == role) {
                return index;
            }
        }
        return -1;
    }

    private static StaffMembership ended(StaffMembership membership, Instant endedAt) {
        return new StaffMembership(membership.organizerId(), membership.staffAccountId(), membership.role(),
                membership.startedAt(), endedAt);
    }
}
