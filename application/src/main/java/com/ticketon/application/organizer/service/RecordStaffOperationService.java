package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.out.StaffOperationRecordRepository;
import com.ticketon.domain.organizer.StaffOperationRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 조작 기록 — 조작을 마친 서비스의 트랜잭션에 참여해 한 줄을 남긴다({@link Propagation#MANDATORY}, 관리자 조작 기록기와 같다).
 * 기록 한 줄을 어떻게 만드는지는 도메인({@link StaffOperationRecord})이 정한다.
 */
@Service
public class RecordStaffOperationService implements RecordStaffOperationUseCase {

    private final StaffOperationRecordRepository recordRepository;

    public RecordStaffOperationService(StaffOperationRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(RecordStaffOperationCommand command) {
        recordRepository.add(StaffOperationRecord.of(command.permission(), command.targetKind(), command.targetId(),
                command.summary(), command.operatedAt()));
    }
}
