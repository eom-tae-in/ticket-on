package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.SessionAdded;
import com.ticketon.domain.event.SessionRemoved;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 일정 편집 흐름(회차 방식 선택, 날짜 · 회차 추가 · 수정 · 삭제, 날짜별 운영 시간, 출연, 회차 가격 조정)이 함께 쓰는 차례 — 위저드 2단계를 저장하는
 * 유스케이스마다 같은 잠금 순서 · 저장 · 알림 · 조작 기록을 쓰게 한곳에 둔다. 규칙(수정 범위, 회차 방식, 시각, 진행 단계 · 마지막 수정 시각)은
 * 행사({@link Event})가 갖는다.
 * <p>
 * <b>부르는 유스케이스의 트랜잭션 안에서 돈다.</b> 권한 확인 → 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인 → 행사 줄(FOR UPDATE)
 * ({@link EventEditingLocks}) → 일정 읽기 → (도메인이 고침) → 바뀌었으면 일정 저장 → 행사 저장 → 알림 → 조작 기록. 같은 행사의 일정 저장은 행사
 * 줄에서 차례로 처리되므로, 두 운영진이 같은 날짜 · 시각의 회차를 거의 동시에 더하면 뒤의 추가는 앞의 회차를 읽고 막힌다. 일정 · 날짜 · 회차 줄은
 * 따로 잠그지 않는다.
 * <p>
 * <b>바뀐 것이 없으면</b>(자동 임시저장을 여러 번 해도) 저장도 알림도 기록도 하지 않는다.
 * <p>
 * <b>재고에 알린다</b> — 저장 전후의 회차 id를 견줘 생긴 회차마다 {@link SessionAdded}, 지운 회차마다 {@link SessionRemoved}를 같은 트랜잭션 안에서
 * 발행한다. 듣는 쪽(재고, #54)이 같은 트랜잭션에서 처리하면 함께 커밋되거나 함께 되돌려진다. 회차를 다른 날짜로 옮기는 것은 같은 회차라 알리지 않는다.
 * <p>
 * 수정 범위는 저장된 상태로 판정한다. 시간이 움직이는 상태의 행사에 지금 시각을 반영하는 일은 상태별 설정 잠금(#65)이 더한다.
 */
@Component
class EventScheduleEditing {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventScheduleRepository scheduleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    EventScheduleEditing(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, EventScheduleRepository scheduleRepository,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.scheduleRepository = scheduleRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 권한을 확인하고 줄을 차례로 잠근 뒤 행사와 일정을 읽는다.
     *
     * @throws com.ticketon.domain.error.PermissionDeniedException 그 주최자의 운영진이 아니거나 주최자가 활동 중이 아니면
     * @throws com.ticketon.domain.error.NotFoundException         없거나 지운 행사, 다른 주최자의 행사면
     */
    LockedSchedule lock(ActingStaff actingStaff, long eventId) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository,
                organizerRepository, new CheckStaffPermissionQuery(actingStaff, StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, eventId);
        return new LockedSchedule(permission, event, scheduleRepository.findByEventId(eventId), now);
    }

    /**
     * 일정이 있어야 하는 편집 — 줄을 잠가 읽고, 고치고, 바뀌었으면 저장 · 알림 · 기록한다.
     *
     * @throws RuleViolationException 회차 방식을 아직 고르지 않았으면({@link EventErrorCode#SESSION_MODE_NOT_CHOSEN}), 도메인이 막으면
     */
    EventScheduleResult edit(ActingStaff actingStaff, long eventId, ScheduleEdit edit) {
        LockedSchedule locked = lock(actingStaff, eventId);
        EventSchedule schedule = locked.schedule().orElseThrow(
                () -> new RuleViolationException(EventErrorCode.SESSION_MODE_NOT_CHOSEN, "행사 " + eventId));
        Set<Long> sessionIdsBefore = ScheduleResults.sessionIdsOf(schedule);
        Optional<String> summary = edit.apply(locked.event(), schedule, locked.now());
        if (summary.isEmpty()) {
            return ScheduleResults.of(schedule);
        }
        return saveChanged(locked, schedule, sessionIdsBefore, summary.get());
    }

    /**
     * 바뀐 일정 · 행사를 저장하고, 생기거나 지워진 회차를 재고에 알리고, 조작 기록을 남긴다.
     *
     * @param sessionIdsBefore 고치기 전 일정의 회차 id
     * @param summary          조작 기록 요약
     * @return 저장한 일정
     */
    EventScheduleResult saveChanged(LockedSchedule locked, EventSchedule edited, Set<Long> sessionIdsBefore, String summary) {
        long eventId = locked.event().id().orElseThrow();
        EventSchedule saved = scheduleRepository.save(edited, locked.now());
        eventRepository.save(locked.event());
        Set<Long> sessionIdsAfter = ScheduleResults.sessionIdsOf(saved);
        sessionIdsBefore.stream().filter(sessionId -> !sessionIdsAfter.contains(sessionId)).sorted()
                .forEach(sessionId -> eventPublisher.publishEvent(new SessionRemoved(eventId, sessionId)));
        sessionIdsAfter.stream().filter(sessionId -> !sessionIdsBefore.contains(sessionId)).sorted()
                .forEach(sessionId -> eventPublisher.publishEvent(new SessionAdded(eventId, sessionId)));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(locked.permission(), StaffOperationTargetKind.EVENT, eventId,
                summary, locked.now()));
        return ScheduleResults.of(saved);
    }

    /**
     * 잠그고 읽은 행사와 일정.
     *
     * @param permission 잠근 뒤 다시 확인한 권한 — 조작 기록에 남긴다
     * @param event      FOR UPDATE로 잠근 행사
     * @param schedule   행사의 일정. 회차 방식을 아직 고르지 않았으면 비어 있다
     * @param now        이 흐름의 지금 시각
     */
    record LockedSchedule(StaffPermissionResult permission, Event event, Optional<EventSchedule> schedule, Instant now) {

        LockedSchedule {
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(schedule, "schedule");
            Objects.requireNonNull(now, "now");
        }
    }

    /** 잠그고 읽은 행사의 일정을 고치는 일 — 행사의 메서드를 부른다. */
    @FunctionalInterface
    interface ScheduleEdit {

        /** @return 바뀌었으면 조작 기록 요약, 바뀐 것이 없으면 빈 값 */
        Optional<String> apply(Event event, EventSchedule schedule, Instant now);
    }
}
