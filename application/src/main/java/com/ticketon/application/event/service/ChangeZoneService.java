package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.ChangeZoneCommand;
import com.ticketon.application.event.port.in.ChangeZoneUseCase;
import com.ticketon.application.event.port.in.ZoneResult;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneCapacityChanged;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구역 수정(위저드 3단계) — 고치는 규칙(바뀌는 칸마다 수정 범위, 저장된 종류로 정원 확인, 이름 겹침, 같은 값이면 바꾸지 않음)은
 * {@link Event#changeZone}이 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤, 바뀌었으면 도면 · 행사를 저장하고 조작 기록을 남긴다. 수량형의
 * 정원이 바뀌었으면 재고에 알린다({@link ZoneCapacityChanged}, #54).
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 추가와 같다({@link EventEditingLocks}). 지금과 같은 값이면(자동 임시저장을 여러 번 해도) 저장도
 * 알림도 기록도 하지 않는다.
 */
@Service
public class ChangeZoneService implements ChangeZoneUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ChangeZoneService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — 고친 뒤의 이름으로 "구역 수정 · 스탠딩A (3단계)". */
    static String operationSummary(ZoneName name) {
        return "구역 수정 · " + name.value() + " (3단계)";
    }

    @Override
    @Transactional
    public ZoneResult change(ChangeZoneCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());
        Optional<ZoneCapacity> capacityBefore = floorPlan.zone(command.zoneId()).capacity();

        if (event.changeZone(floorPlan, command.zoneId(), command.settings(), now)) {
            floorPlanRepository.save(floorPlan, now);
            eventRepository.save(event);
            Zone changed = floorPlan.zone(command.zoneId());
            if (!changed.capacity().equals(capacityBefore)) {
                eventPublisher.publishEvent(new ZoneCapacityChanged(command.eventId(), command.zoneId(),
                        changed.capacity().orElseThrow()));
            }
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), operationSummary(changed.name()), now));
        }
        return ZoneResults.of(floorPlan.zone(command.zoneId()));
    }
}
