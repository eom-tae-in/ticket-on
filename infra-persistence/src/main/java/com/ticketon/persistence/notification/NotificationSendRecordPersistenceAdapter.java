package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.NOTIFICATION_SEND_RECORD;

import com.ticketon.application.notification.port.out.NotificationSendRecordRepository;
import com.ticketon.domain.notification.NotificationSendRecord;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 발송 이력 저장소(notification_send_record — V3) 어댑터.
 */
@Component
public class NotificationSendRecordPersistenceAdapter implements NotificationSendRecordRepository {

    private final DSLContext dsl;

    public NotificationSendRecordPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(NotificationSendRecord record) {
        dsl.insertInto(NOTIFICATION_SEND_RECORD)
                .set(NOTIFICATION_SEND_RECORD.KIND, record.kind().name())
                .set(NOTIFICATION_SEND_RECORD.CHANNEL, record.channel().name())
                .set(NOTIFICATION_SEND_RECORD.RECIPIENT_ADDRESS, record.recipientAddress())
                .set(NOTIFICATION_SEND_RECORD.SUCCEEDED, record.succeeded())
                .set(NOTIFICATION_SEND_RECORD.FAILURE_REASON, record.failureReason().orElse(null))
                .set(NOTIFICATION_SEND_RECORD.SENT_AT, record.sentAt().atOffset(ZoneOffset.UTC))
                .execute();
    }
}
