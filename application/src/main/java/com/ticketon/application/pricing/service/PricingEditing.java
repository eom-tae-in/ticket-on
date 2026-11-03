package com.ticketon.application.pricing.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.application.event.service.EventEditingLocks;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.pricing.port.out.BundleProductRepository;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.pricing.EventPriceTable;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 위저드 4단계 저장(조기예매 · 쿠폰 · 묶음권 추가 · 수정 · 삭제)이 함께 쓰는 차례 — 유스케이스마다 같은 잠금 순서 · 저장 · 조작 기록을 쓰게 한곳에
 * 둔다. 규칙(수정 범위 · 진행 단계는 행사, 할인 · 범위 · 묶음권은 가격 객체)은 도메인이 갖는다.
 * <p>
 * <b>부르는 유스케이스의 트랜잭션 안에서 돈다.</b> 권한 확인 → 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인 → 행사 줄(FOR UPDATE)
 * ({@link EventEditingLocks} — 행사 편집과 같은 차례) → 가격 · 일정 · 도면 읽기 → (도메인이 고침) → 가격 줄 저장 → 행사 저장 → 조작 기록. 같은
 * 행사의 가격 저장과 일정 · 도면 편집은 행사 줄에서 차례로 처리되므로, 두 운영진이 같은 코드의 쿠폰을 거의 동시에 발급하면 뒤의 발급은 앞의 쿠폰을
 * 읽고 막힌다. 가격 줄은 따로 잠그지 않는다.
 */
@Component
class PricingEditing {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventScheduleRepository scheduleRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final BundleProductRepository bundleProductRepository;
    private final Clock clock;

    PricingEditing(CheckStaffPermissionUseCase checkStaffPermissionUseCase, RecordStaffOperationUseCase recordStaffOperationUseCase,
            StaffAccountRepository staffAccountRepository, OrganizerRepository organizerRepository, EventRepository eventRepository,
            EventScheduleRepository scheduleRepository, FloorPlanRepository floorPlanRepository,
            BundleProductRepository bundleProductRepository, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.scheduleRepository = scheduleRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.bundleProductRepository = bundleProductRepository;
        this.clock = clock;
    }

    /**
     * 권한을 확인하고 줄을 차례로 잠근 뒤 행사를 읽는다.
     *
     * @throws com.ticketon.domain.error.PermissionDeniedException 그 주최자의 운영진이 아니거나 주최자가 활동 중이 아니면
     * @throws com.ticketon.domain.error.NotFoundException         없거나 지운 행사, 다른 주최자의 행사면
     */
    LockedEvent lock(ActingStaff actingStaff, long eventId) {
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository,
                organizerRepository, new CheckStaffPermissionQuery(actingStaff, StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, eventId);
        return new LockedEvent(permission, event, clock.instant());
    }

    /** 그 행사의 가격표 — 지금 표의 도면 · 일정 · 묶음권 상품으로. 행사 줄을 잠근 뒤에 부른다. */
    EventPriceTable priceTable(long eventId) {
        return EventPriceTable.of(floorPlanRepository.findByEventId(eventId), scheduleRepository.findByEventId(eventId),
                bundleProductRepository.findByEventId(eventId));
    }

    /**
     * 묶음권은 날짜를 묶으므로 일정이 있어야 한다.
     *
     * @throws RuleViolationException 회차 방식을 아직 고르지 않았으면({@link EventErrorCode#SESSION_MODE_NOT_CHOSEN})
     */
    EventSchedule requireSchedule(long eventId) {
        return scheduleRepository.findByEventId(eventId)
                .orElseThrow(() -> new RuleViolationException(EventErrorCode.SESSION_MODE_NOT_CHOSEN, "행사 " + eventId));
    }

    FloorPlanRepository floorPlanRepository() {
        return floorPlanRepository;
    }

    /** 행사를 저장한다 — 진행 단계 · 마지막 수정 시각이 바뀌었을 때. */
    void saveEvent(LockedEvent locked) {
        eventRepository.save(locked.event());
    }

    /** 조작 기록을 남긴다 — 잠근 뒤 다시 확인한 권한(그때의 역할)으로. */
    void record(LockedEvent locked, String summary) {
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(locked.permission(), StaffOperationTargetKind.EVENT,
                locked.eventId(), summary, locked.now()));
    }

    /**
     * 잠그고 읽은 행사.
     *
     * @param permission 잠근 뒤 다시 확인한 권한 — 조작 기록에 남긴다
     * @param event      FOR UPDATE로 잠근 행사
     * @param now        이 흐름의 지금 시각
     */
    record LockedEvent(StaffPermissionResult permission, Event event, Instant now) {

        LockedEvent {
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(now, "now");
        }

        long eventId() {
            return event.id().orElseThrow();
        }
    }
}
