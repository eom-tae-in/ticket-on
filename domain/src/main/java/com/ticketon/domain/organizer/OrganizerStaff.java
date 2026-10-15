package com.ticketon.domain.organizer;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 한 주최자의 운영진 명단 — 소유자 1명 · 운영자 N명과 지난 소속 이력(docs/01 "주최자와 운영진", docs/14 "전체 관계도").
 * <p>
 * 지켜지는 것:
 * <ul>
 *   <li><b>소유자는 언제나 1명이다.</b> 배상과 정산 계좌의 책임질 사람이 분명해야 한다(docs/01, docs/99 B "소유자 여러 명"). 운영진이 남아
 *       있는 동안 지금 소유자는 꼭 1명이다. 운영진이 아무도 남지 않는 것은 주최자가 닫히거나 영구 정지됐을 때뿐이다.</li>
 *   <li><b>한 사람은 이 주최자에 한 역할로만 소속된다.</b> 다른 주최자에 소속되는 것은 상관없다 — 한 사람이 여러 주최자에 소속될 수 있다.</li>
 *   <li><b>나가도 이력은 남는다.</b> 소속을 지우지 않고 끝낸다({@link StaffMembership}).</li>
 * </ul>
 * 하는 일:
 * <ul>
 *   <li>{@link #startWithOwner 승인} — 신청한 사람이 소유자가 된다.</li>
 *   <li>{@link #requirePermission 권한 확인} — 지금 소속돼 있고 그 역할로 할 수 있는 일인지({@link StaffRole#can}). 주최 관리 기능은
 *       주최자 상태까지 함께 보는 {@link StaffPermission#check}로 확인한다.</li>
 *   <li>{@link #addOperator 운영자 들이기} — 초대를 수락한 사람이 운영자가 된다. 운영자는 심사를 받지 않는다.</li>
 *   <li>{@link #transferOwnership 소유자 넘기기} — 받는 사람은 이미 그 주최자의 운영자여야 하고, 넘긴 사람은 운영자로 남는다.</li>
 *   <li>{@link #leave 나가기}·{@link #removeOperator 내보내기} — 운영자는 언제든 나가고, 소유자가 내보낸다. 소유자는 먼저 넘겨야 나간다.</li>
 * </ul>
 * 주최자 상태(심사 중 · 닫힘 등)는 {@link Organizer}가 본다. 시각은 인자로 받는다.
 */
public final class OrganizerStaff {

    private final long organizerId;
    private final List<StaffMembership> memberships;

    private OrganizerStaff(long organizerId, List<StaffMembership> memberships) {
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        this.organizerId = organizerId;
        this.memberships = new ArrayList<>(memberships);
        requireConsistent();
    }

    /**
     * 승인된 주최자의 명단을 연다 — 신청한 사람이 소유자가 된다(docs/01 "주최자 신청과 승인").
     *
     * @param ownerStaffAccountId 신청한 사람의 운영진 계정 id
     * @param now                 승인 일시 — 소유자 소속의 시작
     */
    public static OrganizerStaff startWithOwner(long organizerId, long ownerStaffAccountId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new OrganizerStaff(organizerId,
                List.of(StaffMembership.startingAt(organizerId, ownerStaffAccountId, StaffRole.OWNER, now)));
    }

    /**
     * 저장소에서 읽은 소속(지난 소속 포함)으로 명단을 되살린다.
     *
     * @throws IllegalArgumentException 다른 주최자의 소속이 섞였거나, 한 사람에게 지금 소속이 둘 이상이거나, 운영진이 남아 있는데
     *                                  지금 소유자가 1명이 아니면
     */
    public static OrganizerStaff restore(long organizerId, List<StaffMembership> memberships) {
        Objects.requireNonNull(memberships, "memberships");
        return new OrganizerStaff(organizerId, memberships);
    }

    /**
     * 그 사람이 지금 이 주최자에서 그 일을 할 수 있는지 확인한다(docs/01 "권한 두 가지").
     *
     * @throws PermissionDeniedException 지금 소속된 운영진이 아니면({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}),
     *                                   운영자가 소유자만 하는 일을 하려 하면({@link OrganizerErrorCode#OWNER_ONLY_ACTION})
     */
    public void requirePermission(long staffAccountId, StaffAction action) {
        Objects.requireNonNull(action, "action");
        StaffRole role = roleOf(staffAccountId).orElseThrow(() -> new PermissionDeniedException(
                OrganizerErrorCode.NOT_ORGANIZER_STAFF, notStaffDetail(staffAccountId)));
        if (!role.can(action)) {
            throw new PermissionDeniedException(OrganizerErrorCode.OWNER_ONLY_ACTION,
                    "주최자 " + organizerId + ", 운영자 " + staffAccountId + ", 일: " + action);
        }
    }

    /**
     * 초대를 수락한 사람을 운영자로 들인다. 운영자는 심사를 받지 않는다(docs/01 "운영자 초대"). 전에 나갔던 사람이면 새 소속으로 다시 시작하고
     * 지난 소속은 이력으로 남는다.
     *
     * @return 막 시작한 운영자 소속 — 저장소에 이 한 줄을 더한다
     * @throws RuleViolationException 이미 이 주최자에 소속돼 있으면({@link OrganizerErrorCode#ALREADY_ORGANIZER_STAFF})
     * @throws IllegalStateException  소유자가 없는 명단(닫힘·영구 정지된 주최자)이면 — 주최자 상태를 먼저 본다
     */
    public StaffMembership addOperator(long staffAccountId, Instant now) {
        Objects.requireNonNull(now, "now");
        if (currentOwner().isEmpty()) {
            throw new IllegalStateException("소유자가 없는 주최자에는 운영자를 들일 수 없습니다: 주최자 " + organizerId);
        }
        if (activeMembershipOf(staffAccountId).isPresent()) {
            throw new RuleViolationException(OrganizerErrorCode.ALREADY_ORGANIZER_STAFF,
                    "주최자 " + organizerId + ", 운영진 " + staffAccountId);
        }
        StaffMembership joined = StaffMembership.startingAt(organizerId, staffAccountId, StaffRole.OPERATOR, now);
        memberships.add(joined);
        return joined;
    }

    /**
     * 소유자를 넘긴다 — 관리자를 거치지 않는다(docs/01 "소유자 넘기기").
     * <ul>
     *   <li>받는 사람은 <b>이미 이 주최자의 운영자</b>여야 한다. 바깥 사람에게 바로 넘길 수 없다.</li>
     *   <li>넘긴 사람은 <b>운영자로 남는다.</b></li>
     * </ul>
     * 두 사람 모두 그때까지의 소속을 끝내고 새 역할의 소속을 같은 시각에 시작한다. 소유자는 넘기기 전에도 뒤에도 1명이다.
     *
     * @param requestedByStaffAccountId 넘기는 사람 — 지금 소유자
     * @param newOwnerStaffAccountId    받는 사람
     * @return 넘기기 한 건 — 운영자 전원과 관리자에게 알리는 데 쓴다
     * @throws PermissionDeniedException 넘기는 사람이 지금 소유자가 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#OWNER_ONLY_ACTION})
     * @throws RuleViolationException    받는 사람이 이 주최자의 운영자가 아니면({@link OrganizerErrorCode#OWNERSHIP_RECEIVER_NOT_OPERATOR})
     */
    public OwnershipTransfer transferOwnership(long requestedByStaffAccountId, long newOwnerStaffAccountId, Instant now) {
        Objects.requireNonNull(now, "now");
        requirePermission(requestedByStaffAccountId, StaffAction.TRANSFER_OWNERSHIP);
        StaffMembership receiver = activeMembershipOf(newOwnerStaffAccountId)
                .filter(membership -> membership.role() == StaffRole.OPERATOR)
                .orElseThrow(() -> new RuleViolationException(OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR,
                        "주최자 " + organizerId + ", 받는 사람 " + newOwnerStaffAccountId));
        StaffMembership owner = activeMembershipOf(requestedByStaffAccountId).orElseThrow();
        changeRole(owner, StaffRole.OPERATOR, now);
        changeRole(receiver, StaffRole.OWNER, now);
        requireConsistent();
        return new OwnershipTransfer(organizerId, requestedByStaffAccountId, newOwnerStaffAccountId, now);
    }

    /**
     * 주최자에서 나간다. 운영자는 언제든 나갈 수 있고, 소유자는 먼저 소유자를 넘기거나 주최자를 닫아야 한다(docs/01 "탈퇴와 주최자 닫기").
     * 운영진 로그인을 탈퇴할 때도 소속 주최자마다 이것으로 나간다({@link StaffAffiliations}).
     *
     * @return 끝난 소속 — 사람·역할·시작은 그대로이고 끝난 일시가 붙었다. 저장소에 이 끝을 남긴다
     * @throws PermissionDeniedException 지금 소속된 운영진이 아니면({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF})
     * @throws RuleViolationException    소유자면({@link OrganizerErrorCode#OWNER_MUST_HAND_OVER_FIRST})
     */
    public StaffMembership leave(long staffAccountId, Instant now) {
        Objects.requireNonNull(now, "now");
        StaffMembership membership = activeMembershipOf(staffAccountId).orElseThrow(() -> new PermissionDeniedException(
                OrganizerErrorCode.NOT_ORGANIZER_STAFF, notStaffDetail(staffAccountId)));
        if (membership.role() == StaffRole.OWNER) {
            throw new RuleViolationException(OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST,
                    "주최자 " + organizerId + "의 소유자 " + staffAccountId);
        }
        return end(membership, now);
    }

    /**
     * 운영자를 내보낸다 — 소유자만 한다({@link StaffAction#REMOVE_STAFF}). 내보내진 사람의 소속 이력은 남는다.
     *
     * @param requestedByStaffAccountId 내보내는 사람 — 소유자
     * @param operatorStaffAccountId    내보낼 운영자
     * @return 끝난 소속 — 저장소에 이 끝을 남긴다
     * @throws PermissionDeniedException 내보내는 사람이 소유자가 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#OWNER_ONLY_ACTION})
     * @throws RuleViolationException    내보낼 사람이 이 주최자의 운영자가 아니면({@link OrganizerErrorCode#NOT_AN_OPERATOR})
     */
    public StaffMembership removeOperator(long requestedByStaffAccountId, long operatorStaffAccountId, Instant now) {
        Objects.requireNonNull(now, "now");
        requirePermission(requestedByStaffAccountId, StaffAction.REMOVE_STAFF);
        StaffMembership operator = activeMembershipOf(operatorStaffAccountId)
                .filter(membership -> membership.role() == StaffRole.OPERATOR)
                .orElseThrow(() -> new RuleViolationException(OrganizerErrorCode.NOT_AN_OPERATOR,
                        "주최자 " + organizerId + ", 운영진 " + operatorStaffAccountId));
        return end(operator, now);
    }

    /** 그 사람의 지금 역할. 지금 소속돼 있지 않으면 비어 있다. */
    public Optional<StaffRole> roleOf(long staffAccountId) {
        return activeMembershipOf(staffAccountId).map(StaffMembership::role);
    }

    /** 지금 소유자의 운영진 계정 id. 운영진이 아무도 남지 않았으면(닫힘·영구 정지) 비어 있다. */
    public OptionalLong currentOwner() {
        return memberships.stream()
                .filter(membership -> membership.isActive() && membership.role() == StaffRole.OWNER)
                .mapToLong(StaffMembership::staffAccountId)
                .findFirst();
    }

    /** 지금 소속된 운영진의 소속 — 소유자와 운영자. */
    public List<StaffMembership> activeMemberships() {
        return memberships.stream().filter(StaffMembership::isActive).toList();
    }

    /** 모든 소속 — 끝난 소속(이력)까지. */
    public List<StaffMembership> memberships() {
        return List.copyOf(memberships);
    }

    public long organizerId() {
        return organizerId;
    }

    private Optional<StaffMembership> activeMembershipOf(long staffAccountId) {
        return memberships.stream()
                .filter(membership -> membership.isActive() && membership.staffAccountId() == staffAccountId)
                .findFirst();
    }

    private void changeRole(StaffMembership membership, StaffRole newRole, Instant now) {
        end(membership, now);
        memberships.add(StaffMembership.startingAt(organizerId, membership.staffAccountId(), newRole, now));
    }

    private StaffMembership end(StaffMembership membership, Instant now) {
        StaffMembership ended = membership.endAt(now);
        memberships.set(memberships.indexOf(membership), ended);
        return ended;
    }

    private String notStaffDetail(long staffAccountId) {
        return "주최자 " + organizerId + ", 운영진 " + staffAccountId;
    }

    private void requireConsistent() {
        Set<Long> activeStaff = new HashSet<>();
        int activeOwners = 0;
        for (StaffMembership membership : memberships) {
            if (membership.organizerId() != organizerId) {
                throw new IllegalArgumentException("다른 주최자(" + membership.organizerId() + ")의 소속이 섞였습니다");
            }
            if (!membership.isActive()) {
                continue;
            }
            if (!activeStaff.add(membership.staffAccountId())) {
                throw new IllegalArgumentException("한 사람에게 지금 소속이 둘 이상입니다: 운영진 " + membership.staffAccountId());
            }
            if (membership.role() == StaffRole.OWNER) {
                activeOwners++;
            }
        }
        if (!activeStaff.isEmpty() && activeOwners != 1) {
            throw new IllegalArgumentException("운영진이 남아 있으면 지금 소유자는 1명이어야 합니다: " + activeOwners + "명");
        }
    }
}
