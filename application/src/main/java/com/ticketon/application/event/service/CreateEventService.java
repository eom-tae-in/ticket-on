package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.out.EventCategoryRepository;
import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.common.TokenGenerator;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 만들기 — 1단계 기본 정보와 함께 행사를 처음 저장한다(빈 초안 없음). 만드는 규칙(작성 중 · 처음 값 · 진행 단계)은 {@link Event#create}가
 * 갖고, 여기서는 권한을 확인하고 줄을 잠근 뒤 저장된 카테고리 목록으로 카테고리를 고르고, 공개 주소를 발급해 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 만드는 사람의 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인 → 카테고리 고르기 → 공개 주소
 * 발급 → 행사 저장 → 조작 기록({@link EventEditingLocks}). 공개 주소는 TokenGenerator로 URL 안전 문자
 * {@value EventPublicAddress#LENGTH}자를 만든다 — 192비트라 겹칠 일이 사실상 없고, 겹치면 유일 색인이 막는다.
 */
@Service
public class CreateEventService implements CreateEventUseCase {

    static final String OPERATION_SUMMARY = "행사 만들기";

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final EventPosterStoragePort posterStorage;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public CreateEventService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, EventCategoryRepository categoryRepository,
            EventPosterStoragePort posterStorage, TokenGenerator tokenGenerator, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.posterStorage = posterStorage;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EventWizardResult create(CreateEventCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.CREATE_EVENT));
        EventBasicInformation basicInformation = command.basicInformation().toBasicInformation(categoryRepository.loadCatalog());
        EventPublicAddress publicAddress = EventPublicAddress.issue(tokenGenerator.urlSafe(EventPublicAddress.LENGTH));

        Event created = eventRepository.add(Event.create(permission.organizerId(), publicAddress, basicInformation, now));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                created.id().orElseThrow(), OPERATION_SUMMARY, now));
        return EventWizardResults.of(created, posterStorage);
    }
}
