package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.StaffAffiliations;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.persistence.jooq.tables.records.StaffMembershipRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 주최자 운영진 명단 저장소(staff_membership — V8) 어댑터. 소속 한 건이 한 줄이다.
 * <p>
 * 승인으로 막 연 명단(소유자 한 줄)을 넣고, 초대를 수락한 운영자 줄을 더하고, 나가기·내보내기·탈퇴로 끝난 줄에 끝난 일시를 남기고,
 * 소유자 넘기기로 두 사람의 역할을 바꾸고, 한 주최자의 소속과 한 사람의 소속을 모두 읽는다. 한 사람이 한 주최자에 지금 소속 하나, 지금
 * 소유자는 주최자마다 많아야 한 명이라는 약속은 표의 유일 색인이 마지막으로 지킨다.
 *
 * <h2>같은 사람이 거의 동시에 들어오기 — 수락 경합</h2>
 * 운영자 줄은 {@code INSERT … ON CONFLICT DO NOTHING}으로 넣는다. 그 사람의 지금 소속이 먼저 들어갔으면 유일 색인
 * (staff_membership_active_ux)에 걸려 한 줄도 들어가지 않고, 진 쪽이라 {@link ConflictException}(409)이 된다. 먼저 넣은 쪽이 아직 커밋하지
 * 않았으면 PostgreSQL이 그 결과를 기다렸다가 판정한다.
 *
 * <h2>내보내는 사이 스스로 나가기 — 끝내기 경합</h2>
 * 끝난 일시는 {@code UPDATE … WHERE ended_at IS NULL}로 남긴다. 바뀐 줄이 없으면 그 사이 먼저 끝난 것이라 {@link ConflictException}(409)이
 * 된다.
 *
 * <h2>같은 소유자가 거의 동시에 두 번 넘기기 — 넘기기 경합</h2>
 * 역할 교대는 넘긴 사람의 소유자 줄 끝내기 → 받은 사람의 운영자 줄 끝내기 → 넘긴 사람의 운영자 줄 · 받은 사람의 소유자 줄 시작 순서다. 첫
 * 끝내기가 {@code UPDATE … WHERE ended_at IS NULL}이라 같은 소유자 줄을 먼저 바꾼 쪽이 커밋할 때까지 늦은 쪽이 기다렸다가, 바뀐 줄이 없어
 * 진다(409 OWNERSHIP_ALREADY_TRANSFERRED). 받는 사람이 그 사이 나갔으면 둘째 끝내기에서 진다(409 MEMBERSHIP_ALREADY_ENDED). 새 줄은
 * {@code INSERT … ON CONFLICT DO NOTHING}으로 넣어, 지금 소유자 유일 색인(staff_membership_active_owner_ux)에 걸리면 넣지 않고 409가 된다.
 */
@Component
public class OrganizerStaffPersistenceAdapter implements OrganizerStaffRepository {

    private final DSLContext dsl;

    public OrganizerStaffPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void addOpened(OrganizerStaff openedStaff) {
        long organizerId = openedStaff.organizerId();
        if (dsl.fetchExists(STAFF_MEMBERSHIP, STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId))) {
            throw new IllegalArgumentException("이미 소속 줄이 있는 주최자입니다 — 명단은 승인 때 한 번만 엽니다: 주최자 " + organizerId);
        }
        for (StaffMembership membership : openedStaff.memberships()) {
            dsl.insertInto(STAFF_MEMBERSHIP)
                    .set(STAFF_MEMBERSHIP.ORGANIZER_ID, organizerId)
                    .set(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID, membership.staffAccountId())
                    .set(STAFF_MEMBERSHIP.ROLE, membership.role().name())
                    .set(STAFF_MEMBERSHIP.STARTED_AT, toDatabaseTime(membership.startedAt()))
                    .set(STAFF_MEMBERSHIP.ENDED_AT,
                            membership.endedAt() == null ? null : toDatabaseTime(membership.endedAt()))
                    .execute();
        }
    }

    @Override
    public void addJoinedOperator(StaffMembership joinedMembership) {
        if (joinedMembership.role() != StaffRole.OPERATOR || !joinedMembership.isActive()) {
            throw new IllegalArgumentException("막 시작한 운영자 소속만 더합니다: " + joinedMembership);
        }
        int inserted = dsl.insertInto(STAFF_MEMBERSHIP)
                .set(STAFF_MEMBERSHIP.ORGANIZER_ID, joinedMembership.organizerId())
                .set(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID, joinedMembership.staffAccountId())
                .set(STAFF_MEMBERSHIP.ROLE, joinedMembership.role().name())
                .set(STAFF_MEMBERSHIP.STARTED_AT, toDatabaseTime(joinedMembership.startedAt()))
                .onConflictDoNothing()
                .execute();
        if (inserted == 0) {
            throw new ConflictException(OrganizerErrorCode.ALREADY_ORGANIZER_STAFF,
                    "수락 경합 — 주최자 " + joinedMembership.organizerId() + ", 운영진 " + joinedMembership.staffAccountId()
                            + "의 지금 소속이 먼저 저장됨");
        }
    }

    @Override
    public void saveEnded(StaffMembership endedMembership) {
        if (endedMembership.isActive()) {
            throw new IllegalArgumentException("끝나지 않은 소속입니다: " + endedMembership);
        }
        int updated = endActive(endedMembership.organizerId(), endedMembership.staffAccountId(), endedMembership.role(),
                toDatabaseTime(endedMembership.endedAt()));
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.MEMBERSHIP_ALREADY_ENDED,
                    "끝내기 경합 — 주최자 " + endedMembership.organizerId() + ", 운영진 " + endedMembership.staffAccountId()
                            + "의 " + endedMembership.role() + " 소속이 먼저 끝남");
        }
    }

    @Override
    public void saveOwnershipTransfer(OwnershipTransfer transfer) {
        long organizerId = transfer.organizerId();
        long previousOwner = transfer.previousOwnerStaffAccountId();
        long newOwner = transfer.newOwnerStaffAccountId();
        OffsetDateTime transferredAt = toDatabaseTime(transfer.transferredAt());
        if (endActive(organizerId, previousOwner, StaffRole.OWNER, transferredAt) == 0) {
            throw ownershipRaceLost(transfer, "넘긴 사람의 소유자 소속이 먼저 끝남");
        }
        if (endActive(organizerId, newOwner, StaffRole.OPERATOR, transferredAt) == 0) {
            throw new ConflictException(OrganizerErrorCode.MEMBERSHIP_ALREADY_ENDED,
                    "넘기기 경합 — 주최자 " + organizerId + ", 받는 사람 " + newOwner + "의 운영자 소속이 먼저 끝남");
        }
        if (startActive(organizerId, previousOwner, StaffRole.OPERATOR, transferredAt) == 0) {
            throw ownershipRaceLost(transfer, "넘긴 사람의 지금 소속이 먼저 생김");
        }
        if (startActive(organizerId, newOwner, StaffRole.OWNER, transferredAt) == 0) {
            throw ownershipRaceLost(transfer, "다른 지금 소유자 줄이 먼저 생김");
        }
    }

    /** 소속을 시작한 순서(같으면 저장한 순서)로 되살린다. */
    @Override
    public OrganizerStaff findByOrganizerId(long organizerId) {
        return OrganizerStaff.restore(organizerId, membershipsWhere(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId)));
    }

    /** 소속을 시작한 순서(같으면 저장한 순서)로 되살린다. */
    @Override
    public StaffAffiliations findAffiliationsOf(long staffAccountId) {
        return new StaffAffiliations(staffAccountId,
                membershipsWhere(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID.eq(staffAccountId)));
    }

    private List<StaffMembership> membershipsWhere(Condition condition) {
        return dsl.selectFrom(STAFF_MEMBERSHIP)
                .where(condition)
                .orderBy(STAFF_MEMBERSHIP.STARTED_AT, STAFF_MEMBERSHIP.ID)
                .fetch(OrganizerStaffPersistenceAdapter::toMembership);
    }

    /** 그 사람의 그 역할 지금 소속을 끝낸다. 아직 끝나지 않은 줄만 바꾼다. */
    private int endActive(long organizerId, long staffAccountId, StaffRole role, OffsetDateTime endedAt) {
        return dsl.update(STAFF_MEMBERSHIP)
                .set(STAFF_MEMBERSHIP.ENDED_AT, endedAt)
                .where(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId))
                .and(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID.eq(staffAccountId))
                .and(STAFF_MEMBERSHIP.ROLE.eq(role.name()))
                .and(STAFF_MEMBERSHIP.ENDED_AT.isNull())
                .execute();
    }

    /** 지금 소속 한 줄을 시작한다. 유일 색인에 걸리면 넣지 않는다. */
    private int startActive(long organizerId, long staffAccountId, StaffRole role, OffsetDateTime startedAt) {
        return dsl.insertInto(STAFF_MEMBERSHIP)
                .set(STAFF_MEMBERSHIP.ORGANIZER_ID, organizerId)
                .set(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID, staffAccountId)
                .set(STAFF_MEMBERSHIP.ROLE, role.name())
                .set(STAFF_MEMBERSHIP.STARTED_AT, startedAt)
                .onConflictDoNothing()
                .execute();
    }

    private static ConflictException ownershipRaceLost(OwnershipTransfer transfer, String reason) {
        return new ConflictException(OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED,
                "넘기기 경합 — 주최자 " + transfer.organizerId() + ", 넘긴 사람 " + transfer.previousOwnerStaffAccountId()
                        + " → 받는 사람 " + transfer.newOwnerStaffAccountId() + ": " + reason);
    }

    private static StaffMembership toMembership(StaffMembershipRecord membership) {
        return new StaffMembership(membership.getOrganizerId(), membership.getStaffAccountId(),
                StaffRole.valueOf(membership.getRole()), membership.getStartedAt().toInstant(),
                toInstantOrNull(membership.getEndedAt()));
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstantOrNull(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
