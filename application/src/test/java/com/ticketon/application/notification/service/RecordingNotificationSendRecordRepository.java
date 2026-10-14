package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.out.NotificationSendRecordRepository;
import com.ticketon.domain.notification.NotificationSendRecord;
import java.util.ArrayList;
import java.util.List;

/** 남긴 발송 이력을 순서대로 모아 둔다. */
final class RecordingNotificationSendRecordRepository implements NotificationSendRecordRepository {

    private final List<NotificationSendRecord> records = new ArrayList<>();

    List<NotificationSendRecord> records() {
        return List.copyOf(records);
    }

    @Override
    public void add(NotificationSendRecord record) {
        records.add(record);
    }
}
