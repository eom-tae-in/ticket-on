package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.AddSeatBlockCommand;
import com.ticketon.application.event.port.in.AddSeatBlockUseCase;
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
import com.ticketon.domain.event.SeatBlock;
import com.ticketon.domain.event.SeatBlockAdded;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블록 얹기(위저드 3단계 배치도 에디터) — 얹는 규칙(수정 범위, 이름이 다른 블록 · 구역과 겹치지 않음, 비운 이름의 "블록 N", 격자의 모든 자리에
 * 좌석, 진행 단계 · 마지막 수정 시각)은 {@link Event#addBlock}이 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤 도면 · 행사를 저장하고, 재고에
 * 알리고({@link SeatBlockAdded}, #54), 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 추가와 같다({@link EventEditingLocks}) — 같은 행사의 도면 저장은 행사 줄에서 차례로 처리된다.
 * 두 운영진이 같은 이름의 블록을, 또는 한쪽은 구역을 다른 쪽은 블록을 같은 이름으로 거의 동시에 더하면 뒤의 추가는 앞의 것을 읽고 이름이 겹쳐
 * 막힌다 — 구역 이름과 블록 이름은 표를 건너 DB가 막을 수 없어 이 차례에 기댄다. 알림은 같은 트랜잭션 안에서 발행한다.
 */
@Service
public class AddSeatBlockService implements AddSeatBlockUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AddSeatBlockService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, FloorPlanRepository floorPlanRepository,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 조작 기록의 요약 — "블록 추가 · 1층 (3단계)". */
    static String operationSummary(BlockName name) {
        return "블록 추가 · " + name.value() + " (3단계)";
    }

    @Override
    @Transactional
    public SeatBlockResult add(AddSeatBlockCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = lockOrganizer(new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());

        SeatBlock added = event.addBlock(floorPlan, command.name(), command.size(), command.position(), command.angle(), now);
        SeatBlock saved = FloorPlanResults.savedBlockNamed(floorPlanRepository.save(floorPlan, now), added.name());
        eventRepository.save(event);
        eventPublisher.publishEvent(SeatBlockAdded.of(command.eventId(), saved));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                command.eventId(), operationSummary(saved.name()), now));
        return FloorPlanResults.of(saved);
    }

    private StaffPermissionResult lockOrganizer(CheckStaffPermissionQuery query) {
        return EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository, organizerRepository, query);
    }
}
