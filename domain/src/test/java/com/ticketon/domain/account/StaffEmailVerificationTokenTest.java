package com.ticketon.domain.account;

import static com.ticketon.domain.account.StaffAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.StaffAccountFixtures.assertRejected;
import static com.ticketon.domain.account.StaffAccountFixtures.storedActiveAccount;
import static com.ticketon.domain.account.StaffAccountFixtures.storedPendingAccount;
import static com.ticketon.domain.account.StaffAccountFixtures.storedWithdrawnAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("운영진 로그인 이메일 인증 링크")
class StaffEmailVerificationTokenTest {

    private static final long ACCOUNT_ID = 7;

    private static final String TOKEN_VALUE = "staffAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final Instant ISSUED_AT = SIGNUP_TIME;

    @Test
    @DisplayName("유효 시간은 관객과 같은 24시간이다 (확인 필요 #11) — 보낸 때부터 센다")
    void validForTwentyFourHoursFromIssue() {
        StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

        assertThat(ProposedPolicy.EMAIL_VERIFICATION_LINK_VALIDITY).isEqualTo(Duration.ofHours(24));
        assertThat(token.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(token.expiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofHours(24)));
        assertThat(token.staffAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(TOKEN_VALUE).hasSize(EmailVerificationToken.LENGTH);
    }

    @Test
    @DisplayName("24시간에서 1초 모자라면 아직 유효하고, 24시간이 되는 순간부터 지난 것이다")
    void expiresExactlyAtTwentyFourHours() {
        StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

        assertThat(token.isExpiredAt(ISSUED_AT.plus(Duration.ofHours(24)).minusSeconds(1))).isFalse();
        assertThat(token.isExpiredAt(ISSUED_AT.plus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    @DisplayName("문자열로 바꿔도 링크 값은 드러나지 않는다")
    void toStringHidesValue() {
        assertThat(StaffEmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT).toString())
                .doesNotContain(TOKEN_VALUE);
    }

    @Nested
    @DisplayName("인증")
    class Verify {

        @Test
        @DisplayName("유효 시간 안에 열면 인증 대기 계정이 활동 상태가 되고, 연 시각이 인증 일시로 남는다")
        void verifiesPendingAccountWithinValidity() {
            StaffAccount account = storedPendingAccount(ACCOUNT_ID);
            StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);
            Instant openedAt = ISSUED_AT.plus(Duration.ofHours(23));

            token.verify(account, openedAt);

            assertThat(account.status()).isEqualTo(StaffAccountStatus.ACTIVE);
            assertThat(account.emailVerifiedAt()).contains(openedAt);
        }

        @Test
        @DisplayName("24시간이 지나 열면 인증하지 않는다 — 계정은 인증 대기 그대로다")
        void rejectsExpiredLinkForPendingAccount() {
            StaffAccount account = storedPendingAccount(ACCOUNT_ID);
            StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

            assertRejected(() -> token.verify(account, ISSUED_AT.plus(Duration.ofHours(24))),
                    AccountErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED);
            assertThat(account.status()).isEqualTo(StaffAccountStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("이미 인증한 계정이 다시 열면 유효 시간이 지났어도 '이미 인증을 마친 계정'이다")
        void reopeningAfterVerificationSaysAlreadyVerified() {
            StaffAccount account = storedActiveAccount(ACCOUNT_ID);
            StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

            assertRejected(() -> token.verify(account, ISSUED_AT.plus(Duration.ofDays(3))),
                    AccountErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        @Test
        @DisplayName("탈퇴한 계정은 인증할 수 없다")
        void rejectsWithdrawnAccount() {
            StaffAccount account = storedWithdrawnAccount(ACCOUNT_ID);
            StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

            assertRejected(() -> token.verify(account, ISSUED_AT.plusSeconds(60)), AccountErrorCode.STAFF_WITHDRAWN);
        }

        @Test
        @DisplayName("다른 계정에는 쓸 수 없다")
        void rejectsOtherAccount() {
            StaffAccount otherAccount = storedPendingAccount(ACCOUNT_ID + 1);
            StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

            assertThatIllegalArgumentException().isThrownBy(() -> token.verify(otherAccount, ISSUED_AT.plusSeconds(60)));
        }
    }
}
