package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.WithdrawStaffAccountCommand;
import com.ticketon.application.account.port.in.WithdrawStaffAccountUseCase;
import com.ticketon.application.account.port.out.PasswordResetTokenRepository;
import com.ticketon.application.account.port.out.SessionTerminationPort;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.account.port.out.StaffEmailVerificationTokenRepository;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffAffiliations;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 로그인 탈퇴 — 탈퇴 규칙은 도메인이 본다: 소유자인 주최자가 남으면 막고({@link StaffAffiliations#requireWithdrawable}), 주최자마다
 * 나가고({@link OrganizerStaff#leave}), 계정의 개인정보를 파기한다({@link StaffAccount#withdraw}). 여기서는 그 순서대로 읽고 저장한 뒤 링크를
 * 지우고 세션을 모두 끊는다.
 * <p>
 * <b>한 트랜잭션이다.</b> 계정 잠금 → 소속 읽기 · 소유자 확인 → 계정 탈퇴 저장 → 주최자마다 소속 끝내기 · 조작 기록 → 인증 · 재설정 링크
 * 지우기 → 세션 끊기 순서. 계정 줄을 맨 먼저 잠그고 그 뒤에 소속을 읽는다 — 잠금 순서 "계정 줄 → 초대 줄 → 소속 줄"
 * ({@link StaffAccountRepository#findByIdForUpdate})과 비밀번호 재설정 링크 저장소의 "계정 → 링크"를 함께 따른다. 같은 계정의 탈퇴 두 건,
 * 탈퇴와 비밀번호 재설정, 탈퇴와 초대 수락 · 소유자 넘기기 · 내보내기 · 나가기 · 주최자 신청 승인이 차례로 처리되고 교착하지 않는다. 계정 줄을
 * 잠그지 않는 흐름이 그 사이 소속을 먼저 끝냈으면 소속 끝내기에서 충돌로 멈추고 아무것도 바뀌지 않는다.
 * <p>
 * 조작 기록은 운영진 권한 확인을 거치지 않고 그 사람의 지금 소속(역할)으로 만든다 — 탈퇴는 주최 관리 기능이 아니라 계정의 일이라, 주최자
 * 상태와 상관없이 막지 않는다.
 */
@Service
public class WithdrawStaffAccountService implements WithdrawStaffAccountUseCase {

    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerStaffRepository staffRepository;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffEmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SessionTerminationPort sessionTerminationPort;
    private final Clock clock;

    public WithdrawStaffAccountService(StaffAccountRepository staffAccountRepository,
            OrganizerStaffRepository staffRepository, RecordStaffOperationUseCase recordStaffOperationUseCase,
            StaffEmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository, SessionTerminationPort sessionTerminationPort,
            Clock clock) {
        this.staffAccountRepository = staffAccountRepository;
        this.staffRepository = staffRepository;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.sessionTerminationPort = sessionTerminationPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void withdraw(WithdrawStaffAccountCommand command) {
        Instant now = clock.instant();
        AccountRef account = command.account();
        long staffAccountId = account.id();
        passwordResetTokenRepository.lockAccount(account);
        StaffAccount staffAccount = staffAccountRepository.findById(staffAccountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 운영진 계정이 없습니다: " + account.principalName()));
        StaffAffiliations affiliations = staffRepository.findAffiliationsOf(staffAccountId);

        affiliations.requireWithdrawable();
        staffAccount.withdraw(now);

        staffAccountRepository.saveWithdrawal(staffAccount);
        for (long organizerId : affiliations.activeOrganizerIds()) {
            leave(organizerId, staffAccountId, now);
        }
        emailVerificationTokenRepository.deleteAllOf(staffAccountId);
        passwordResetTokenRepository.deleteAllOf(account);
        sessionTerminationPort.terminateAllSessions(account);
    }

    /** 그 주최자에서 나가고, 그 주최자의 운영진 조작 기록에 남긴다. */
    private void leave(long organizerId, long staffAccountId, Instant now) {
        StaffMembership ended = staffRepository.findByOrganizerId(organizerId).leave(staffAccountId, now);
        staffRepository.saveEnded(ended);
        StaffPermissionResult leaving = new StaffPermissionResult(organizerId, staffAccountId, ended.role(),
                StaffAction.LEAVE_ORGANIZER);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(leaving, StaffOperationTargetKind.STAFF_MEMBER,
                staffAccountId, "운영진 로그인 탈퇴로 나감", now));
    }
}
