package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.AssignSeatsToZoneCommand;
import com.ticketon.application.event.port.in.AssignSeatsToZoneUseCase;
import com.ticketon.application.event.port.in.SeatsResult;
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
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌석 구역 배정 · 배정 풀기(위저드 3단계 배치도 에디터, 색칠 · 색 지우기) — 규칙(하나라도 바뀔 때만 도면 수정 범위, 지정형 구역만, 묶음 안에 없는
 * 좌석이 있으면 아무것도 바꾸지 않음, 진행 단계 · 마지막 수정 시각)은 {@link Event#assignSeatsToZone} · {@link Event#unassignSeats}가 갖고,
 * 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤, 바뀌었으면 도면 · 행사를 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 추가와 같다({@link EventEditingLocks}) — 같은 행사의 도면 저장은 행사 줄에서 차례로 처리된다. 바뀌는 좌석이 없으면(여러 번 보내도) 저장도 기록도 하지 않는다.
 */
@Service
public class AssignSeatsToZoneService implements AssignSeatsToZoneUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final Clock clock;

    public AssignSeatsToZoneService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — 구역에 칠했으면 "좌석 구역 배정 · R석 20석 (3단계)". 고른 좌석 수를 센다. */
    static String assignedSummary(ZoneName zoneName, int seatCount) {
        return "좌석 구역 배정 · " + zoneName.value() + " " + seatCount + "석 (3단계)";
    }

    /** 조작 기록의 요약 — 배정을 풀었으면 "좌석 구역 배정 풀기 · 20석 (3단계)". */
    static String unassignedSummary(int seatCount) {
        return "좌석 구역 배정 풀기 · " + seatCount + "석 (3단계)";
    }

    @Override
    @Transactional
    public SeatsResult assign(AssignSeatsToZoneCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = lockOrganizer(new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());

        boolean changed = command.zoneId() == null
                ? event.unassignSeats(floorPlan, command.seatIds(), now)
                : event.assignSeatsToZone(floorPlan, command.seatIds(), command.zoneId(), now);
        if (changed) {
            floorPlanRepository.save(floorPlan, now);
            eventRepository.save(event);
            String summary = command.zoneId() == null
                    ? unassignedSummary(command.seatIds().size())
                    : assignedSummary(floorPlan.zone(command.zoneId()).name(), command.seatIds().size());
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), summary, now));
        }
        return FloorPlanResults.seatsOf(floorPlan, command.seatIds());
    }

    private StaffPermissionResult lockOrganizer(CheckStaffPermissionQuery query) {
        return EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository, organizerRepository, query);
    }
}
