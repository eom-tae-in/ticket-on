package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.port.in.GetEventScheduleQuery;
import com.ticketon.application.event.port.in.GetEventScheduleUseCase;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 일정 조회(위저드 2단계) — 고칠 수 있는 사람인지({@link StaffAction#EDIT_EVENT}, 운영진 누구나) 확인한 뒤 그 주최자의 행사인지 보고 일정을
 * 읽어 옮긴다. 다른 주최자의 행사는 없는 것으로 다룬다. 회차 방식을 아직 고르지 않았으면 빈 일정이다. 보기만 하므로 잠그지 않고 조작 기록을 남기지
 * 않는다.
 * <p>
 * <b>스냅숏 하나로 읽는다(REPEATABLE READ).</b> 일정은 여러 문장(일정 → 회차 → 날짜)으로 읽는데, 행사 줄을 잠그지 않으므로 문장마다 새 스냅숏을
 * 잡으면(READ COMMITTED) 그 사이에 커밋된 회차 추가 · 삭제를 반쯤만 읽어 회차 없는 날짜가 되어 일정을 되살리지 못한다(500). 이유는
 * {@link GetEventFloorPlanService}와 같다.
 */
@Service
public class GetEventScheduleService implements GetEventScheduleUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final EventRepository eventRepository;
    private final EventScheduleRepository scheduleRepository;

    public GetEventScheduleService(CheckStaffPermissionUseCase checkStaffPermissionUseCase, EventRepository eventRepository,
            EventScheduleRepository scheduleRepository) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.eventRepository = eventRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EventScheduleResult get(GetEventScheduleQuery query) {
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(query.actingStaff(), StaffAction.EDIT_EVENT));
        eventRepository.findById(query.eventId())
                .filter(event -> permission.covers(event.organizerId()))
                .orElseThrow(() -> new NotFoundException(EventErrorCode.EVENT_NOT_FOUND,
                        "행사 " + query.eventId() + ", 주최자 " + permission.organizerId()));
        return scheduleRepository.findByEventId(query.eventId())
                .map(ScheduleResults::of)
                .orElseGet(() -> EventScheduleResult.notChosen(query.eventId()));
    }
}
