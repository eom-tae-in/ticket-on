package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.AddEntranceCommand;
import com.ticketon.application.event.port.in.AddEntranceUseCase;
import com.ticketon.application.event.port.in.EntranceResult;
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
 * 입구 추가(위저드 6단계) — 더하는 규칙(수정 범위 — 판매가 끝난 뒤에도 행사가 끝날 때까지, 이름 겹침, 진행 단계 · 마지막 수정 시각)은
 * {@link Event#addEntrance}가 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤 입구 목록 · 행사를 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 저장과 같다({@link EventEditingLocks}) — 같은 행사의 입구 저장은 행사 줄에서 차례로 처리된다.
 * 수정 범위는 저장된 상태로 판정한다 — 지금 시각을 반영하는 일은 #65가 더한다.
 */
@Service
public class AddEntranceService implements AddEntranceUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventEntranceRepository entranceRepository;
    private final Clock clock;

    public AddEntranceService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — "입구 추가 · 옆문 (6단계)". */
    static String operationSummary(EntranceName name) {
        return "입구 추가 · " + name.value() + " (6단계)";
    }

    @Override
    @Transactional
    public EntranceResult add(AddEntranceCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        EventEntrances entrances = entranceRepository.findByEventId(command.eventId());

        Entrance added = event.addEntrance(entrances, command.name(), now);
        Entrance saved = EntranceResults.savedEntranceNamed(entranceRepository.save(entrances, now), added.name());
        eventRepository.save(event);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                command.eventId(), operationSummary(saved.name()), now));
        return EntranceResults.of(saved);
    }
}
