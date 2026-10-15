package com.ticketon.application.admin.service;

import com.ticketon.application.admin.port.out.AdminOperationRecordRepository;
import com.ticketon.domain.admin.AdminOperationRecord;
import java.util.ArrayList;
import java.util.List;

/** 남긴 관리자 조작 기록을 차례대로 모아 둔다. */
final class RecordingAdminOperationRecordRepository implements AdminOperationRecordRepository {

    private final List<AdminOperationRecord> records = new ArrayList<>();

    @Override
    public void add(AdminOperationRecord record) {
        records.add(record);
    }

    List<AdminOperationRecord> records() {
        return List.copyOf(records);
    }
}
