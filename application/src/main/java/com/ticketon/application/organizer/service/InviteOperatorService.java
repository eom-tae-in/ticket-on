package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.InviteOperatorCommand;
import com.ticketon.application.organizer.port.in.InviteOperatorResult;
import com.ticketon.application.organizer.port.in.InviteOperatorUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.application.organizer.port.out.StaffInvitationRepository;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffInvitation;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 초대 — 보내는 규칙(소유자만, 이미 운영진인 사람 제외, 유효 기간, 지난 초대 다시 열기)은 {@link StaffInvitation}이 보고, 여기서는
 * 운영진 권한을 확인하고 명단 · 초대받는 이메일의 운영진 로그인 · 기다리는 초대를 읽어 넘긴 뒤 저장하고, 조작 기록을 남기고, 초대 메일을 보낸다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 초대 저장 → 조작 기록 → 메일 순서(주최 관리 기능의 공통 순서, {@link CheckStaffPermissionUseCase}).
 * 같은 이메일로 거의 동시에 두 번 초대해 저장에서 지면 충돌로 멈추고 기록도 메일도 남지 않는다. 메일이 실패해도 초대는 그대로다 — 발송 결과는
 * 발송 이력에 남고, 받은 사람은 내 주최 홈의 "받은 초대"에서도 본다.
 */
@Service
public class InviteOperatorService implements InviteOperatorUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final StaffInvitationRepository invitationRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final WebAddress webAddress;
    private final Clock clock;

    public InviteOperatorService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerRepository organizerRepository,
            OrganizerStaffRepository staffRepository, StaffInvitationRepository invitationRepository,
            StaffAccountRepository staffAccountRepository, SendNotificationUseCase sendNotificationUseCase,
            WebAddress webAddress, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.invitationRepository = invitationRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.webAddress = webAddress;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InviteOperatorResult invite(InviteOperatorCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.INVITE_STAFF));
        long organizerId = permission.organizerId();
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));
        EmailAddress inviteeEmail = command.inviteeEmail();

        StaffInvitation invitation = send(staffRepository.findByOrganizerId(organizerId), permission.staffAccountId(),
                inviteeEmail, now);

        long invitationId = invitation.id().orElseThrow(() -> new IllegalStateException("저장한 초대에 id가 없습니다"));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                StaffOperationTargetKind.STAFF_INVITATION, invitationId, "운영자 초대 · " + inviteeEmail.value(), now));
        sendNotificationUseCase.send(OperatorInvitationNotice.commandFor(organizer.name(), invitation, webAddress));
        return new InviteOperatorResult(invitationId, inviteeEmail.value(), invitation.invitedAt(), invitation.expiresAt());
    }

    /** 새 초대를 보내거나, 같은 이메일로 기다리는 초대가 있으면 그 초대를 다시 연다(아직 유효하면 도메인이 막는다). */
    private StaffInvitation send(OrganizerStaff staff, long invitedByStaffAccountId, EmailAddress inviteeEmail,
            Instant now) {
        OptionalLong inviteeStaffAccountId = staffAccountIdOf(inviteeEmail);
        Optional<StaffInvitation> pendingInvitation = invitationRepository.findPending(staff.organizerId(), inviteeEmail);
        if (pendingInvitation.isEmpty()) {
            return invitationRepository.add(
                    StaffInvitation.issue(staff, invitedByStaffAccountId, inviteeEmail, inviteeStaffAccountId, now));
        }
        StaffInvitation invitation = pendingInvitation.get();
        invitation.reinvite(staff, invitedByStaffAccountId, inviteeStaffAccountId, now);
        invitationRepository.saveReinvitation(invitation);
        return invitation;
    }

    /** 그 이메일로 가입한 운영진 로그인 — 이미 그 주최자의 운영진인지 보는 데 쓴다. 없으면 비어 있다. */
    private OptionalLong staffAccountIdOf(EmailAddress email) {
        return staffAccountRepository.findRegisteredByEmail(email)
                .map(account -> account.id().orElseThrow())
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }
}
