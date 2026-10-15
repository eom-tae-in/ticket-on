package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
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
 * 지금은 승인으로 막 연 명단(소유자 한 줄)을 넣고, 한 주최자의 소속을 모두 읽는다. 소속을 끝내거나(나가기·내보내기) 역할을 바꾸는(소유자
 * 넘기기) 저장은 그 기능의 이슈가 더한다. 한 사람이 한 주최자에 지금 소속 하나, 지금 소유자는 주최자마다 많아야 한 명이라는 약속은 표의 유일
 * 색인이 마지막으로 지킨다.
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
