package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.AudienceAccountFixtures.adultPersonalInformation;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static com.ticketon.domain.account.AudienceAccountFixtures.koreanTime;
import static com.ticketon.domain.account.AudienceAccountFixtures.personalInformationBornOn;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("관객 카카오·구글 가입 규칙")
class AudienceAccountOAuthSignUpTest {

    private static final SignupConsentChoices REQUIRED_ONLY = SignupConsentChoices.agreeingToAllRequired(false);

    private static final PasswordHash NEW_PASSWORD_HASH = new PasswordHash("{test}first-password");

    @Test
    @DisplayName("비밀번호 없이 가입되고, 직접 가입처럼 인증 대기 상태에 네 동의 항목이 가입 일시로 남는다")
    void signsUpWithoutPassword() {
        AudienceAccount account = signUpWith(OAuthProvider.KAKAO, adultPersonalInformation(), REQUIRED_ONLY,
                EmailAvailability.AVAILABLE);

        assertThat(account.passwordHash()).isEmpty();
        assertThat(account.status()).isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
        assertThat(account.isEmailVerified()).isFalse();
        assertThat(account.signedUpAt()).isEqualTo(SIGNUP_TIME);
        assertThat(account.consentRecords()).hasSize(4)
                .allSatisfy(consentRecord -> assertThat(consentRecord.recordedAt()).isEqualTo(SIGNUP_TIME));
        assertThat(account.isMarketingAgreed()).isFalse();
    }

    @Test
    @DisplayName("제공자가 확인한 이메일이면 바로 인증을 마쳐 활동 상태가 된다 (#11 — signUp 뒤 바로 verifyEmail)")
    void providerVerifiedEmailVerifiesRightAway() {
        AudienceAccount account = signUpWith(OAuthProvider.GOOGLE, adultPersonalInformation(), REQUIRED_ONLY,
                EmailAvailability.AVAILABLE);

        account.verifyEmail(SIGNUP_TIME);

        assertThat(account.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
        assertThat(account.emailVerifiedAt()).contains(SIGNUP_TIME);
        assertThat(account.can(AudienceAction.LOG_IN)).isTrue();
    }

    @Test
    @DisplayName("비밀번호 없는 계정도 비밀번호 찾기로 비밀번호를 처음 정한다 (#16)")
    void setsFirstPasswordThroughReset() {
        AudienceAccount account = signUpWith(OAuthProvider.KAKAO, adultPersonalInformation(), REQUIRED_ONLY,
                EmailAvailability.AVAILABLE);
        account.verifyEmail(SIGNUP_TIME);

        account.resetPassword(NEW_PASSWORD_HASH);

        assertThat(account.passwordHash()).contains(NEW_PASSWORD_HASH);
    }

    @Nested
    @DisplayName("직접 가입과 같은 규칙 (docs/01 \"OAuth는 중복 계정을 막지 못합니다\")")
    class SameRulesAsDirectSignUp {

        private static final LocalDate BORN_2012_10_15 = LocalDate.of(2012, 10, 15);

        @Test
        @DisplayName("2012-10-15생은 만 14세 생일 전날(2026-10-14)에 가입할 수 없고, 생일 당일(2026-10-15)에는 가입된다")
        void minimumAge() {
            Instant dayBeforeBirthday = koreanTime(2026, 10, 14, 23, 59);
            Instant birthdayMidnight = koreanTime(2026, 10, 15, 0, 0);

            assertRejected(() -> signUpBornOn(BORN_2012_10_15, dayBeforeBirthday),
                    AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE);
            assertThat(signUpBornOn(BORN_2012_10_15, birthdayMidnight).status())
                    .isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
        }

        @ParameterizedTest(name = "{0} 누락")
        @EnumSource(value = ConsentItem.class, names = {"TERMS_OF_SERVICE", "PERSONAL_INFORMATION_COLLECTION",
                "MINIMUM_AGE_CONFIRMATION"})
        @DisplayName("필수 동의가 하나라도 빠지면 가입할 수 없다")
        void requiredConsents(ConsentItem missingItem) {
            Set<ConsentItem> agreedItems = EnumSet.allOf(ConsentItem.class);
            agreedItems.remove(missingItem);

            assertRejected(() -> signUpWith(OAuthProvider.GOOGLE, adultPersonalInformation(),
                    new SignupConsentChoices(agreedItems), EmailAvailability.AVAILABLE),
                    AccountErrorCode.SIGNUP_REQUIRED_CONSENT_MISSING);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = PhoneNumberAvailability.class, names = {"ALREADY_REGISTERED", "BLOCKED"})
        @DisplayName("가입된 번호·차단된 번호는 같은 문구로 막는다 — 제공자 계정이 셋이어도 번호가 하나면 계정은 하나")
        void phoneNumber(PhoneNumberAvailability phoneNumberAvailability) {
            assertRejected(() -> AudienceAccount.signUpThroughOAuth(OAuthProvider.KAKAO, adultPersonalInformation(),
                    REQUIRED_ONLY, phoneNumberAvailability, EmailAvailability.AVAILABLE, SIGNUP_TIME),
                    AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        }

        private AudienceAccount signUpBornOn(LocalDate birthDate, Instant now) {
            return AudienceAccount.signUpThroughOAuth(OAuthProvider.KAKAO, personalInformationBornOn(birthDate),
                    REQUIRED_ONLY, PhoneNumberAvailability.AVAILABLE, EmailAvailability.AVAILABLE, now);
        }
    }

    @Nested
    @DisplayName("같은 이메일의 관객 계정이 있으면 — 자동으로 묶지 않고 연동을 안내한다 (docs/01 \"계정 연동\")")
    class SameEmail {

        @Test
        @DisplayName("구글 — \"이 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인한 뒤 구글 계정을 연결해 주세요.\"")
        void google() {
            assertRejected(() -> signUpWith(OAuthProvider.GOOGLE, adultPersonalInformation(), REQUIRED_ONLY,
                    EmailAvailability.ALREADY_REGISTERED), AccountErrorCode.GOOGLE_SIGNUP_EMAIL_ALREADY_REGISTERED);
            assertThat(AccountErrorCode.GOOGLE_SIGNUP_EMAIL_ALREADY_REGISTERED.message())
                    .isEqualTo("이 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인한 뒤 구글 계정을 연결해 주세요.");
        }

        @Test
        @DisplayName("카카오 — 문구의 제공자 이름만 카카오로 바뀐다")
        void kakao() {
            assertRejected(() -> signUpWith(OAuthProvider.KAKAO, adultPersonalInformation(), REQUIRED_ONLY,
                    EmailAvailability.ALREADY_REGISTERED), AccountErrorCode.KAKAO_SIGNUP_EMAIL_ALREADY_REGISTERED);
            assertThat(AccountErrorCode.KAKAO_SIGNUP_EMAIL_ALREADY_REGISTERED.message())
                    .isEqualTo("이 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인한 뒤 카카오 계정을 연결해 주세요.");
        }

        @Test
        @DisplayName("규칙은 직접 가입과 같은 순서로 본다 — 번호와 이메일이 둘 다 걸리면 번호로 막는다")
        void phoneNumberIsCheckedBeforeEmail() {
            assertRejected(() -> AudienceAccount.signUpThroughOAuth(OAuthProvider.GOOGLE, adultPersonalInformation(),
                    REQUIRED_ONLY, PhoneNumberAvailability.ALREADY_REGISTERED, EmailAvailability.ALREADY_REGISTERED,
                    SIGNUP_TIME), AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        }
    }

    private static AudienceAccount signUpWith(OAuthProvider provider, AudiencePersonalInformation personalInformation,
            SignupConsentChoices consentChoices, EmailAvailability emailAvailability) {
        return AudienceAccount.signUpThroughOAuth(provider, personalInformation, consentChoices,
                PhoneNumberAvailability.AVAILABLE, emailAvailability, SIGNUP_TIME);
    }
}
