package com.ticketon.domain.organizer;

import com.ticketon.domain.error.RuleViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 한 사람의 소속 — 그 사람이 소속됐던 모든 주최자의 소속(지난 소속 포함).
 * <p>
 * <b>한 사람이 여러 주최자에 소속될 수 있다</b>(docs/01 "운영자 초대"). 막으면 두 동아리를 돕는 사람이 이메일을 두 개 만들어야 한다.
 * 주최자마다 역할이 따로다. 운영진 프로필의 "참여한 주최자"({@link StaffPublicProfile})와 주최자 전환이 이 목록에서 나온다.
 * <p>
 * 운영진 로그인 탈퇴 규칙을 지킨다(docs/01 "탈퇴와 주최자 닫기"):
 * <ul>
 *   <li><b>운영자</b>는 언제든 탈퇴한다. 소속 주최자에서 빠진다({@link OrganizerStaff#leave}).</li>
 *   <li><b>소유자</b>는 먼저 소유자를 넘기거나 주최자를 닫아야 한다. 닫으면 운영진이 전원 빠지므로 지금 소유자인 주최자가 남지 않는다.</li>
 * </ul>
 *
 * @param staffAccountId 그 사람의 운영진 계정 id
 * @param memberships    그 사람의 모든 소속
 */
public record StaffAffiliations(long staffAccountId, List<StaffMembership> memberships) {

    public StaffAffiliations {
        if (staffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + staffAccountId);
        }
        Objects.requireNonNull(memberships, "memberships");
        memberships = List.copyOf(memberships);
        Set<Long> activeOrganizers = new HashSet<>();
        for (StaffMembership membership : memberships) {
            if (membership.staffAccountId() != staffAccountId) {
                throw new IllegalArgumentException("다른 사람(" + membership.staffAccountId() + ")의 소속이 섞였습니다");
            }
            if (membership.isActive() && !activeOrganizers.add(membership.organizerId())) {
                throw new IllegalArgumentException("한 주최자에 지금 소속이 둘 이상입니다: 주최자 " + membership.organizerId());
            }
        }
    }

    /** 지금 소속된 주최자의 소속. */
    public List<StaffMembership> activeMemberships() {
        return memberships.stream().filter(StaffMembership::isActive).toList();
    }

    /** 지금 소유자인 주최자의 id. */
    public List<Long> ownedOrganizerIds() {
        return activeMemberships().stream()
                .filter(membership -> membership.role() == StaffRole.OWNER)
                .map(StaffMembership::organizerId)
                .toList();
    }

    /** 운영진 로그인을 탈퇴할 수 있는지 — 지금 소유자인 주최자가 하나도 없다. */
    public boolean canWithdraw() {
        return ownedOrganizerIds().isEmpty();
    }

    /**
     * 운영진 로그인을 탈퇴할 수 있는지 확인한다. 통과하면 지금 소속된 주최자마다 나가게 한 뒤 탈퇴한다.
     *
     * @throws RuleViolationException 지금 소유자인 주최자가 있으면({@link OrganizerErrorCode#OWNER_MUST_HAND_OVER_FIRST})
     */
    public void requireWithdrawable() {
        List<Long> ownedOrganizerIds = ownedOrganizerIds();
        if (!ownedOrganizerIds.isEmpty()) {
            throw new RuleViolationException(OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST,
                    "운영진 " + staffAccountId + "이 소유자인 주최자: " + ownedOrganizerIds);
        }
    }

    /** 지금 소속된 주최자의 id — 탈퇴할 때 이 주최자들에서 나간다. */
    public List<Long> activeOrganizerIds() {
        return activeMemberships().stream().map(StaffMembership::organizerId).toList();
    }

    /** 그 주최자에 지금 소속돼 있는지. 나왔거나 소속된 적이 없으면 false. */
    public boolean isActiveIn(long organizerId) {
        return activeMemberships().stream().anyMatch(membership -> membership.organizerId() == organizerId);
    }

    /** 소속된 적이 있는 모든 주최자의 id — 지난 소속 포함, 겹치지 않게 소속 목록의 순서대로. 운영진 프로필의 참여한 주최자가 이 주최자들이다. */
    public List<Long> participatedOrganizerIds() {
        return memberships.stream().map(StaffMembership::organizerId).distinct().toList();
    }
}
