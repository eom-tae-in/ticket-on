package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.application.event.port.in.SaveEventBasicInformationUseCase;
import com.ticketon.application.event.port.out.EventCategoryRepository;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위저드 1단계(기본 정보) 저장 — 저장하는 규칙(바뀐 항목마다 수정 범위 확인, 같은 값이면 바꾸지 않음, 진행 단계 · 마지막 수정 시각)은
 * {@link Event#saveBasicInformationStep}이 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤 카테고리를 고르고, 바뀌었으면 저장하고 조작 기록을
 * 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인 → 행사 줄(FOR UPDATE) → 저장 → 조작 기록
 * ({@link EventEditingLocks}). 지금과 같은 값이면(자동 임시저장을 여러 번 해도) 저장도 기록도 하지 않는다. 같은 행사를 거의 동시에 저장하면 뒤의
 * 저장은 행사 줄에서 앞의 저장을 기다렸다가 바뀐 값을 읽는다 — 같은 값이면 뒤의 저장은 바뀐 것이 없다.
 * <p>
 * 수정 범위는 저장된 상태로 판정한다. 시간이 움직이는 상태(판매 대기 · 판매 중 등)의 행사에 지금 시각을 반영하는 일은 일정(#41)이 생긴 뒤 상태별
 * 설정 잠금(#65)이 더한다 — 이 이슈의 행사는 판매를 시작할 수 없어 늘 작성 중이다.
 */
@Service
public class SaveEventBasicInformationService implements SaveEventBasicInformationUseCase {

    static final String OPERATION_SUMMARY = "행사 기본 정보 저장 (1단계)";

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final Clock clock;

    public SaveEventBasicInformationService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, EventCategoryRepository categoryRepository,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EventWizardResult save(SaveEventBasicInformationCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        EventBasicInformation basicInformation = command.basicInformation().toBasicInformation(categoryRepository.loadCatalog());

        if (event.saveBasicInformationStep(basicInformation, now)) {
            eventRepository.save(event);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), OPERATION_SUMMARY, now));
        }
        return EventWizardResults.of(event);
    }
}
