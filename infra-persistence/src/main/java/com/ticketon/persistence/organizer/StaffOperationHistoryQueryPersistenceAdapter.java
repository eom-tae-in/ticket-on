package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.STAFF_OPERATION_RECORD;

import com.ticketon.application.organizer.port.out.StaffOperationHistoryQueryPort;
import com.ticketon.application.organizer.query.StaffOperationHistoryEntry;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 주최자 이력의 운영진 조작 기록 조회 어댑터 — 조작 기록 표(staff_operation_record — V11)에 운영진 로그인 표(staff_account)를 붙여 목록에 쓸
 * 칸만 돌려준다. 도메인 객체를 만들지 않는다. 운영진 로그인의 이메일 · 비밀번호 해시는 읽지 않는다.
 */
@Component
public class StaffOperationHistoryQueryPersistenceAdapter implements StaffOperationHistoryQueryPort {

    private final DSLContext dsl;

    public StaffOperationHistoryQueryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<StaffOperationHistoryEntry> historyOf(long organizerId, Long beforeRecordId, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("많아야 몇 줄인지는 1 이상이어야 합니다: " + limit);
        }
        Condition before = beforeRecordId == null ? DSL.noCondition() : STAFF_OPERATION_RECORD.ID.lt(beforeRecordId);
        return dsl.select(STAFF_OPERATION_RECORD.ID, STAFF_OPERATION_RECORD.OPERATED_AT,
                        STAFF_OPERATION_RECORD.STAFF_ACCOUNT_ID, STAFF_ACCOUNT.NICKNAME, STAFF_ACCOUNT.NAME,
                        STAFF_OPERATION_RECORD.STAFF_ROLE, STAFF_OPERATION_RECORD.STAFF_ACTION,
                        STAFF_OPERATION_RECORD.TARGET_KIND, STAFF_OPERATION_RECORD.TARGET_ID, STAFF_OPERATION_RECORD.SUMMARY)
                .from(STAFF_OPERATION_RECORD)
                .join(STAFF_ACCOUNT).on(STAFF_ACCOUNT.ID.eq(STAFF_OPERATION_RECORD.STAFF_ACCOUNT_ID))
                .where(STAFF_OPERATION_RECORD.ORGANIZER_ID.eq(organizerId))
                .and(before)
                .orderBy(STAFF_OPERATION_RECORD.ID.desc())
                .limit(limit)
                .fetch(row -> new StaffOperationHistoryEntry(row.value1(), row.value2().toInstant(), row.value3(),
                        row.value4(), row.value5(), StaffRole.valueOf(row.value6()), StaffAction.valueOf(row.value7()),
                        StaffOperationTargetKind.valueOf(row.value8()), row.value9(), row.value10()));
    }
}
