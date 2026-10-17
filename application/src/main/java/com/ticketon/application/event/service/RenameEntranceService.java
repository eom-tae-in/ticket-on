package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.EntranceResult;
import com.ticketon.application.event.port.in.RenameEntranceCommand;
import com.ticketon.application.event.port.in.RenameEntranceUseCase;
import com.ticketon.application.event.port.out.EventEntranceRepository;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.event.EntranceName;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventEntrances;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입구 이름 변경(위저드 6단계) — 바꾸는 규칙(수정 범위, 이름 겹침, 같은 이름이면 바꾸지 않음)은 {@link Event#renameEntrance}가 갖고, 여기서는 권한을
 * 확인하고 줄을 잠가 읽은 뒤, 바뀌었으면 입구 목록 · 행사를 저장하고 조작 기록을 남긴다. 한 트랜잭션이다({@link EventEditingLocks}).
 */
@Service
public class RenameEntranceService implements RenameEntranceUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventEntranceRepository entranceRepository;
    private final Clock clock;

    public RenameEntranceService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, EventEntranceRepository entranceRepository,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.entranceRepository = entranceRepository;
        this.clock = clock;
    }

    /** 조작 기록의 요약 — "입구 이름 변경 · 정문 → 옆문 (6단계)". */
    static String operationSummary(EntranceName before, EntranceName after) {
        return "입구 이름 변경 · " + before.value() + " → " + after.value() + " (6단계)";
    }

    @Override
    @Transactional
    public EntranceResult rename(RenameEntranceCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        EventEntrances entrances = entranceRepository.findByEventId(command.eventId());
        EntranceName nameBefore = entrances.entrance(command.entranceId()).name();

        if (event.renameEntrance(entrances, command.entranceId(), command.name(), now)) {
            entranceRepository.save(entrances, now);
            eventRepository.save(event);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), operationSummary(nameBefore, command.name()), now));
        }
        return EntranceResults.of(entrances.entrance(command.entranceId()));
    }
}
