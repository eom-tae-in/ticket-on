package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.LogInStaffCommand;
import com.ticketon.application.account.port.in.WithdrawStaffAccountCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffEmailVerificationToken;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 운영진 로그인 탈퇴 — docs/15의 사람들. 박정훈은 극단 열두달(12)의 소유자이고, 이도현은 열두달과 늦여름(30) 두 곳의 운영자다. 늦여름의 소유자는
 * 최여름(99)이다.
 */
@DisplayName("운영진 로그인 탈퇴 유스케이스 (docs/01 \"탈퇴와 주최자 닫기\")")
class WithdrawStaffAccountServiceTest {

    private static final String JUNGHOON_EMAIL = "junghoon@example.com";
    private static final String DOHYUN_EMAIL = "dohyun@example.com";
    private static final long TWELVE_MONTHS = 12L;
    private static final long LATE_SUMMER = 30L;
    private static final long YEOREUM = 99L;

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));
    private static final Instant JOINED_AT = KoreanTime.toInstant(LocalDateTime.of(2023, 5, 1, 10, 0));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

    private final WriteLog writeLog = new WriteLog();
    private final FakeStaffAccountRepository accountRepository = new FakeStaffAccountRepository();
    private final StoredStaffAccounts accounts = new StoredStaffAccounts(accountRepository);
    private final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    private final RecordingStaffOperationUseCase recordStaffOperationUseCase = new RecordingStaffOperationUseCase();
    private final FakeStaffEmailVerificationTokenRepository verificationTokenRepository =
            new FakeStaffEmailVerificationTokenRepository();
    private final FakePasswordResetTokenRepository resetTokenRepository = new FakePasswordResetTokenRepository(writeLog);
    private final RecordingSessionTerminationPort sessionTerminationPort = new RecordingSessionTerminationPort();
    private final MutableClock clock = MutableClock.at(NOW);

    private final WithdrawStaffAccountService service = new WithdrawStaffAccountService(accountRepository,
            staffRepository, recordStaffOperationUseCase, verificationTokenRepository, resetTokenRepository,
            sessionTerminationPort, clock);

    private long junghoon;
    private long dohyun;

    @BeforeEach
    void storeStaff() {
        junghoon = accounts.active(JUNGHOON_EMAIL, "조용한고래");
        dohyun = accounts.active(DOHYUN_EMAIL, "밤의기타");
        OrganizerStaff twelveMonths = OrganizerStaff.startWithOwner(TWELVE_MONTHS, junghoon, APPROVED_AT);
        twelveMonths.addOperator(dohyun, JOINED_AT);
        staffRepository.store(twelveMonths);
        OrganizerStaff lateSummer = OrganizerStaff.startWithOwner(LATE_SUMMER, YEOREUM, APPROVED_AT);
        lateSummer.addOperator(dohyun, APPROVED_AT);
        staffRepository.store(lateSummer);
        verificationTokenRepository.add(StaffEmailVerificationToken.issue("verifyAAAAAAAAAAAAAAAAAAAAAAAAAA", dohyun,
                StoredStaffAccounts.SIGNUP_TIME));
        resetTokenRepository.add(PasswordResetToken.issue("resetAAAAAAAAAAAAAAAAAAAAAAAAAAA", staff(dohyun),
                clock.instant().minusSeconds(600)));
        resetTokenRepository.add(PasswordResetToken.issue("resetBBBBBBBBBBBBBBBBBBBBBBBBBBB", staff(junghoon),
                clock.instant().minusSeconds(600)));
    }

    private static AccountRef staff(long id) {
        return AccountRef.of(AccountKind.STAFF, id);
    }

    private void withdraw(long staffAccountId) {
        service.withdraw(new WithdrawStaffAccountCommand(staff(staffAccountId)));
    }

    @Test
    @DisplayName("운영자가 탈퇴하면 소속된 두 주최자에서 모두 빠지고 소속 이력은 남으며, 주최자마다 조작 기록이 남는다")
    void operatorLeavesEveryOrganizer() {
        withdraw(dohyun);

        Instant now = clock.instant();
        assertThat(staffRepository.findByOrganizerId(TWELVE_MONTHS).memberships())
                .contains(new StaffMembership(TWELVE_MONTHS, dohyun, StaffRole.OPERATOR, JOINED_AT, now));
        assertThat(staffRepository.findByOrganizerId(LATE_SUMMER).memberships())
                .contains(new StaffMembership(LATE_SUMMER, dohyun, StaffRole.OPERATOR, APPROVED_AT, now));
        assertThat(staffRepository.findAffiliationsOf(dohyun).activeMemberships()).isEmpty();
        assertThat(staffRepository.findByOrganizerId(TWELVE_MONTHS).currentOwner()).hasValue(junghoon);
        assertThat(recordStaffOperationUseCase.recordedCommands())
                .extracting(command -> command.permission().organizerId())
                .containsExactly(TWELVE_MONTHS, LATE_SUMMER);
        assertThat(recordStaffOperationUseCase.recordedCommands()).allSatisfy(command -> {
            assertThat(command.permission().staffAccountId()).isEqualTo(dohyun);
            assertThat(command.permission().role()).isEqualTo(StaffRole.OPERATOR);
            assertThat(command.permission().action()).isEqualTo(StaffAction.LEAVE_ORGANIZER);
            assertThat(command.targetKind()).isEqualTo(StaffOperationTargetKind.STAFF_MEMBER);
            assertThat(command.targetId()).isEqualTo(dohyun);
            assertThat(command.summary()).isEqualTo("운영진 로그인 탈퇴로 나감");
            assertThat(command.operatedAt()).isEqualTo(now);
        });
    }

    @Test
    @DisplayName("탈퇴하면 이름 · 이메일 · 비밀번호 해시가 파기되고 닉네임만 남으며, 그 계정의 인증 · 재설정 링크가 지워진다")
    void destroysPersonalInformationAndTokens() {
        withdraw(dohyun);

        StaffAccount saved = accountRepository.findById(dohyun).orElseThrow();
        assertThat(saved.isWithdrawn()).isTrue();
        assertThat(saved.withdrawnAt()).contains(clock.instant());
        assertThat(saved.nickname()).isEqualTo(Nickname.of("밤의기타"));
        assertThat(verificationTokenRepository.savedTokens()).isEmpty();
        assertThat(resetTokenRepository.savedTokens()).extracting(PasswordResetToken::account)
                .containsExactly(staff(junghoon));
    }

    @Test
    @DisplayName("탈퇴하면 그 계정의 로그인 세션을 모두 끊는다 — 계정을 먼저 잠그고 링크를 지운다")
    void terminatesAllSessions() {
        withdraw(dohyun);

        assertThat(sessionTerminationPort.terminatedAccounts()).containsExactly(staff(dohyun));
        assertThat(writeLog.writes()).containsSubsequence("계정 잠금 STAFF:" + dohyun, "링크 지우기 STAFF:" + dohyun);
    }

    @Test
    @DisplayName("탈퇴한 계정으로는 다시 로그인할 수 없다(STAFF_LOGIN_FAILED)")
    void withdrawnAccountCannotLogIn() {
        withdraw(dohyun);

        LogInStaffService logInStaffService = new LogInStaffService(accountRepository, new FakePasswordHashPort());
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> logInStaffService.logIn(new LogInStaffCommand(EmailAddress.of(DOHYUN_EMAIL),
                        new EnteredPassword(StoredStaffAccounts.RAW_PASSWORD))))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.STAFF_LOGIN_FAILED));
    }

    @Test
    @DisplayName("소유자로 남은 주최자가 있으면 \"먼저 소유자를 넘기거나 주최자를 닫아야 합니다\"로 막고 아무것도 바뀌지 않는다")
    void ownerCannotWithdraw() {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> withdraw(junghoon))
                .satisfies(exception -> {
                    assertThat(exception.errorCode()).isEqualTo(OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST);
                    assertThat(exception.errorCode().message()).isEqualTo("먼저 소유자를 넘기거나 주최자를 닫아야 합니다.");
                });

        assertThat(accountRepository.findById(junghoon).orElseThrow().isWithdrawn()).isFalse();
        assertThat(staffRepository.findByOrganizerId(TWELVE_MONTHS).currentOwner()).hasValue(junghoon);
        assertThat(recordStaffOperationUseCase.recordedCommands()).isEmpty();
        assertThat(resetTokenRepository.savedTokens()).hasSize(2);
        assertThat(sessionTerminationPort.terminatedAccounts()).isEmpty();
    }

    @Test
    @DisplayName("소유자를 넘기고 나면 운영자로 남은 주최자에서 빠지며 탈퇴할 수 있다")
    void formerOwnerWithdrawsAfterTransfer() {
        Instant transferredAt = clock.instant().minusSeconds(3_600);
        staffRepository.saveOwnershipTransfer(
                staffRepository.findByOrganizerId(TWELVE_MONTHS).transferOwnership(junghoon, dohyun, transferredAt));

        withdraw(junghoon);

        assertThat(accountRepository.findById(junghoon).orElseThrow().isWithdrawn()).isTrue();
        assertThat(staffRepository.findByOrganizerId(TWELVE_MONTHS).currentOwner()).hasValue(dohyun);
        assertThat(staffRepository.findByOrganizerId(TWELVE_MONTHS).roleOf(junghoon)).isEmpty();
        assertThat(recordStaffOperationUseCase.recordedCommands()).singleElement()
                .extracting(RecordStaffOperationCommand::permission)
                .satisfies(permission -> assertThat(permission.role()).isEqualTo(StaffRole.OPERATOR));
    }

    @Test
    @DisplayName("어느 주최자에도 소속되지 않은 운영진 로그인도 탈퇴한다 — 조작 기록은 남지 않는다")
    void staffWithoutAffiliationWithdraws() {
        long stranger = accounts.active("stranger@example.com", "낯선여행자");

        withdraw(stranger);

        assertThat(accountRepository.findById(stranger).orElseThrow().isWithdrawn()).isTrue();
        assertThat(recordStaffOperationUseCase.recordedCommands()).isEmpty();
        assertThat(sessionTerminationPort.terminatedAccounts()).containsExactly(staff(stranger));
    }

    @Test
    @DisplayName("이미 탈퇴한 계정은 다시 탈퇴하지 못한다(STAFF_WITHDRAWN) — 세션도 다시 끊지 않는다")
    void withdrawnAccountCannotWithdrawAgain() {
        long withdrawn = accounts.withdrawn("left@example.com", "떠난바람");

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> withdraw(withdrawn))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.STAFF_WITHDRAWN));
        assertThat(sessionTerminationPort.terminatedAccounts()).isEmpty();
    }
}
