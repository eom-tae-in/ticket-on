package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationCommand;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationResult;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationUseCase;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.out.OrganizerApplicationRepository;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminAction;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 신청 반려 — 반려 규칙(심사 중일 때만, 사유 필수)은 {@link OrganizerApplication#reject}가 보고, 여기서는 관리자 권한을 확인하고
 * 결과를 저장하고, 사유와 함께 조작 기록을 남기고, 사유를 담은 반려 메일을 보낸다.
 * <p>
 * 심사받던 주최자는 건드리지 않는다 — 심사 중으로 남고 소속도 생기지 않는다({@link OrganizerApplication#reject}). 한 트랜잭션이고, 신청서는
 * 표에서 아직 심사 중일 때만 바뀐다 — 다른 관리자가 거의 동시에 먼저 처리했으면 충돌로 멈추고 기록도 메일도 남지 않는다.
 */
@Service
public class RejectOrganizerApplicationService implements RejectOrganizerApplicationUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OrganizerApplicationRepository applicationRepository;
    private final AdminOperationRecorder operationRecorder;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public RejectOrganizerApplicationService(AdminAccountRepository adminAccountRepository,
            OrganizerApplicationRepository applicationRepository, AdminOperationRecorder operationRecorder,
            SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.applicationRepository = applicationRepository;
        this.operationRecorder = operationRecorder;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RejectOrganizerApplicationResult reject(RejectOrganizerApplicationCommand command) {
        Instant now = clock.instant();
        AdminAccount reviewer = ReviewingAdmin.of(adminAccountRepository, command.reviewer());
        reviewer.requirePermission(AdminAction.REVIEW_ORGANIZER_APPLICATION);
        OrganizerApplication application = applicationRepository.findById(command.applicationId())
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.APPLICATION_NOT_FOUND,
                        "신청서 " + command.applicationId()));

        application.reject(command.reason(), now);

        applicationRepository.saveReview(application);
        operationRecorder.record(AdminOperationRecord.organizerApplicationRejected(reviewer.accountRef(),
                command.applicationId(), application.form().name(), command.reason(), now));
        sendNotificationUseCase.send(
                OrganizerApplicationRejectedNotice.commandFor(application.form(), command.reason()));
        return new RejectOrganizerApplicationResult(command.applicationId(), application.form().name().value(),
                command.reason().text(), now);
    }
}
