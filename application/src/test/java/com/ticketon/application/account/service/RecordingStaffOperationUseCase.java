package com.ticketon.application.account.service;

import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import java.util.ArrayList;
import java.util.List;

/** 남기라고 한 운영진 조작 기록을 부른 차례대로 모아 두는 기록 유스케이스. 한 줄을 어떻게 만드는지는 조작 기록 유스케이스의 테스트가 본다. */
final class RecordingStaffOperationUseCase implements RecordStaffOperationUseCase {

    private final List<RecordStaffOperationCommand> recordedCommands = new ArrayList<>();

    List<RecordStaffOperationCommand> recordedCommands() {
        return List.copyOf(recordedCommands);
    }

    @Override
    public void record(RecordStaffOperationCommand command) {
        recordedCommands.add(command);
    }
}
