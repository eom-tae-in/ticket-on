package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.PASSWORD_HASH;
import static com.ticketon.domain.account.AudienceAccountFixtures.adultPersonalInformation;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static com.ticketon.domain.account.AudienceAccountFixtures.koreanTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("마케팅 수신 동의 변경 — 언제든 철회, 동의 기록은 새 줄로 (docs/01 \"약관과 동의\", docs/12 \"마케팅 수신은 구분해서 표시\")")
class AudienceAccountMarketingConsentChangeTest {

    private static final Instant SIGNED_UP_AT = koreanTime(2026, 2, 20, 10, 0);

    /** docs/12 "알림 설정" 예시의 동의 일시 — "2026-03-01 동의 · 언제든 철회할 수 있습니다". */
    private static final Instant AGREED_AT = koreanTime(2026, 3, 1, 9, 30);

    private static final Instant WITHDRAWN_AT = koreanTime(2026, 4, 10, 21, 0);

    @Test
    @DisplayName("가입 때 동의하지 않은 관객이 동의하면 새 줄이 더해지고, 동의 일시는 2026-03-01이다 (docs/12 예시)")
    void agreeAddsRecord() {
        AudienceAccount account = activeAccountSignedUpWith(false);

        assertThat(account.changeMarketingConsent(true, AGREED_AT))
                .contains(new ConsentRecord(ConsentItem.MARKETING, true, AGREED_AT));

        assertThat(account.isMarketingAgreed()).isTrue();
        assertThat(account.marketingAgreedAt()).contains(AGREED_AT);
        assertThat(KoreanTime.toDate(account.marketingAgreedAt().orElseThrow())).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(account.consentRecords()).hasSize(5).endsWith(new ConsentRecord(ConsentItem.MARKETING, true, AGREED_AT));
    }

    @Test
    @DisplayName("철회하면 이전 기록을 고치지 않고 철회 줄을 더한다 — 가입 때와 동의한 날의 기록이 그대로 남는다")
    void withdrawKeepsEarlierRecords() {
        AudienceAccount account = activeAccountSignedUpWith(false);
        account.changeMarketingConsent(true, AGREED_AT);

        assertThat(account.changeMarketingConsent(false, WITHDRAWN_AT))
                .contains(new ConsentRecord(ConsentItem.MARKETING, false, WITHDRAWN_AT));

        assertThat(account.isMarketingAgreed()).isFalse();
        assertThat(account.marketingAgreedAt()).isEmpty();
        assertThat(account.consentRecords()).containsExactly(
                new ConsentRecord(ConsentItem.TERMS_OF_SERVICE, true, SIGNED_UP_AT),
                new ConsentRecord(ConsentItem.PERSONAL_INFORMATION_COLLECTION, true, SIGNED_UP_AT),
                new ConsentRecord(ConsentItem.MINIMUM_AGE_CONFIRMATION, true, SIGNED_UP_AT),
                new ConsentRecord(ConsentItem.MARKETING, false, SIGNED_UP_AT),
                new ConsentRecord(ConsentItem.MARKETING, true, AGREED_AT),
                new ConsentRecord(ConsentItem.MARKETING, false, WITHDRAWN_AT));
    }

    @Test
    @DisplayName("철회한 뒤 다시 동의할 수 있고, 동의 일시는 다시 동의한 때다")
    void agreeAgainAfterWithdrawal() {
        AudienceAccount account = activeAccountSignedUpWith(true);
        account.changeMarketingConsent(false, WITHDRAWN_AT);
        Instant agreedAgainAt = koreanTime(2026, 5, 2, 8, 0);

        account.changeMarketingConsent(true, agreedAgainAt);

        assertThat(account.isMarketingAgreed()).isTrue();
        assertThat(account.marketingAgreedAt()).contains(agreedAgainAt);
    }

    @Test
    @DisplayName("가입 때 동의했으면 동의 일시는 가입 일시다")
    void agreedAtSignup() {
        assertThat(activeAccountSignedUpWith(true).marketingAgreedAt()).contains(SIGNED_UP_AT);
        assertThat(activeAccountSignedUpWith(false).marketingAgreedAt()).isEmpty();
    }

    @ParameterizedTest(name = "가입 때 동의 {0} → {0}로 바꾸기")
    @ValueSource(booleans = {true, false})
    @DisplayName("지금 상태와 같으면 아무것도 더하지 않는다 — 이미 동의한 사람의 동의 일시가 뒤로 밀리지 않는다")
    void sameStateAddsNothing(boolean marketingAgreed) {
        AudienceAccount account = activeAccountSignedUpWith(marketingAgreed);

        assertThat(account.changeMarketingConsent(marketingAgreed, AGREED_AT)).isEmpty();

        assertThat(account.consentRecords()).hasSize(4);
        assertThat(account.isMarketingAgreed()).isEqualTo(marketingAgreed);
        if (marketingAgreed) {
            assertThat(account.marketingAgreedAt()).contains(SIGNED_UP_AT);
        }
    }

    @Test
    @DisplayName("가입과 같은 일시에 바꿔도 나중에 더한 줄이 지금 상태다")
    void sameInstantAsLatestRecord() {
        AudienceAccount account = activeAccountSignedUpWith(false);

        account.changeMarketingConsent(true, SIGNED_UP_AT);

        assertThat(account.isMarketingAgreed()).isTrue();
    }

    @Test
    @DisplayName("가장 최근 기록보다 앞선 일시로는 바꾸지 않는다 — 그 기록이 계속 지금 상태로 남게 된다")
    void rejectsTimeBeforeLatestRecord() {
        AudienceAccount account = activeAccountSignedUpWith(false);
        account.changeMarketingConsent(true, AGREED_AT);

        assertThatThrownBy(() -> account.changeMarketingConsent(false, AGREED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account.isMarketingAgreed()).isTrue();
        assertThat(account.consentRecords()).hasSize(5);
    }

    @Test
    @DisplayName("이용 정지 중에도 철회할 수 있다 — 동의는 언제든 철회할 수 있어야 한다")
    void suspendedAccountCanWithdraw() {
        AudienceAccount account = activeAccountSignedUpWith(true);
        account.suspend("반복되는 허위 신고", AGREED_AT);

        account.changeMarketingConsent(false, WITHDRAWN_AT);

        assertThat(account.isMarketingAgreed()).isFalse();
    }

    @Test
    @DisplayName("인증 대기·탈퇴 계정은 바꿀 수 없다 — 로그인할 수 없는 상태다")
    void pendingOrWithdrawnAccountCannotChange() {
        AudienceAccount pending = AudienceAccount.signUp(adultPersonalInformation(), PASSWORD_HASH,
                SignupConsentChoices.agreeingToAllRequired(false), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNED_UP_AT);
        AudienceAccount withdrawn = AudienceAccount.restore(7, adultPersonalInformation(), PASSWORD_HASH, SIGNED_UP_AT,
                SignupConsentChoices.agreeingToAllRequired(true).recordsAt(SIGNED_UP_AT), AudienceAccountStatus.WITHDRAWN,
                SIGNED_UP_AT.plusSeconds(300), List.of());

        assertRejected(() -> pending.changeMarketingConsent(true, AGREED_AT), AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        assertRejected(() -> withdrawn.changeMarketingConsent(false, AGREED_AT), AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        assertThat(pending.consentRecords()).hasSize(4);
    }

    /** 2026-02-20에 가입하고 이메일 인증까지 마친 계정(활동). */
    private static AudienceAccount activeAccountSignedUpWith(boolean marketingAgreed) {
        AudienceAccount account = AudienceAccount.signUp(adultPersonalInformation(), PASSWORD_HASH,
                SignupConsentChoices.agreeingToAllRequired(marketingAgreed), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNED_UP_AT);
        account.verifyEmail(SIGNED_UP_AT.plusSeconds(300));
        return account;
    }
}
