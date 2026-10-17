package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.RenameSeatBlockCommand;
import com.ticketon.application.event.port.in.RenameSeatBlockUseCase;
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
 * 블록 이름 변경(위저드 3단계 배치도 에디터) — 바꾸는 규칙(이름이 바뀔 때만 블록 이름 수정 범위, 겹침, 비운 이름의 "블록 N", 진행 단계 · 마지막 수정
 * 시각)은 {@link Event#renameBlock}이 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤, 바뀌었으면 도면 · 행사를 저장하고 조작 기록을 남긴다.
 * 이름은 블록 한 곳에만 있어 이미 팔린 표의 표기도 따라 바뀐다 — 블록 이름 변경 이력은 남기지 않는다(docs/02, docs/99 B). 운영진 조작 기록은
 * 누가 무엇을 했는지의 기록이라 따로 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 추가와 같다({@link EventEditingLocks}) — 같은 행사의 도면 저장은 행사 줄에서 차례로 처리된다. 같은 이름이면(여러 번 보내도) 저장도 기록도 하지 않는다.
 */
@Service
public class RenameSeatBlockService implements RenameSeatBlockUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final Clock clock;

    public RenameSeatBlockService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — 바꾸기 전 · 뒤 이름으로 "블록 이름 변경 · 1증 → 1층 (3단계)". */
    static String operationSummary(BlockName before, BlockName after) {
        return "블록 이름 변경 · " + before.value() + " → " + after.value() + " (3단계)";
    }

    @Override
    @Transactional
    public SeatBlockResult rename(RenameSeatBlockCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = lockOrganizer(new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());
        BlockName before = floorPlan.block(command.blockId()).name();

        if (event.renameBlock(floorPlan, command.blockId(), command.name(), now)) {
            floorPlanRepository.save(floorPlan, now);
            eventRepository.save(event);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), operationSummary(before, floorPlan.block(command.blockId()).name()), now));
        }
        return FloorPlanResults.of(floorPlan.block(command.blockId()));
    }

    private StaffPermissionResult lockOrganizer(CheckStaffPermissionQuery query) {
        return EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository, organizerRepository, query);
    }
}
