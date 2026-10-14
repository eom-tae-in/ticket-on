package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 계정의 비밀번호 새로 정하기 — 비밀번호 찾기는 로그인을 되찾는 일이라, 로그인할 수 있는 계정만 비밀번호를 다시 정한다.
 * 재설정 링크의 규칙(유효 시간·1회용·대체)은 {@link PasswordResetTokenTest}가 본다.
 */
@DisplayName("계정 비밀번호 새로 정하기")
class AccountPasswordResetTest {

    private static final long ACCOUNT_ID = 7;

    private static final PasswordHash NEW_PASSWORD_HASH = new PasswordHash("{test}new-password");

    @Nested
    @DisplayName("관객 계정")
    class Audience {

        @Test
        @DisplayName("활동 중인 계정은 비밀번호 찾기를 할 수 있고, 새 해시로 바뀐다 — 지금 비밀번호는 묻지 않는다")
        void activeAccountResets() {
            AudienceAccount account = AudienceAccountFixtures.activeAccount();

            assertThat(account.can(AudienceAction.RESET_PASSWORD)).isTrue();
            account.resetPassword(NEW_PASSWORD_HASH);

            assertThat(account.passwordHash()).isEqualTo(NEW_PASSWORD_HASH);
        }

        @Test
        @DisplayName("이용 정지 중인 계정도 할 수 있다 — 정지 중에도 로그인은 된다")
        void suspendedAccountResets() {
            AudienceAccount account = AudienceAccountFixtures.activeAccount();
            account.suspend("번호를 바꿔가며 1인 매수 제한을 넘는 예매", SIGNUP_TIME.plus(Duration.ofDays(1)));

            assertThat(account.can(AudienceAction.RESET_PASSWORD)).isTrue();
            account.resetPassword(NEW_PASSWORD_HASH);

            assertThat(account.passwordHash()).isEqualTo(NEW_PASSWORD_HASH);
        }

        @Test
        @DisplayName("인증 대기 계정은 할 수 없다 — 비밀번호가 맞아도 로그인할 수 없는 계정이다")
        void pendingAccountCannotReset() {
            AudienceAccount account = AudienceAccountFixtures.newlySignedUpAccount();

            assertThat(account.can(AudienceAction.RESET_PASSWORD)).isFalse();
            assertRejected(() -> account.resetPassword(NEW_PASSWORD_HASH), AccountErrorCode.AUDIENCE_NOT_ACTIVE);
            assertThat(account.passwordHash()).isEqualTo(AudienceAccountFixtures.PASSWORD_HASH);
        }

        @Test
        @DisplayName("탈퇴한 계정은 할 수 없다")
        void withdrawnAccountCannotReset() {
            AudienceAccount active = AudienceAccountFixtures.activeAccount();
            AudienceAccount account = AudienceAccount.restore(ACCOUNT_ID, active.personalInformation(),
                    active.passwordHash(), active.signedUpAt(), active.consentRecords(), AudienceAccountStatus.WITHDRAWN,
                    active.emailVerifiedAt().orElse(null), active.suspensions());

            assertThat(account.can(AudienceAction.RESET_PASSWORD)).isFalse();
            assertRejected(() -> account.resetPassword(NEW_PASSWORD_HASH), AccountErrorCode.AUDIENCE_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("운영진 로그인")
    class Staff {

        @Test
        @DisplayName("활동 중인 계정은 비밀번호 찾기를 할 수 있고, 새 해시로 바뀐다")
        void activeAccountResets() {
            StaffAccount account = StaffAccountFixtures.storedActiveAccount(ACCOUNT_ID);

            assertThat(account.canResetPassword()).isTrue();
            account.resetPassword(NEW_PASSWORD_HASH);

            assertThat(account.passwordHash()).isEqualTo(NEW_PASSWORD_HASH);
        }

        @Test
        @DisplayName("인증 대기 계정은 할 수 없다")
        void pendingAccountCannotReset() {
            StaffAccount account = StaffAccountFixtures.storedPendingAccount(ACCOUNT_ID);

            assertThat(account.canResetPassword()).isFalse();
            StaffAccountFixtures.assertRejected(() -> account.resetPassword(NEW_PASSWORD_HASH),
                    AccountErrorCode.STAFF_NOT_ACTIVE);
            assertThat(account.passwordHash()).isEqualTo(StaffAccountFixtures.PASSWORD_HASH);
        }

        @Test
        @DisplayName("탈퇴한 계정은 할 수 없다")
        void withdrawnAccountCannotReset() {
            StaffAccount account = StaffAccountFixtures.storedWithdrawnAccount(ACCOUNT_ID);

            assertThat(account.canResetPassword()).isFalse();
            StaffAccountFixtures.assertRejected(() -> account.resetPassword(NEW_PASSWORD_HASH),
                    AccountErrorCode.STAFF_WITHDRAWN);
        }
    }
}
