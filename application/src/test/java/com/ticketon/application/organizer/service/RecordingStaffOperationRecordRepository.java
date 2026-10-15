package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.StaffOperationRecordRepository;
import com.ticketon.domain.organizer.StaffOperationRecord;
import java.util.ArrayList;
import java.util.List;

/** 남긴 운영진 조작 기록을 모아 두는 저장소. */
final class RecordingStaffOperationRecordRepository implements StaffOperationRecordRepository {

    private final List<StaffOperationRecord> records = new ArrayList<>();

    List<StaffOperationRecord> records() {
        return List.copyOf(records);
    }

    @Override
    public void add(StaffOperationRecord record) {
        records.add(record);
    }
}
