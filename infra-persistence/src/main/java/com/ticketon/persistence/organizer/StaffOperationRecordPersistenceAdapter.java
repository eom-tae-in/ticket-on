package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_OPERATION_RECORD;

import com.ticketon.application.organizer.port.out.StaffOperationRecordRepository;
import com.ticketon.domain.organizer.StaffOperationRecord;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 운영진 조작 기록 저장소(staff_operation_record — V11) 어댑터. 한 줄씩 넣기만 하고 고치거나 지우지 않는다.
 * 부른 쪽의 트랜잭션 안에서 넣으므로 조작이 되돌려지면 기록도 함께 되돌려진다.
 */
@Component
public class StaffOperationRecordPersistenceAdapter implements StaffOperationRecordRepository {

    private final DSLContext dsl;

    public StaffOperationRecordPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(StaffOperationRecord record) {
        dsl.insertInto(STAFF_OPERATION_RECORD)
                .set(STAFF_OPERATION_RECORD.ORGANIZER_ID, record.organizerId())
                .set(STAFF_OPERATION_RECORD.STAFF_ACCOUNT_ID, record.staffAccountId())
                .set(STAFF_OPERATION_RECORD.STAFF_ROLE, record.staffRole().name())
                .set(STAFF_OPERATION_RECORD.OPERATED_AT, record.operatedAt().atOffset(ZoneOffset.UTC))
                .set(STAFF_OPERATION_RECORD.STAFF_ACTION, record.action().name())
                .set(STAFF_OPERATION_RECORD.TARGET_KIND, record.targetKind().name())
                .set(STAFF_OPERATION_RECORD.TARGET_ID, record.targetId())
                .set(STAFF_OPERATION_RECORD.SUMMARY, record.summary())
                .execute();
    }
}
