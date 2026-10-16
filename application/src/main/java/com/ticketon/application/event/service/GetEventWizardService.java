package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.GetEventWizardQuery;
import com.ticketon.application.event.port.in.GetEventWizardUseCase;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 위저드 값 조회 — 고칠 수 있는 사람인지({@link StaffAction#EDIT_EVENT}, 운영진 누구나) 확인한 뒤 그 주최자의 행사를 읽어 옮긴다. 다른 주최자의
 * 행사는 없는 것으로 다룬다. 보기만 하므로 잠그지 않고 조작 기록을 남기지 않는다.
 */
@Service
public class GetEventWizardService implements GetEventWizardUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final EventRepository eventRepository;

    public GetEventWizardService(CheckStaffPermissionUseCase checkStaffPermissionUseCase, EventRepository eventRepository) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public EventWizardResult get(GetEventWizardQuery query) {
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(query.actingStaff(), StaffAction.EDIT_EVENT));
        return eventRepository.findById(query.eventId())
                .filter(event -> permission.covers(event.organizerId()))
                .map(EventWizardResults::of)
                .orElseThrow(() -> new NotFoundException(EventErrorCode.EVENT_NOT_FOUND,
                        "행사 " + query.eventId() + ", 주최자 " + permission.organizerId()));
    }
}
