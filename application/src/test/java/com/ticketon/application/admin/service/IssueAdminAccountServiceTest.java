package com.ticketon.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.admin.port.in.IssueAdminAccountCommand;
import com.ticketon.application.admin.port.in.IssueAdminAccountResult;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.admin.AdminOperationTargetKind;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 계정 발급 유스케이스 — 최고 관리자만, 조작 기록 한 줄")
class IssueAdminAccountServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 14, 30);

    private static final Instant FIRST_ISSUE_TIME = Instant.parse("2026-10-01T00:00:00Z");

    private static final String NEW_ADMIN_EMAIL = "new-admin@ticket-on.example";

    private static final String NEW_ADMIN_PASSWORD = "new-admin-1234";

    private final FakeAdminAccountRepository accountRepository = new FakeAdminAccountRepository();
    private final RecordingAdminOperationRecordRepository recordRepository = new RecordingAdminOperationRecordRepository();
    private final MutableClock clock = MutableClock.at(NOW);

    private final IssueAdminAccountService service = new IssueAdminAccountService(accountRepository,
            new FakePasswordHashPort(), new AdminOperationRecorder(recordRepository), clock);

    @Nested
    @DisplayName("최고 관리자가 발급한다")
    class SuperAdminIssues {

        @Test
        @DisplayName("새 관리자 계정이 이메일·비밀번호 해시·등급·발급 일시로 저장되고, 결과에 id·이메일·등급이 온다")
        void savesNewAdminAccount() {
            long superAdminId = storedSuperAdmin();

            IssueAdminAccountResult result = service.issue(command(superAdminId, NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

            AdminAccount saved = accountRepository.findById(result.adminAccountId()).orElseThrow();
            assertThat(saved.email()).isEqualTo(EmailAddress.of(NEW_ADMIN_EMAIL));
            assertThat(saved.passwordHash()).isEqualTo(FakePasswordHashPort.hashOf(NEW_ADMIN_PASSWORD));
            assertThat(saved.grade()).isEqualTo(AdminGrade.ADMIN);
            assertThat(saved.issuedAt()).isEqualTo(clock.instant());
            assertThat(result).isEqualTo(new IssueAdminAccountResult(result.adminAccountId(), NEW_ADMIN_EMAIL,
                    AdminGrade.ADMIN));
        }

        @Test
        @DisplayName("관리자 조작 기록이 꼭 한 줄 남는다 — 누가(최고 관리자) · 언제 · 무엇을(관리자 계정 발급) · 대상(새 계정) · 요약")
        void leavesOneOperationRecord() {
            long superAdminId = storedSuperAdmin();

            IssueAdminAccountResult result = service.issue(command(superAdminId, NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

            assertThat(recordRepository.records()).singleElement().satisfies(record -> {
                assertThat(record.operator()).isEqualTo(AccountRef.of(AccountKind.ADMIN, superAdminId));
                assertThat(record.operatedAt()).isEqualTo(clock.instant());
                assertThat(record.kind()).isEqualTo(AdminOperationKind.ISSUE_ADMIN_ACCOUNT);
                assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.ADMIN_ACCOUNT);
                assertThat(record.targetId()).isEqualTo(result.adminAccountId());
                assertThat(record.summary()).isEqualTo("관리자 계정 발급 — new-admin@ticket-on.example (관리자)");
                assertThat(record.reason()).isEmpty();
            });
        }

        @Test
        @DisplayName("최고 관리자 계정도 발급한다 — 요약에 '최고 관리자'가 적힌다")
        void issuesSuperAdminAccount() {
            long superAdminId = storedSuperAdmin();

            IssueAdminAccountResult result = service.issue(command(superAdminId, NEW_ADMIN_EMAIL, AdminGrade.SUPER_ADMIN));

            assertThat(result.grade()).isEqualTo(AdminGrade.SUPER_ADMIN);
            assertThat(recordRepository.records()).extracting(AdminOperationRecord::summary)
                    .containsExactly("관리자 계정 발급 — new-admin@ticket-on.example (최고 관리자)");
        }

        @Test
        @DisplayName("발급받은 최고 관리자도 관리자 계정을 발급한다")
        void issuedSuperAdminCanIssue() {
            long secondSuperAdminId = service.issue(command(storedSuperAdmin(), "second-super@ticket-on.example",
                    AdminGrade.SUPER_ADMIN)).adminAccountId();

            service.issue(command(secondSuperAdminId, NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

            assertThat(recordRepository.records()).extracting(AdminOperationRecord::adminAccountId)
                    .endsWith(secondSuperAdminId);
        }
    }

    @Nested
    @DisplayName("발급되지 않는다 — 계정도 기록도 남지 않는다")
    class Rejected {

        @Test
        @DisplayName("관리자는 발급할 수 없다 — 권한 없음 SUPER_ADMIN_ONLY_ACTION")
        void adminCannotIssue() {
            storedSuperAdmin();
            long adminId = accountRepository.store("admin@ticket-on.example", FakePasswordHashPort.hashOf("admin-1234"),
                    AdminGrade.ADMIN, FIRST_ISSUE_TIME);

            assertThatExceptionOfType(PermissionDeniedException.class)
                    .isThrownBy(() -> service.issue(command(adminId, NEW_ADMIN_EMAIL, AdminGrade.ADMIN)))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(AccountErrorCode.SUPER_ADMIN_ONLY_ACTION));
            assertNothingIssued(2);
        }

        @Test
        @DisplayName("같은 이메일(대소문자만 다름)의 관리자 계정이 있으면 막힌다 — '이미 가입된 이메일입니다.'")
        void duplicateEmailIsRejected() {
            long superAdminId = storedSuperAdmin();

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> service.issue(command(superAdminId, "DOHYUN@ticket-on.example", AdminGrade.ADMIN)))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED));
            assertNothingIssued(1);
        }

        @Test
        @DisplayName("확인한 뒤 같은 이메일의 다른 발급이 먼저 저장됐으면 충돌로 막히고 기록을 남기지 않는다")
        void lostIssueRaceLeavesNoRecord() {
            long superAdminId = storedSuperAdmin();
            accountRepository.letAnotherIssueWinNextAdd();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> service.issue(command(superAdminId, NEW_ADMIN_EMAIL, AdminGrade.ADMIN)))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED));
            assertNothingIssued(1);
        }

        @Test
        @DisplayName("로그인한 관리자 계정이 저장소에 없으면 발급하지 않는다")
        void unknownIssuerFails() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> service.issue(command(99, NEW_ADMIN_EMAIL, AdminGrade.ADMIN)));
            assertNothingIssued(0);
        }

        private void assertNothingIssued(int existingAccounts) {
            assertThat(accountRepository.savedAccounts()).hasSize(existingAccounts);
            assertThat(recordRepository.records()).isEmpty();
        }
    }

    /** 맨 처음 최고 관리자 — 만드는 방법은 #22가 정하므로 픽스처로 바로 넣는다. 이도현(docs/15). */
    private long storedSuperAdmin() {
        return accountRepository.store("dohyun@ticket-on.example", FakePasswordHashPort.hashOf("dohyun-admin-1234"),
                AdminGrade.SUPER_ADMIN, FIRST_ISSUE_TIME);
    }

    private static IssueAdminAccountCommand command(long issuerId, String email, AdminGrade grade) {
        return new IssueAdminAccountCommand(AccountRef.of(AccountKind.ADMIN, issuerId), EmailAddress.of(email),
                new NewPassword(NEW_ADMIN_PASSWORD), grade);
    }
}
