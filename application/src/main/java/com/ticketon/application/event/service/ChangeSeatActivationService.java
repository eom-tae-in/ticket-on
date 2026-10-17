package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.ChangeSeatActivationCommand;
import com.ticketon.application.event.port.in.ChangeSeatActivationUseCase;
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
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌석 끄기 · 다시 켜기(위저드 3단계 배치도 에디터 — 통로 · 기둥 같은 빈 자리) — 규칙(하나라도 바뀔 때만 도면 수정 범위, 번호는 다시 매기지 않음,
 * 묶음 안에 없는 좌석이 있으면 아무것도 바꾸지 않음, 진행 단계 · 마지막 수정 시각)은 {@link Event#changeSeatActivation}이 갖고, 여기서는 권한을
 * 확인하고 줄을 잠가 읽은 뒤, 바뀌었으면 도면 · 행사를 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 추가와 같다({@link EventEditingLocks}) — 같은 행사의 도면 저장은 행사 줄에서 차례로 처리된다. 바뀌는 좌석이 없으면(여러 번 보내도) 저장도 기록도 하지 않는다.
 */
@Service
public class ChangeSeatActivationService implements ChangeSeatActivationUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final Clock clock;

    public ChangeSeatActivationService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — 껐으면 "좌석 비활성화 · 3석 (3단계)", 켰으면 "좌석 활성화 · 3석 (3단계)". 고른 좌석 수를 센다. */
    static String operationSummary(boolean active, int seatCount) {
        return (active ? "좌석 활성화 · " : "좌석 비활성화 · ") + seatCount + "석 (3단계)";
    }

    @Override
    @Transactional
    public SeatsResult change(ChangeSeatActivationCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = lockOrganizer(new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());

        if (event.changeSeatActivation(floorPlan, command.seatIds(), command.active(), now)) {
            floorPlanRepository.save(floorPlan, now);
            eventRepository.save(event);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), operationSummary(command.active(), command.seatIds().size()), now));
        }
        return FloorPlanResults.seatsOf(floorPlan, command.seatIds());
    }

    private StaffPermissionResult lockOrganizer(CheckStaffPermissionQuery query) {
        return EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository, organizerRepository, query);
    }
}
