package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("비밀번호 재설정 링크")
class PasswordResetTokenTest {

    private static final long ACCOUNT_ID = 7;

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, ACCOUNT_ID);

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, ACCOUNT_ID);

    private static final String TOKEN_VALUE = "resetAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    /** 비밀번호 찾기를 한 일시 — 가입한 다음 날 2026-10-16 10:00 (한국 시각). */
    private static final Instant ISSUED_AT = SIGNUP_TIME.plus(Duration.ofDays(1));

    private static final PasswordHash NEW_PASSWORD_HASH = new PasswordHash("{test}new-password");

    @Test
    @DisplayName("유효 시간은 1시간이다 (확인 필요 #16) — 보낸 때부터 센다")
    void validForOneHourFromIssue() {
        PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT);

        assertThat(ProposedPolicy.PASSWORD_RESET_LINK_VALIDITY).isEqualTo(Duration.ofHours(1));
        assertThat(token.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(token.expiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofHours(1)));
        assertThat(token.account()).isEqualTo(SEOYUN);
        assertThat(token.usedAt()).isEmpty();
        assertThat(token.replacedAt()).isEmpty();
        assertThat(TOKEN_VALUE).hasSize(PasswordResetToken.LENGTH);
    }

    @Test
    @DisplayName("1시간에서 1초 모자라면 아직 유효하고, 1시간이 되는 순간부터 지난 것이다")
    void expiresExactlyAtOneHour() {
        PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT);

        assertThat(token.isExpiredAt(ISSUED_AT.plus(Duration.ofHours(1)).minusSeconds(1))).isFalse();
        assertThat(token.isExpiredAt(ISSUED_AT.plus(Duration.ofHours(1)))).isTrue();
    }

    @Test
    @DisplayName("문자열로 바꿔도 링크 값은 드러나지 않는다")
    void toStringHidesValue() {
        assertThat(PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT).toString()).doesNotContain(TOKEN_VALUE);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"ADMIN", "CHECKIN"})
    @DisplayName("관리자·검표 계정에는 링크를 만들지 않는다 — 비밀번호 찾기는 관객/주최자 중 고른다 (docs/13)")
    void onlyAudienceAndStaff(AccountKind kind) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PasswordResetToken.issue(TOKEN_VALUE, AccountRef.of(kind, ACCOUNT_ID), ISSUED_AT));
        assertThat(PasswordResetToken.RESETTABLE_KINDS).containsExactlyInAnyOrder(AccountKind.AUDIENCE, AccountKind.STAFF);
    }

    @Test
    @DisplayName("쓴 일시와 대체된 일시가 둘 다 있는 링크는 되살리지 않는다")
    void usedAndReplacedCannotBothExist() {
        assertThatIllegalArgumentException().isThrownBy(() -> PasswordResetToken.restore(TOKEN_VALUE, SEOYUN, ISSUED_AT,
                ISSUED_AT.plus(Duration.ofHours(1)), ISSUED_AT.plusSeconds(60), ISSUED_AT.plusSeconds(120)));
    }

    @Nested
    @DisplayName("관객 계정의 비밀번호 바꾸기")
    class AudienceReset {

        @Test
        @DisplayName("유효 시간 안에 열면 비밀번호가 새 해시로 바뀌고, 바꾼 시각이 쓴 일시로 남는다")
        void resetsPasswordWithinValidity() {
            AudienceAccount account = storedAudience(AudienceAccountFixtures.activeAccount());
            PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT);
            Instant openedAt = ISSUED_AT.plus(Duration.ofMinutes(59));

            token.resetPassword(account, NEW_PASSWORD_HASH, openedAt);

            assertThat(account.passwordHash()).isEqualTo(NEW_PASSWORD_HASH);
            assertThat(token.usedAt()).contains(openedAt);
        }

        @Test
        @DisplayName("이용 정지 중인 계정도 바꾼다 — 정지 중에도 로그인은 된다 (docs/01 \"관객 이용 정지\")")
        void suspendedAccountCanReset() {
            AudienceAccount active = AudienceAccountFixtures.activeAccount();
            active.suspend("번호를 바꿔가며 1인 매수 제한을 넘는 예매", SIGNUP_TIME.plus(Duration.ofHours(1)));
            AudienceAccount account = storedAudience(active);

            PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT).resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT);

            assertThat(account.passwordHash()).isEqualTo(NEW_PASSWORD_HASH);
        }

        @Test
        @DisplayName("한 번 쓴 링크로는 다시 바꿀 수 없다 — 1회용")
        void usedLinkCannotBeUsedAgain() {
            PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT);
            token.resetPassword(storedAudience(AudienceAccountFixtures.activeAccount()), NEW_PASSWORD_HASH,
                    ISSUED_AT.plusSeconds(60));
            AudienceAccount account = storedAudience(AudienceAccountFixtures.activeAccount());

            assertRejected(() -> token.resetPassword(account, new PasswordHash("{test}another"), ISSUED_AT.plusSeconds(120)),
                    AccountErrorCode.PASSWORD_RESET_LINK_ALREADY_USED);
            assertThat(account.passwordHash()).isEqualTo(AudienceAccountFixtures.PASSWORD_HASH);
            assertThat(token.usedAt()).contains(ISSUED_AT.plusSeconds(60));
        }

        @Test
        @DisplayName("쓴 링크를 유효 시간이 지난 뒤에 열어도 '이미 사용한 링크'로 알린다")
        void usedLinkReportsUsedEvenAfterExpiry() {
            PasswordResetToken token = PasswordResetToken.restore(TOKEN_VALUE, SEOYUN, ISSUED_AT,
                    ISSUED_AT.plus(Duration.ofHours(1)), ISSUED_AT.plusSeconds(60), null);

            assertRejected(() -> token.resetPassword(storedAudience(AudienceAccountFixtures.activeAccount()),
                    NEW_PASSWORD_HASH, ISSUED_AT.plus(Duration.ofHours(2))), AccountErrorCode.PASSWORD_RESET_LINK_ALREADY_USED);
        }

        @Test
        @DisplayName("새 링크로 대체된 링크로는 바꿀 수 없다 — 가장 최근 링크만 쓴다")
        void replacedLinkCannotBeUsed() {
            PasswordResetToken token = PasswordResetToken.restore(TOKEN_VALUE, SEOYUN, ISSUED_AT,
                    ISSUED_AT.plus(Duration.ofHours(1)), null, ISSUED_AT.plusSeconds(30));
            AudienceAccount account = storedAudience(AudienceAccountFixtures.activeAccount());

            assertRejected(() -> token.resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT.plusSeconds(60)),
                    AccountErrorCode.PASSWORD_RESET_LINK_REPLACED);
            assertThat(account.passwordHash()).isEqualTo(AudienceAccountFixtures.PASSWORD_HASH);
            assertThat(token.usedAt()).isEmpty();
        }

        @Test
        @DisplayName("1시간이 지나 열면 바꾸지 않는다 — 비밀번호도 링크도 그대로다")
        void expiredLinkCannotBeUsed() {
            PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT);
            AudienceAccount account = storedAudience(AudienceAccountFixtures.activeAccount());

            assertRejected(() -> token.resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT.plus(Duration.ofHours(1))),
                    AccountErrorCode.PASSWORD_RESET_LINK_EXPIRED);
            assertThat(account.passwordHash()).isEqualTo(AudienceAccountFixtures.PASSWORD_HASH);
            assertThat(token.usedAt()).isEmpty();
        }

        @Test
        @DisplayName("인증 대기 계정은 바꾸지 않고, 링크도 쓰지 않은 채로 둔다")
        void pendingAccountIsNotReset() {
            PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT);
            AudienceAccount account = storedAudience(AudienceAccountFixtures.newlySignedUpAccount());

            assertRejected(() -> token.resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT),
                    AccountErrorCode.AUDIENCE_NOT_ACTIVE);
            assertThat(token.usedAt()).isEmpty();
        }

        @Test
        @DisplayName("다른 관객 계정이나 운영진 로그인을 가리키는 링크로는 바꿀 수 없다")
        void rejectsOtherAccount() {
            AudienceAccount account = storedAudience(AudienceAccountFixtures.activeAccount());

            assertThatIllegalArgumentException().isThrownBy(() -> PasswordResetToken
                    .issue(TOKEN_VALUE, AccountRef.of(AccountKind.AUDIENCE, ACCOUNT_ID + 1), ISSUED_AT)
                    .resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT));
            assertThatIllegalArgumentException().isThrownBy(() -> PasswordResetToken.issue(TOKEN_VALUE, JUNGHOON, ISSUED_AT)
                    .resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT));
            assertThat(account.passwordHash()).isEqualTo(AudienceAccountFixtures.PASSWORD_HASH);
        }

        private static AudienceAccount storedAudience(AudienceAccount account) {
            return AudienceAccount.restore(ACCOUNT_ID, account.personalInformation(), account.passwordHash(),
                    account.signedUpAt(), account.consentRecords(), account.status(), account.emailVerifiedAt().orElse(null),
                    account.suspensions());
        }
    }

    @Nested
    @DisplayName("운영진 로그인의 비밀번호 바꾸기")
    class StaffReset {

        @Test
        @DisplayName("유효 시간 안에 열면 비밀번호가 새 해시로 바뀌고, 바꾼 시각이 쓴 일시로 남는다")
        void resetsPasswordWithinValidity() {
            StaffAccount account = StaffAccountFixtures.storedActiveAccount(ACCOUNT_ID);
            PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, JUNGHOON, ISSUED_AT);

            token.resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT.plusSeconds(60));

            assertThat(account.passwordHash()).isEqualTo(NEW_PASSWORD_HASH);
            assertThat(token.usedAt()).contains(ISSUED_AT.plusSeconds(60));
        }

        @Test
        @DisplayName("한 번 쓴 링크로는 다시 바꿀 수 없다 — 1회용")
        void usedLinkCannotBeUsedAgain() {
            PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, JUNGHOON, ISSUED_AT);
            token.resetPassword(StaffAccountFixtures.storedActiveAccount(ACCOUNT_ID), NEW_PASSWORD_HASH, ISSUED_AT);
            StaffAccount account = StaffAccountFixtures.storedActiveAccount(ACCOUNT_ID);

            assertRejected(() -> token.resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT.plusSeconds(60)),
                    AccountErrorCode.PASSWORD_RESET_LINK_ALREADY_USED);
            assertThat(account.passwordHash()).isEqualTo(StaffAccountFixtures.PASSWORD_HASH);
        }

        @Test
        @DisplayName("1시간이 지나 열면 바꾸지 않는다")
        void expiredLinkCannotBeUsed() {
            StaffAccount account = StaffAccountFixtures.storedActiveAccount(ACCOUNT_ID);

            assertRejected(() -> PasswordResetToken.issue(TOKEN_VALUE, JUNGHOON, ISSUED_AT)
                    .resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT.plus(Duration.ofHours(1))),
                    AccountErrorCode.PASSWORD_RESET_LINK_EXPIRED);
            assertThat(account.passwordHash()).isEqualTo(StaffAccountFixtures.PASSWORD_HASH);
        }

        @Test
        @DisplayName("탈퇴한 계정은 바꾸지 않는다")
        void withdrawnAccountIsNotReset() {
            PasswordResetToken token = PasswordResetToken.issue(TOKEN_VALUE, JUNGHOON, ISSUED_AT);

            assertRejected(() -> token.resetPassword(StaffAccountFixtures.storedWithdrawnAccount(ACCOUNT_ID),
                    NEW_PASSWORD_HASH, ISSUED_AT), AccountErrorCode.STAFF_WITHDRAWN);
            assertThat(token.usedAt()).isEmpty();
        }

        @Test
        @DisplayName("관객 계정을 가리키는 링크로는 같은 id의 운영진 로그인 비밀번호를 바꿀 수 없다")
        void rejectsAudienceLink() {
            StaffAccount account = StaffAccountFixtures.storedActiveAccount(ACCOUNT_ID);

            assertThatIllegalArgumentException().isThrownBy(() -> PasswordResetToken.issue(TOKEN_VALUE, SEOYUN, ISSUED_AT)
                    .resetPassword(account, NEW_PASSWORD_HASH, ISSUED_AT));
            assertThat(account.passwordHash()).isEqualTo(StaffAccountFixtures.PASSWORD_HASH);
        }
    }
}
