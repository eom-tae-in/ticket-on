package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.application.organizer.port.out.StaffListQueryPort;
import com.ticketon.application.organizer.query.CurrentStaffMember;
import com.ticketon.domain.organizer.StaffRole;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 운영진 목록 조회 어댑터 — 소속 표(staff_membership — V8)의 끝나지 않은 줄과 운영진 로그인(staff_account — V5)의 닉네임을 읽어 화면에 쓸
 * 칸만 돌려준다. 도메인 객체를 만들지 않는다. 이름 · 이메일은 읽지 않는다.
 */
@Component
public class StaffListQueryPersistenceAdapter implements StaffListQueryPort {

    private final DSLContext dsl;

    public StaffListQueryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 소유자가 먼저, 그다음 지금 역할로 소속된 순서(같으면 저장한 순서). */
    @Override
    public List<CurrentStaffMember> currentStaffOf(long organizerId) {
        return dsl.select(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID, STAFF_ACCOUNT.NICKNAME, STAFF_MEMBERSHIP.ROLE,
                        STAFF_MEMBERSHIP.STARTED_AT)
                .from(STAFF_MEMBERSHIP)
                .join(STAFF_ACCOUNT).on(STAFF_ACCOUNT.ID.eq(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID))
                .where(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId))
                .and(STAFF_MEMBERSHIP.ENDED_AT.isNull())
                .orderBy(DSL.when(STAFF_MEMBERSHIP.ROLE.eq(StaffRole.OWNER.name()), 0).otherwise(1),
                        STAFF_MEMBERSHIP.STARTED_AT, STAFF_MEMBERSHIP.ID)
                .fetch(row -> new CurrentStaffMember(row.value1(), row.value2(), StaffRole.valueOf(row.value3()),
                        row.value4().toInstant()));
    }
}
