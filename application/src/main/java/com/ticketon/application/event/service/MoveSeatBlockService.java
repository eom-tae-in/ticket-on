package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.MoveSeatBlockCommand;
import com.ticketon.application.event.port.in.MoveSeatBlockUseCase;
import com.ticketon.application.event.port.in.SeatBlockResult;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블록 이동(위저드 3단계 배치도 에디터) — 옮기는 규칙(위치가 바뀔 때만 도면 수정 범위, 진행 단계 · 마지막 수정 시각)은 {@link Event#moveBlock}이
 * 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤, 바뀌었으면 도면 · 행사를 저장하고 조작 기록을 남긴다. 좌석 줄과 id는 그대로다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 추가와 같다({@link EventEditingLocks}) — 같은 행사의 도면 저장은 행사 줄에서 차례로 처리된다. 같은 위치면 저장도 기록도 하지 않는다.
 */
@Service
public class MoveSeatBlockService implements MoveSeatBlockUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final Clock clock;

    public MoveSeatBlockService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, FloorPlanRepository floorPlanRepository,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.clock = clock;
    }

    /** 조작 기록의 요약 — "블록 이동 · 1층 (3단계)". */
    static String operationSummary(BlockName name) {
        return "블록 이동 · " + name.value() + " (3단계)";
    }

    @Override
    @Transactional
    public SeatBlockResult move(MoveSeatBlockCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = lockOrganizer(new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());

        if (event.moveBlock(floorPlan, command.blockId(), command.position(), now)) {
            floorPlanRepository.save(floorPlan, now);
            eventRepository.save(event);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), operationSummary(floorPlan.block(command.blockId()).name()), now));
        }
        return FloorPlanResults.of(floorPlan.block(command.blockId()));
    }

    private StaffPermissionResult lockOrganizer(CheckStaffPermissionQuery query) {
        return EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository, organizerRepository, query);
    }
}
