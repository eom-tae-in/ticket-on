package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.PASSWORD_HASH;
import static com.ticketon.domain.account.AudienceAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.AudienceAccountFixtures.adultPersonalInformation;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("이메일 인증 링크")
class EmailVerificationTokenTest {

    private static final long ACCOUNT_ID = 7;

    private static final String TOKEN_VALUE = "verifyAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final Instant ISSUED_AT = SIGNUP_TIME;

    @Test
    @DisplayName("유효 시간은 24시간이다 (확인 필요 #11) — 보낸 때부터 센다")
    void validForTwentyFourHoursFromIssue() {
        EmailVerificationToken token = EmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

        assertThat(ProposedPolicy.EMAIL_VERIFICATION_LINK_VALIDITY).isEqualTo(Duration.ofHours(24));
        assertThat(token.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(token.expiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofHours(24)));
        assertThat(token.audienceAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(token.value()).isEqualTo(TOKEN_VALUE);
    }

    @Test
    @DisplayName("링크 값은 URL 안전 문자 32자다 — 192비트")
    void tokenLengthIsThirtyTwo() {
        assertThat(EmailVerificationToken.LENGTH).isEqualTo(32);
        assertThat(TOKEN_VALUE).hasSize(EmailVerificationToken.LENGTH);
    }

    @Test
    @DisplayName("24시간에서 1초 모자라면 아직 유효하고, 24시간이 되는 순간부터 지난 것이다")
    void expiresExactlyAtTwentyFourHours() {
        EmailVerificationToken token = EmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

        assertThat(token.isExpiredAt(ISSUED_AT.plus(Duration.ofHours(24)).minusSeconds(1))).isFalse();
        assertThat(token.isExpiredAt(ISSUED_AT.plus(Duration.ofHours(24)))).isTrue();
    }

    @Nested
    @DisplayName("인증")
    class Verify {

        @Test
        @DisplayName("유효 시간 안에 열면 인증 대기 계정이 활동 상태가 되고, 연 시각이 인증 일시로 남는다")
        void verifiesPendingAccountWithinValidity() {
            AudienceAccount account = pendingAccount();
            EmailVerificationToken token = EmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);
            Instant openedAt = ISSUED_AT.plus(Duration.ofHours(23));

            token.verify(account, openedAt);

            assertThat(account.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
            assertThat(account.emailVerifiedAt()).contains(openedAt);
        }

        @Test
        @DisplayName("24시간이 지나 열면 인증하지 않는다 — 계정은 인증 대기 그대로다")
        void rejectsExpiredLinkForPendingAccount() {
            AudienceAccount account = pendingAccount();
            EmailVerificationToken token = EmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

            assertRejected(() -> token.verify(account, ISSUED_AT.plus(Duration.ofHours(24))),
                    AccountErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED);
            assertThat(account.status()).isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
            assertThat(AccountErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED.message()).isEqualTo("인증 링크의 유효 시간이 지났습니다.");
        }

        @Test
        @DisplayName("이미 인증한 계정이 링크를 다시 열면 유효 시간 안이든 밖이든 '이미 인증' 문구다")
        void alreadyVerifiedWinsOverExpiry() {
            EmailVerificationToken token = EmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT);

            assertRejected(() -> token.verify(activeAccount(), ISSUED_AT.plus(Duration.ofHours(1))),
                    AccountErrorCode.EMAIL_ALREADY_VERIFIED);
            assertRejected(() -> token.verify(activeAccount(), ISSUED_AT.plus(Duration.ofDays(3))),
                    AccountErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        @Test
        @DisplayName("다른 계정의 링크로는 인증하지 않는다")
        void rejectsOtherAccount() {
            EmailVerificationToken otherAccountToken = EmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID + 1, ISSUED_AT);

            assertThatThrownBy(() -> otherAccountToken.verify(pendingAccount(), ISSUED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private AudienceAccount pendingAccount() {
            return AudienceAccount.restore(ACCOUNT_ID, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                    SignupConsentChoices.agreeingToAllRequired(false).recordsAt(SIGNUP_TIME),
                    AudienceAccountStatus.PENDING_VERIFICATION, null, List.of());
        }

        private AudienceAccount activeAccount() {
            return AudienceAccount.restore(ACCOUNT_ID, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                    SignupConsentChoices.agreeingToAllRequired(false).recordsAt(SIGNUP_TIME),
                    AudienceAccountStatus.ACTIVE, SIGNUP_TIME.plusSeconds(60), List.of());
        }
    }

    @Test
    @DisplayName("값이 비었거나, 계정 id가 0 이하이거나, 끝나는 일시가 보낸 일시보다 늦지 않으면 거부한다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new EmailVerificationToken(" ", ACCOUNT_ID, ISSUED_AT, ISSUED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailVerificationToken(TOKEN_VALUE, 0, ISSUED_AT, ISSUED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailVerificationToken(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT, ISSUED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("문자열로 바꿔도 링크 값은 드러나지 않는다")
    void toStringHidesValue() {
        assertThat(EmailVerificationToken.issue(TOKEN_VALUE, ACCOUNT_ID, ISSUED_AT).toString()).doesNotContain(TOKEN_VALUE);
    }
}
