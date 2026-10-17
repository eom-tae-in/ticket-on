package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.FloorPlanResult;
import com.ticketon.application.event.port.in.GetEventFloorPlanQuery;
import com.ticketon.application.event.port.in.GetEventFloorPlanUseCase;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.FloorPlanRepository;
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
 * 배치도 에디터용 도면 전체 조회 — 고칠 수 있는 사람인지({@link StaffAction#EDIT_EVENT}, 운영진 누구나) 확인한 뒤 그 주최자의 행사인지 보고 도면을
 * 읽어 옮긴다. 다른 주최자의 행사는 없는 것으로 다룬다. 보기만 하므로 잠그지 않고 조작 기록을 남기지 않는다.
 * <p>
 * <b>스냅숏 하나로 읽는다(REPEATABLE READ).</b> 도면은 여러 문장(구역 → 좌석 → 블록 → 무대)으로 읽는데, 행사 줄을 잠그지 않으므로 문장마다 새
 * 스냅숏을 잡으면(READ COMMITTED) 그 사이에 커밋된 블록 얹기 · 구역 추가 · 좌석 배정을 반쯤만 읽어 좌석 없는 블록, 도면에 없는 구역을 가리키는
 * 좌석이 되어 도면을 되살리지 못한다(500). 트랜잭션 전체를 첫 문장의 스냅숏으로 읽으면 늘 한 시점의 도면이다. PostgreSQL의 읽기 전용
 * REPEATABLE READ는 직렬화 실패로 끊기지 않는다.
 */
@Service
public class GetEventFloorPlanService implements GetEventFloorPlanUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;

    public GetEventFloorPlanService(CheckStaffPermissionUseCase checkStaffPermissionUseCase, EventRepository eventRepository,
            FloorPlanRepository floorPlanRepository) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.eventRepository = eventRepository;
        this.floorPlanRepository = floorPlanRepository;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FloorPlanResult get(GetEventFloorPlanQuery query) {
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(query.actingStaff(), StaffAction.EDIT_EVENT));
        eventRepository.findById(query.eventId())
                .filter(event -> permission.covers(event.organizerId()))
                .orElseThrow(() -> new NotFoundException(EventErrorCode.EVENT_NOT_FOUND,
                        "행사 " + query.eventId() + ", 주최자 " + permission.organizerId()));
        return FloorPlanResults.of(floorPlanRepository.findByEventId(query.eventId()));
    }
}
