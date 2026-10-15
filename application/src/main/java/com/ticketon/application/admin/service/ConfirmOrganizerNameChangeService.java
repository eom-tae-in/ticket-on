package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.admin.port.in.ConfirmOrganizerNameChangeCommand;
import com.ticketon.application.admin.port.in.ConfirmOrganizerNameChangeUseCase;
import com.ticketon.application.organizer.port.out.OrganizerNameChangeRepository;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminAction;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerNameChangeEntry;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자명 변경 사후 확인 — 확인 규칙(한 번만, 관리자만, 바꾼 뒤에만)은 {@link OrganizerNameChangeEntry#confirm}이 보고, 여기서는 관리자 권한을
 * 확인하고 이력을 읽어 확인을 저장하고 조작 기록을 남긴다. 소유자 변경 사후 확인({@link ConfirmOwnershipChangeService})과 같은 방식이다.
 * <p>
 * 한 트랜잭션이고, 이력은 표에서 아직 확인 전일 때만 바뀐다 — 다른 관리자가 거의 동시에 먼저 확인했으면 충돌로 멈추고 기록이 남지 않는다.
 */
@Service
public class ConfirmOrganizerNameChangeService implements ConfirmOrganizerNameChangeUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OrganizerNameChangeRepository nameChangeRepository;
    private final AdminOperationRecorder operationRecorder;
    private final Clock clock;

    public ConfirmOrganizerNameChangeService(AdminAccountRepository adminAccountRepository,
            OrganizerNameChangeRepository nameChangeRepository, AdminOperationRecorder operationRecorder, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.nameChangeRepository = nameChangeRepository;
        this.operationRecorder = operationRecorder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void confirm(ConfirmOrganizerNameChangeCommand command) {
        Instant now = clock.instant();
        AdminAccount admin = ReviewingAdmin.of(adminAccountRepository, command.admin());
        admin.requirePermission(AdminAction.CONFIRM_ORGANIZER_NAME_CHANGE);
        long organizerNameChangeId = command.organizerNameChangeId();
        OrganizerNameChangeEntry entry = nameChangeRepository.findById(organizerNameChangeId)
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.ORGANIZER_NAME_CHANGE_NOT_FOUND,
                        "주최자명 변경 " + organizerNameChangeId));

        entry.confirm(admin.accountRef(), now);

        nameChangeRepository.saveConfirmation(entry);
        operationRecorder.record(AdminOperationRecord.organizerNameChangeConfirmed(admin.accountRef(),
                organizerNameChangeId, entry.change(), now));
    }
}
