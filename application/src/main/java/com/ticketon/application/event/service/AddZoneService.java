package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.AddZoneCommand;
import com.ticketon.application.event.port.in.AddZoneUseCase;
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
import com.ticketon.domain.event.ZoneAdded;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구역 추가(위저드 3단계) — 더하는 규칙(수정 범위, 정원과 종류, 이름이 다른 구역 · 블록과 겹치지 않음, 진행 단계 · 마지막 수정 시각)은
 * {@link Event#addZone}이 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤 도면 · 행사를 저장하고, 재고에 알리고, 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인 → 행사 줄(FOR UPDATE)({@link EventEditingLocks})
 * → 도면 읽기 · 저장 → 행사 저장 → 알림 → 조작 기록. 같은 행사의 도면 저장은 행사 줄에서 차례로 처리되므로, 두 운영진이 같은 이름의 구역을 거의
 * 동시에 더하면 뒤의 추가는 앞의 구역을 읽고 이름이 겹쳐 막힌다.
 * <p>
 * 알림({@link ZoneAdded})은 같은 트랜잭션 안에서 발행한다 — 듣는 쪽(재고, #54)이 같은 트랜잭션에서 처리하면 함께 커밋되거나 함께 되돌려진다.
 * <p>
 * 수정 범위는 저장된 상태로 판정한다. 시간이 움직이는 상태의 행사에 지금 시각을 반영하는 일은 일정(#41)이 생긴 뒤 상태별 설정 잠금(#65)이 더한다.
 */
@Service
public class AddZoneService implements AddZoneUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AddZoneService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — "구역 추가 · 스탠딩A (3단계)". */
    static String operationSummary(ZoneName name) {
        return "구역 추가 · " + name.value() + " (3단계)";
    }

    @Override
    @Transactional
    public ZoneResult add(AddZoneCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());

        Zone added = event.addZone(floorPlan, command.kind(), command.settings(), now);
        Zone saved = ZoneResults.savedZoneNamed(floorPlanRepository.save(floorPlan, now), added.name());
        eventRepository.save(event);
        eventPublisher.publishEvent(ZoneAdded.of(command.eventId(), saved));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                command.eventId(), operationSummary(saved.name()), now));
        return ZoneResults.of(saved);
    }
}
