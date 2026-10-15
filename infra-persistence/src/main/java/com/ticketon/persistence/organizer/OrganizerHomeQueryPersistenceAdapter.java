package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_APPLICATION;
import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.application.organizer.port.out.OrganizerHomeQueryPort;
import com.ticketon.application.organizer.query.CurrentAffiliation;
import com.ticketon.application.organizer.query.LatestOrganizerApplication;
import com.ticketon.application.organizer.query.ReceivedOrganizerInvitation;
import com.ticketon.application.organizer.query.ReopenableClosedOrganizer;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 내 주최 홈 조회 어댑터 — 소속 표(staff_membership)·주최자 표(organizer)·신청서 표(organizer_application, V8)를 읽어 화면에 쓸 칸만
 * 돌려준다. 도메인 객체를 만들지 않는다. 신청서의 담당자 연락처 · 사업자 정보 · 정산 계좌는 읽지 않는다.
 * <p>
 * 받은 초대와 다시 열 수 있는 닫힌 주최자는 아직 늘 비어 있다 — 초대 표는 운영자 초대(#25)가, 닫기와 "다시 열기를 신청할 수 있는 사람"은
 * 닫기·다시 열기(#134)가 만든다. 그 이슈에서 이 두 조회를 채운다.
 */
@Component
public class OrganizerHomeQueryPersistenceAdapter implements OrganizerHomeQueryPort {

    private final DSLContext dsl;

    public OrganizerHomeQueryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<CurrentAffiliation> currentAffiliationsOf(long staffAccountId) {
        return dsl.select(STAFF_MEMBERSHIP.ORGANIZER_ID, ORGANIZER.NAME, STAFF_MEMBERSHIP.ROLE, STAFF_MEMBERSHIP.STARTED_AT)
                .from(STAFF_MEMBERSHIP)
                .join(ORGANIZER).on(ORGANIZER.ID.eq(STAFF_MEMBERSHIP.ORGANIZER_ID))
                .where(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID.eq(staffAccountId))
                .and(STAFF_MEMBERSHIP.ENDED_AT.isNull())
                .orderBy(STAFF_MEMBERSHIP.STARTED_AT, STAFF_MEMBERSHIP.ID)
                .fetch(row -> new CurrentAffiliation(row.value1(), row.value2(), StaffRole.valueOf(row.value3()),
                        row.value4().toInstant()));
    }

    /** 제출 일시가 가장 늦은 신청 — 같으면 나중에 저장된 것. */
    @Override
    public Optional<LatestOrganizerApplication> latestApplicationOf(long staffAccountId) {
        return dsl.select(ORGANIZER_APPLICATION.ID, ORGANIZER_APPLICATION.NAME, ORGANIZER_APPLICATION.STATUS,
                        ORGANIZER_APPLICATION.SUBMITTED_AT, ORGANIZER_APPLICATION.REVIEWED_AT,
                        ORGANIZER_APPLICATION.REJECTION_REASON)
                .from(ORGANIZER_APPLICATION)
                .where(ORGANIZER_APPLICATION.APPLICANT_STAFF_ACCOUNT_ID.eq(staffAccountId))
                .orderBy(ORGANIZER_APPLICATION.SUBMITTED_AT.desc(), ORGANIZER_APPLICATION.ID.desc())
                .limit(1)
                .fetchOptional(row -> new LatestOrganizerApplication(row.value1(), row.value2(),
                        OrganizerApplicationStatus.valueOf(row.value3()), row.value4().toInstant(),
                        toInstantOrNull(row.value5()), row.value6()));
    }

    @Override
    public List<ReceivedOrganizerInvitation> receivedInvitationsOf(long staffAccountId) {
        return List.of();
    }

    @Override
    public List<ReopenableClosedOrganizer> reopenableOrganizersOf(long staffAccountId) {
        return List.of();
    }

    private static Instant toInstantOrNull(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
