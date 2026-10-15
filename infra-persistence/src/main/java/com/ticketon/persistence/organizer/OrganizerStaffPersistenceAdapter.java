package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 주최자 운영진 명단 저장소(staff_membership — V8) 어댑터. 소속 한 건이 한 줄이다.
 * <p>
 * 승인으로 막 연 명단(소유자 한 줄)을 넣고, 초대를 수락한 운영자 줄을 더하고, 나가기·내보내기로 끝난 줄에 끝난 일시를 남기고, 한 주최자의
 * 소속을 모두 읽는다. 역할을 바꾸는(소유자 넘기기) 저장은 그 기능의 이슈가 더한다. 한 사람이 한 주최자에 지금 소속 하나, 지금 소유자는
 * 주최자마다 많아야 한 명이라는 약속은 표의 유일 색인이 마지막으로 지킨다.
 *
 * <h2>같은 사람이 거의 동시에 들어오기 — 수락 경합</h2>
 * 운영자 줄은 {@code INSERT … ON CONFLICT DO NOTHING}으로 넣는다. 그 사람의 지금 소속이 먼저 들어갔으면 유일 색인
 * (staff_membership_active_ux)에 걸려 한 줄도 들어가지 않고, 진 쪽이라 {@link ConflictException}(409)이 된다. 먼저 넣은 쪽이 아직 커밋하지
 * 않았으면 PostgreSQL이 그 결과를 기다렸다가 판정한다.
 *
 * <h2>내보내는 사이 스스로 나가기 — 끝내기 경합</h2>
 * 끝난 일시는 {@code UPDATE … WHERE ended_at IS NULL}로 남긴다. 바뀐 줄이 없으면 그 사이 먼저 끝난 것이라 {@link ConflictException}(409)이
 * 된다.
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
        int updated = dsl.update(STAFF_MEMBERSHIP)
                .set(STAFF_MEMBERSHIP.ENDED_AT, toDatabaseTime(endedMembership.endedAt()))
                .where(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(endedMembership.organizerId()))
                .and(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID.eq(endedMembership.staffAccountId()))
                .and(STAFF_MEMBERSHIP.ROLE.eq(endedMembership.role().name()))
                .and(STAFF_MEMBERSHIP.ENDED_AT.isNull())
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.MEMBERSHIP_ALREADY_ENDED,
                    "끝내기 경합 — 주최자 " + endedMembership.organizerId() + ", 운영진 " + endedMembership.staffAccountId()
                            + "의 " + endedMembership.role() + " 소속이 먼저 끝남");
        }
    }

    /** 소속을 시작한 순서(같으면 저장한 순서)로 되살린다. */
    @Override
    public OrganizerStaff findByOrganizerId(long organizerId) {
        List<StaffMembership> memberships = dsl.selectFrom(STAFF_MEMBERSHIP)
                .where(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId))
                .orderBy(STAFF_MEMBERSHIP.STARTED_AT, STAFF_MEMBERSHIP.ID)
                .fetch(membership -> new StaffMembership(membership.getOrganizerId(), membership.getStaffAccountId(),
                        StaffRole.valueOf(membership.getRole()), membership.getStartedAt().toInstant(),
                        toInstantOrNull(membership.getEndedAt())));
        return OrganizerStaff.restore(organizerId, memberships);
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstantOrNull(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
