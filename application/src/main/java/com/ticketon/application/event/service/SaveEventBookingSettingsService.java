package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.SaveEventBookingSettingsCommand;
import com.ticketon.application.event.port.in.SaveEventBookingSettingsUseCase;
import com.ticketon.application.event.port.out.EventPasswordHashPort;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.EventPasswordHash;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위저드 5단계(공개 수준 · 예매 설정 · 취소 규정 조정) 저장 — 저장하는 규칙(바뀐 항목마다 수정 범위 확인, 암호를 적지 않으면 걸려 있던 암호, 같은
 * 값이면 바꾸지 않음)은 {@link Event#saveBookingStep}이 갖고, 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤, 암호를 새로 적었을 때만 해시해 넘기고,
 * 바뀌었으면 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 잠금 순서와 재확인은 1단계 저장과 같다({@link EventEditingLocks}). 걸려 있던 암호를 다시 적었으면 새로 해시하지 않는다 —
 * 해시는 솔트 때문에 부를 때마다 달라 같은 암호도 바뀐 것으로 보이기 때문이다. 해시와 비교는 일부러 느려(PBKDF2) 행사 줄을 쥔 채 하지만, 암호를
 * 적었을 때만 한다.
 */
@Service
public class SaveEventBookingSettingsService implements SaveEventBookingSettingsUseCase {

    static final String OPERATION_SUMMARY = "행사 예매 설정 저장 (5단계)";

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventPasswordHashPort passwordHashPort;
    private final Clock clock;

    public SaveEventBookingSettingsService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, EventPasswordHashPort passwordHashPort,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.passwordHashPort = passwordHashPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EventWizardResult save(SaveEventBookingSettingsCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        EventPasswordHash newPasswordHash = newPasswordHashOf(event, command.newPassword());

        if (event.saveBookingStep(command.visibility(), newPasswordHash, command.bookingSettings(),
                command.cancellationPolicy(), now)) {
            eventRepository.save(event);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                    command.eventId(), OPERATION_SUMMARY, now));
        }
        return EventWizardResults.of(event);
    }

    /** 새로 정한 암호의 해시 — 적지 않았거나 걸려 있던 암호를 다시 적었으면 null(걸려 있던 암호를 그대로 쓴다). */
    private EventPasswordHash newPasswordHashOf(Event event, EventPassword newPassword) {
        if (newPassword == null) {
            return null;
        }
        EventPasswordHash currentHash = event.visibility().passwordHash();
        if (currentHash != null && passwordHashPort.matches(newPassword, currentHash)) {
            return null;
        }
        return passwordHashPort.hash(newPassword);
    }
}
