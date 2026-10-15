package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.AcceptStaffInvitationResult;
import com.ticketon.application.organizer.port.in.AcceptStaffInvitationUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.AnswerStaffInvitationCommand;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.application.organizer.port.out.StaffInvitationRepository;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffInvitation;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 초대 수락 — 수락 규칙(답했는지 · 유효 기간 · 주최자 활동 · 이미 운영진인지, 운영자 소속 시작)은 {@link StaffInvitation#accept}가
 * 보고, 여기서는 로그인한 운영진의 이메일로 온 초대와 그 주최자 · 명단을 읽어 넘긴 뒤 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이고, 잠금 순서는 계정 줄 → 초대 줄 → 소속 줄이다</b>({@link StaffAccountRepository#findByIdForUpdate}).
 * <ol>
 *   <li>수락하는 사람의 계정 줄을 잠그고 읽는다({@link AnsweringInvitee}) — 탈퇴했으면 멈춘다. 운영진 로그인 탈퇴와 같은 줄을 맨 먼저 잠가,
 *       탈퇴가 커밋되기 전에 들어온 수락이 탈퇴한 계정에 소속을 남기지 않는다. 같은 사람이 같은 초대를 거의 동시에 두 번 수락해도 여기서
 *       차례를 기다린다.</li>
 *   <li>초대 줄을 잠그고 읽는다({@link StaffInvitationRepository#findByIdForUpdate}) — 주최자 · 명단보다 먼저. 늦은 수락은 먼저 수락한
 *       쪽이 커밋한 뒤의 "이미 답함"을 읽고 {@link com.ticketon.domain.organizer.OrganizerErrorCode#STAFF_INVITATION_ALREADY_ANSWERED}로
 *       멈춘다. 잠그지 않고 읽으면 두 읽기 사이에 먼저 수락한 쪽이 커밋할 때 "답을 기다리는 초대"와 "이미 소속된 명단"을 함께 보게 돼 다른
 *       이유로 멈춘다.</li>
 *   <li>초대의 답을 저장한다 — 표에서 아직 답을 기다릴 때만 바뀐다.</li>
 *   <li>운영자 소속 줄을 더한다 — 그 사람의 지금 소속은 표의 유일 색인이 마지막으로 하나만 남긴다.</li>
 * </ol>
 * 교착하지 않는다: 소속 줄을 바꾸는 다른 흐름(넘기기 · 내보내기 · 나가기 · 승인 · 탈퇴)도 관련된 사람의 계정 줄을 맨 먼저 잠그고, 거절도 계정
 * 줄 → 초대 줄 순서다. 다시 보내기는 초대 줄을 잡은 채 소유자 계정 줄의 외래 키 확인 잠금을 기다릴 수 있지만, 소유자 계정 줄을 잡은 채 초대 줄을
 * 기다리는 흐름은 없다. 진 쪽은 충돌로 멈추고 소속도 기록도 남지 않는다.
 * <p>
 * 조작 기록은 수락으로 막 운영자가 된 사람의 일로 남긴다. 다른 주최 관리 기능과 같이 운영진 권한 확인을 거쳐 그때의 역할(운영자)을 얻는다 —
 * 같은 트랜잭션이라 방금 더한 소속이 보인다.
 */
@Service
public class AcceptStaffInvitationService implements AcceptStaffInvitationUseCase {

    private final StaffAccountRepository staffAccountRepository;
    private final StaffInvitationRepository invitationRepository;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final Clock clock;

    public AcceptStaffInvitationService(StaffAccountRepository staffAccountRepository,
            StaffInvitationRepository invitationRepository, OrganizerRepository organizerRepository,
            OrganizerStaffRepository staffRepository, CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, Clock clock) {
        this.staffAccountRepository = staffAccountRepository;
        this.invitationRepository = invitationRepository;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AcceptStaffInvitationResult accept(AnswerStaffInvitationCommand command) {
        Instant now = clock.instant();
        AnsweringInvitee invitee = AnsweringInvitee.of(staffAccountRepository, invitationRepository, command);
        StaffInvitation invitation = invitee.invitation();
        long organizerId = invitation.organizerId();
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("초대가 가리키는 주최자가 없습니다: " + organizerId));

        StaffMembership joined = invitation.accept(invitee.staffAccountId(), invitee.email(), organizer,
                staffRepository.findByOrganizerId(organizerId), now);

        invitationRepository.saveAnswer(invitation);
        staffRepository.addJoinedOperator(joined);
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(new CheckStaffPermissionQuery(
                new ActingStaff(command.account(), organizerId), StaffAction.ACCEPT_STAFF_INVITATION));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                StaffOperationTargetKind.STAFF_INVITATION, command.invitationId(), "운영자 초대 수락", now));
        return new AcceptStaffInvitationResult(organizerId, organizer.name().value(), joined.startedAt());
    }
}
