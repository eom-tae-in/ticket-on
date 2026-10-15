package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 계정")
class AdminAccountTest {

    /** 발급 일시 — 2026-10-15 10:00 (한국 시각). */
    private static final Instant ISSUE_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    /** 비밀번호 해시 — 해시 방식은 어댑터가 정하므로 도메인 테스트에서는 아무 값이나 쓴다. */
    private static final PasswordHash PASSWORD_HASH = new PasswordHash("{test}hashed-password");

    private static final EmailAddress DOHYUN_EMAIL = EmailAddress.of("dohyun@ticket-on.example");

    private static final EmailAddress NEW_ADMIN_EMAIL = EmailAddress.of("new-admin@ticket-on.example");

    @Nested
    @DisplayName("발급 — 최고 관리자만 (docs/01 \"계정 4종\", \"관리자 2등급\")")
    class Issue {

        @Test
        @DisplayName("최고 관리자는 관리자 계정을 발급한다 — id가 없는 새 계정이 이메일·비밀번호 해시·등급·발급 일시를 갖는다")
        void superAdminIssuesAdminAccount() {
            AdminAccount issued = AdminAccount.issue(storedSuperAdmin(1), NEW_ADMIN_EMAIL, PASSWORD_HASH, AdminGrade.ADMIN,
                    EmailAvailability.AVAILABLE, ISSUE_TIME);

            assertThat(issued.id()).isEmpty();
            assertThat(issued.email()).isEqualTo(NEW_ADMIN_EMAIL);
            assertThat(issued.passwordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(issued.grade()).isEqualTo(AdminGrade.ADMIN);
            assertThat(issued.issuedAt()).isEqualTo(ISSUE_TIME);
        }

        @Test
        @DisplayName("최고 관리자는 최고 관리자 계정도 발급한다")
        void superAdminIssuesSuperAdminAccount() {
            AdminAccount issued = AdminAccount.issue(storedSuperAdmin(1), NEW_ADMIN_EMAIL, PASSWORD_HASH,
                    AdminGrade.SUPER_ADMIN, EmailAvailability.AVAILABLE, ISSUE_TIME);

            assertThat(issued.grade()).isEqualTo(AdminGrade.SUPER_ADMIN);
        }

        @Test
        @DisplayName("관리자는 발급할 수 없다 — 권한 없음 SUPER_ADMIN_ONLY_ACTION")
        void adminCannotIssue() {
            assertThatExceptionOfType(PermissionDeniedException.class)
                    .isThrownBy(() -> AdminAccount.issue(storedAdmin(2), NEW_ADMIN_EMAIL, PASSWORD_HASH, AdminGrade.ADMIN,
                            EmailAvailability.AVAILABLE, ISSUE_TIME))
                    .satisfies(exception -> {
                        assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.SUPER_ADMIN_ONLY_ACTION);
                        assertThat(exception.errorCode().message()).isEqualTo("최고 관리자만 할 수 있는 일입니다.");
                    });
        }

        @Test
        @DisplayName("권한을 이메일보다 먼저 본다 — 관리자가 이미 있는 이메일로 발급하려 해도 권한 없음이다")
        void permissionIsCheckedBeforeEmail() {
            assertThatExceptionOfType(PermissionDeniedException.class)
                    .isThrownBy(() -> AdminAccount.issue(storedAdmin(2), NEW_ADMIN_EMAIL, PASSWORD_HASH, AdminGrade.ADMIN,
                            EmailAvailability.ALREADY_REGISTERED, ISSUE_TIME));
        }

        @Test
        @DisplayName("같은 이메일의 관리자 계정이 있으면 막는다 — '이미 가입된 이메일입니다.'")
        void duplicateEmailIsRejected() {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> AdminAccount.issue(storedSuperAdmin(1), NEW_ADMIN_EMAIL, PASSWORD_HASH,
                            AdminGrade.ADMIN, EmailAvailability.ALREADY_REGISTERED, ISSUE_TIME))
                    .satisfies(exception -> {
                        assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED);
                        assertThat(exception.errorCode().message()).isEqualTo("이미 가입된 이메일입니다.");
                    });
        }
    }

    @Nested
    @DisplayName("등급별 권한")
    class Permission {

        @Test
        @DisplayName("관리자는 관리자 줄의 일을 하고, 영구 정지·관리자 계정 발급은 막힌다")
        void adminPermissions() {
            AdminAccount admin = storedAdmin(2);

            assertThat(admin.can(AdminAction.SUSPEND_ORGANIZER)).isTrue();
            assertThat(admin.can(AdminAction.PERMANENTLY_SUSPEND_ORGANIZER)).isFalse();
            assertThat(admin.can(AdminAction.ISSUE_ADMIN_ACCOUNT)).isFalse();
            admin.requirePermission(AdminAction.REISSUE_TICKET_QR);
            assertThatExceptionOfType(PermissionDeniedException.class)
                    .isThrownBy(() -> admin.requirePermission(AdminAction.PERMANENTLY_SUSPEND_ORGANIZER))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(AccountErrorCode.SUPER_ADMIN_ONLY_ACTION));
        }

        @Test
        @DisplayName("최고 관리자는 영구 정지·관리자 계정 발급까지 전부 한다")
        void superAdminPermissions() {
            AdminAccount superAdmin = storedSuperAdmin(1);

            for (AdminAction action : AdminAction.values()) {
                assertThat(superAdmin.can(action)).as(action.name()).isTrue();
                superAdmin.requirePermission(action);
            }
        }
    }

    @Nested
    @DisplayName("저장소에서 되살리기와 로그인 주체")
    class RestoreAndAccountRef {

        @Test
        @DisplayName("되살린 계정은 값을 그대로 갖고, 로그인 주체는 ADMIN:id다")
        void restoresAccount() {
            AdminAccount restored = AdminAccount.restore(7, DOHYUN_EMAIL, PASSWORD_HASH, AdminGrade.SUPER_ADMIN, ISSUE_TIME);

            assertThat(restored.id()).hasValue(7);
            assertThat(restored.email()).isEqualTo(DOHYUN_EMAIL);
            assertThat(restored.passwordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(restored.grade()).isEqualTo(AdminGrade.SUPER_ADMIN);
            assertThat(restored.issuedAt()).isEqualTo(ISSUE_TIME);
            assertThat(restored.accountRef()).isEqualTo(AccountRef.of(AccountKind.ADMIN, 7));
            assertThat(restored.accountRef().principalName()).isEqualTo("ADMIN:7");
        }

        @Test
        @DisplayName("id는 1 이상이어야 한다")
        void rejectsNonPositiveId() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> AdminAccount.restore(0, DOHYUN_EMAIL, PASSWORD_HASH, AdminGrade.ADMIN, ISSUE_TIME));
        }

        @Test
        @DisplayName("저장 전 계정에는 로그인 주체가 없다")
        void newAccountHasNoAccountRef() {
            AdminAccount issued = AdminAccount.issue(storedSuperAdmin(1), NEW_ADMIN_EMAIL, PASSWORD_HASH, AdminGrade.ADMIN,
                    EmailAvailability.AVAILABLE, ISSUE_TIME);

            assertThatIllegalStateException().isThrownBy(issued::accountRef);
        }
    }

    private static AdminAccount storedSuperAdmin(long id) {
        return AdminAccount.restore(id, DOHYUN_EMAIL, PASSWORD_HASH, AdminGrade.SUPER_ADMIN, ISSUE_TIME.minusSeconds(86_400));
    }

    private static AdminAccount storedAdmin(long id) {
        return AdminAccount.restore(id, EmailAddress.of("admin@ticket-on.example"), PASSWORD_HASH, AdminGrade.ADMIN,
                ISSUE_TIME.minusSeconds(3_600));
    }
}
