package com.ticketon.persistence.admin;

import static com.ticketon.persistence.jooq.Tables.ADMIN_OPERATION_RECORD;

import com.ticketon.application.admin.port.out.AdminOperationRecordRepository;
import com.ticketon.domain.admin.AdminOperationRecord;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 관리자 조작 기록 저장소(admin_operation_record — V9) 어댑터. 한 줄씩 넣기만 하고 고치거나 지우지 않는다.
 * 부른 쪽의 트랜잭션 안에서 넣으므로 조작이 되돌려지면 기록도 함께 되돌려진다.
 */
@Component
public class AdminOperationRecordPersistenceAdapter implements AdminOperationRecordRepository {

    private final DSLContext dsl;

    public AdminOperationRecordPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(AdminOperationRecord record) {
        dsl.insertInto(ADMIN_OPERATION_RECORD)
                .set(ADMIN_OPERATION_RECORD.ADMIN_ACCOUNT_ID, record.adminAccountId())
                .set(ADMIN_OPERATION_RECORD.OPERATED_AT, record.operatedAt().atOffset(ZoneOffset.UTC))
                .set(ADMIN_OPERATION_RECORD.OPERATION_KIND, record.kind().name())
                .set(ADMIN_OPERATION_RECORD.TARGET_KIND, record.targetKind().name())
                .set(ADMIN_OPERATION_RECORD.TARGET_ID, record.targetId())
                .set(ADMIN_OPERATION_RECORD.SUMMARY, record.summary())
                .set(ADMIN_OPERATION_RECORD.REASON, record.reason().orElse(null))
                .execute();
    }
}
