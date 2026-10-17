package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.ChangeSeatAttributeCommand;
import com.ticketon.application.event.port.in.ChangeSeatAttributeUseCase;
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
import com.ticketon.domain.event.SeatAttribute;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌석 속성 지정(위저드 3단계 배치도 에디터 — 일반 · 휠체어 · 시야제한) — 규칙(하나라도 바뀔 때만 도면 수정 범위, 묶음 안에 없는 좌석이 있으면
 * 아무것도 바꾸지 않음, 진행 단계 · 마지막 수정 시각)은 {@link Event#changeSeatAttribute}가 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤,
 * 바뀌었으면 도면 · 행사를 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 구역 추가와 같다({@link EventEditingLocks}) — 같은 행사의 도면 저장은 행사 줄에서 차례로 처리된다. 바뀌는 좌석이 없으면(여러 번 보내도) 저장도 기록도 하지 않는다.
 */
@Service
public class ChangeSeatAttributeService implements ChangeSeatAttributeUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final Clock clock;

    public ChangeSeatAttributeService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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

    /** 조작 기록의 요약 — "좌석 속성 지정 · 휠체어 2석 (3단계)". 고른 좌석 수를 센다. */
    static String operationSummary(SeatAttribute attribute, int seatCount) {
        return "좌석 속성 지정 · " + label(attribute) + " " + seatCount + "석 (3단계)";
    }

    /** 속성의 화면 이름 — docs/02 "좌석 속성" 표의 이름. */
    private static String label(SeatAttribute attribute) {
        return switch (attribute) {
            case GENERAL -> "일반";
            case WHEELCHAIR -> "휠체어";
            case RESTRICTED_VIEW -> "시야제한";
        };
    }

    @Override
    @Transactional
    public SeatsResult change(ChangeSeatAttributeCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = lockOrganizer(new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        FloorPlan floorPlan = floorPlanRepository.findByEventId(command.eventId());

        if (event.changeSeatAttribute(floorPlan, command.seatIds(), command.attribute(), now)) {
            floorPlanRepository.save(floorPlan, now);
            eventRepository.save(event);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), operationSummary(command.attribute(), command.seatIds().size()), now));
        }
        return FloorPlanResults.seatsOf(floorPlan, command.seatIds());
    }

    private StaffPermissionResult lockOrganizer(CheckStaffPermissionQuery query) {
        return EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository, organizerRepository, query);
    }
}
