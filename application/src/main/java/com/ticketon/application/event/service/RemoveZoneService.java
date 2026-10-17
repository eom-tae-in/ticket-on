package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.RemoveZoneCommand;
import com.ticketon.application.event.port.in.RemoveZoneUseCase;
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
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneRemoved;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구역 삭제(위저드 3단계) — 지우는 규칙(수정 범위, 배정했던 좌석의 배정 풀기, 진행 단계 · 마지막 수정 시각)은 {@link Event#removeZone}이 갖고,
 * 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤 도면 · 행사를 저장하고(지운 구역은 줄을 남기고 지운 시각을 적는다), 재고에 알리고
 * ({@link ZoneRemoved}, #54), 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 추가와 같다({@link EventEditingLocks}).
 */
@Service
public class RemoveZoneService implements RemoveZoneUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RemoveZoneService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — "구역 삭제 · 스탠딩A (3단계)". */
    static String operationSummary(ZoneName name) {
        return "구역 삭제 · " + name.value() + " (3단계)";
    }

    @Override
    @Transactional
    public void remove(RemoveZoneCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());

        Zone removed = event.removeZone(floorPlan, command.zoneId(), now);
        floorPlanRepository.save(floorPlan, now);
        eventRepository.save(event);
        eventPublisher.publishEvent(new ZoneRemoved(command.eventId(), command.zoneId(), removed.kind()));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                command.eventId(), operationSummary(removed.name()), now));
    }
}
