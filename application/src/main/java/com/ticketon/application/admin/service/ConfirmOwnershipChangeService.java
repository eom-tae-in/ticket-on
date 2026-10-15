package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.admin.port.in.ConfirmOwnershipChangeCommand;
import com.ticketon.application.admin.port.in.ConfirmOwnershipChangeUseCase;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OwnershipChangeRepository;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminAction;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipChange;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유자 변경 사후 확인 — 확인 규칙(한 번만, 관리자만, 넘긴 뒤에만)은 {@link OwnershipChange#confirm}이 보고, 여기서는 관리자 권한을 확인하고
 * 이력을 읽어 확인을 저장하고 조작 기록을 남긴다.
 * <p>
 * 한 트랜잭션이고, 이력은 표에서 아직 확인 전일 때만 바뀐다 — 다른 관리자가 거의 동시에 먼저 확인했으면 충돌로 멈추고 기록이 남지 않는다.
 */
@Service
public class ConfirmOwnershipChangeService implements ConfirmOwnershipChangeUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OwnershipChangeRepository ownershipChangeRepository;
    private final OrganizerRepository organizerRepository;
    private final AdminOperationRecorder operationRecorder;
    private final Clock clock;

    public ConfirmOwnershipChangeService(AdminAccountRepository adminAccountRepository,
            OwnershipChangeRepository ownershipChangeRepository, OrganizerRepository organizerRepository,
            AdminOperationRecorder operationRecorder, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.ownershipChangeRepository = ownershipChangeRepository;
        this.organizerRepository = organizerRepository;
        this.operationRecorder = operationRecorder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void confirm(ConfirmOwnershipChangeCommand command) {
        Instant now = clock.instant();
        AdminAccount admin = ReviewingAdmin.of(adminAccountRepository, command.admin());
        admin.requirePermission(AdminAction.CONFIRM_OWNERSHIP_CHANGE);
        long ownershipChangeId = command.ownershipChangeId();
        OwnershipChange change = ownershipChangeRepository.findById(ownershipChangeId)
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.OWNERSHIP_CHANGE_NOT_FOUND,
                        "소유자 변경 " + ownershipChangeId));

        change.confirm(admin.accountRef(), now);

        ownershipChangeRepository.saveConfirmation(change);
        long organizerId = change.transfer().organizerId();
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("소유자 변경 이력이 가리키는 주최자가 없습니다: " + organizerId));
        operationRecorder.record(AdminOperationRecord.ownershipChangeConfirmed(admin.accountRef(), ownershipChangeId,
                organizer.name(), now));
    }
}
