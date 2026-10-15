package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.AnswerStaffInvitationCommand;
import com.ticketon.application.organizer.port.in.DeclineStaffInvitationUseCase;
import com.ticketon.application.organizer.port.out.StaffInvitationRepository;
import com.ticketon.domain.organizer.StaffInvitation;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 초대 거절 — 거절 규칙(답했는지 · 유효 기간)은 {@link StaffInvitation#decline}이 보고, 여기서는 로그인한 운영진의 이메일로 온 초대를
 * 읽어 넘긴 뒤 답을 저장한다. 표에서 아직 답을 기다릴 때만 바뀐다.
 * <p>
 * 거절하는 사람은 그 주최자의 운영진이 아니라 운영진 조작 기록에는 남기지 않는다 — 초대에 답한 일시와 사람이 남는다.
 */
@Service
public class DeclineStaffInvitationService implements DeclineStaffInvitationUseCase {

    private final StaffAccountRepository staffAccountRepository;
    private final StaffInvitationRepository invitationRepository;
    private final Clock clock;

    public DeclineStaffInvitationService(StaffAccountRepository staffAccountRepository,
            StaffInvitationRepository invitationRepository, Clock clock) {
        this.staffAccountRepository = staffAccountRepository;
        this.invitationRepository = invitationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void decline(AnswerStaffInvitationCommand command) {
        Instant now = clock.instant();
        AnsweringInvitee invitee = AnsweringInvitee.of(staffAccountRepository, invitationRepository, command);

        invitee.invitation().decline(invitee.staffAccountId(), invitee.email(), now);

        invitationRepository.saveAnswer(invitee.invitation());
    }
}
