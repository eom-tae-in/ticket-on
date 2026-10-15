package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationCommand;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationResult;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationUseCase;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.out.OrganizerApplicationRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminAction;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 신청 승인 — 승인 규칙(신청서·주최자가 심사 중일 때만, 주최자 활동, 신청한 사람이 소유자)은 {@link OrganizerApplication#approve}가
 * 보고, 여기서는 관리자 권한을 확인하고 신청서·주최자를 읽어 넘긴 뒤 결과를 저장하고, 조작 기록을 남기고, 승인 메일을 보낸다.
 * <p>
 * <b>한 트랜잭션이다.</b> 신청서는 표에서 아직 심사 중일 때만 바뀐다({@link OrganizerApplicationRepository#saveReview}) — 다른 관리자가 거의
 * 동시에 먼저 처리했으면 충돌로 멈추고, 그때는 주최자·소속·조작 기록이 되돌려지고 메일도 나가지 않는다. 신청서를 먼저 저장해 경합을 가장 먼저
 * 가른다. 메일이 실패해도 승인은 그대로다 — 발송 결과는 발송 이력에 남는다.
 * <p>
 * 신청한 사람의 운영진 계정 줄을 먼저 잠그고 읽는다(잠금 순서 계정 줄 → 소속 줄, {@link StaffAccountRepository#findByIdForUpdate}). 운영진
 * 로그인 탈퇴도 같은 줄을 먼저 잠그므로, 탈퇴가 먼저면 탈퇴한 계정을 보고 승인을 막고({@link OrganizerApplication#approve}), 승인이 먼저면 탈퇴는
 * 소유자가 된 사람을 보고 막힌다 — 탈퇴한 계정이 소유자가 되지 않는다.
 */
@Service
public class ApproveOrganizerApplicationService implements ApproveOrganizerApplicationUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerApplicationRepository applicationRepository;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final AdminOperationRecorder operationRecorder;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public ApproveOrganizerApplicationService(AdminAccountRepository adminAccountRepository,
            StaffAccountRepository staffAccountRepository, OrganizerApplicationRepository applicationRepository,
            OrganizerRepository organizerRepository, OrganizerStaffRepository staffRepository,
            AdminOperationRecorder operationRecorder, SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.applicationRepository = applicationRepository;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.operationRecorder = operationRecorder;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ApproveOrganizerApplicationResult approve(ApproveOrganizerApplicationCommand command) {
        Instant now = clock.instant();
        AdminAccount reviewer = ReviewingAdmin.of(adminAccountRepository, command.reviewer());
        reviewer.requirePermission(AdminAction.APPROVE_ORGANIZER_APPLICATION);
        OrganizerApplication application = applicationRepository.findById(command.applicationId())
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.APPLICATION_NOT_FOUND,
                        "신청서 " + command.applicationId()));
        StaffAccount applicant = staffAccountRepository.findByIdForUpdate(application.applicantStaffAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "신청서를 낸 운영진 계정이 없습니다: " + application.applicantStaffAccountId()));
        Organizer organizer = organizerRepository.findById(application.organizerId())
                .orElseThrow(() -> new IllegalStateException("신청서가 가리키는 주최자가 없습니다: " + application.organizerId()));

        OrganizerStaff openedStaff = application.approve(organizer, applicant, now);

        applicationRepository.saveReview(application);
        organizerRepository.saveApproval(organizer);
        staffRepository.addOpened(openedStaff);
        operationRecorder.record(AdminOperationRecord.organizerApplicationApproved(reviewer.accountRef(),
                command.applicationId(), application.form().name(), now));
        sendNotificationUseCase.send(OrganizerApplicationApprovedNotice.commandFor(application.form()));
        return new ApproveOrganizerApplicationResult(command.applicationId(), application.organizerId(),
                organizer.name().value(), application.applicantStaffAccountId(), now);
    }
}
