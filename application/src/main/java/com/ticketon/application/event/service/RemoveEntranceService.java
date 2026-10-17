package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.RemoveEntranceCommand;
import com.ticketon.application.event.port.in.RemoveEntranceUseCase;
import com.ticketon.application.event.port.out.EventEntranceRepository;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.event.Entrance;
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
 * 입구 삭제(위저드 6단계) — 지우는 규칙(수정 범위, 진행 단계 · 마지막 수정 시각)은 {@link Event#removeEntrance}가 갖고, 여기서는 권한을 확인하고
 * 줄을 잠가 읽은 뒤 입구 목록 · 행사를 저장하고(지운 입구는 줄을 남기고 지운 시각을 적는다) 조작 기록을 남긴다. 한 트랜잭션이다
 * ({@link EventEditingLocks}).
 */
@Service
public class RemoveEntranceService implements RemoveEntranceUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventEntranceRepository entranceRepository;
    private final Clock clock;

    public RemoveEntranceService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — "입구 삭제 · VIP 입구 (6단계)". */
    static String operationSummary(EntranceName name) {
        return "입구 삭제 · " + name.value() + " (6단계)";
    }

    @Override
    @Transactional
    public void remove(RemoveEntranceCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        EventEntrances entrances = entranceRepository.findByEventId(command.eventId());

        Entrance removed = event.removeEntrance(entrances, command.entranceId(), now);
        entranceRepository.save(entrances, now);
        eventRepository.save(event);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                command.eventId(), operationSummary(removed.name()), now));
    }
}
