package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.ADULT_BIRTH_DATE;
import static com.ticketon.domain.account.AudienceAccountFixtures.PASSWORD_HASH;
import static com.ticketon.domain.account.AudienceAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.AudienceAccountFixtures.adultPersonalInformation;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static com.ticketon.domain.account.AudienceAccountFixtures.koreanTime;
import static com.ticketon.domain.account.AudienceAccountFixtures.newlySignedUpAccount;
import static com.ticketon.domain.account.AudienceAccountFixtures.personalInformationBornOn;
import static com.ticketon.domain.account.ConsentItem.MARKETING;
import static com.ticketon.domain.account.ConsentItem.MINIMUM_AGE_CONFIRMATION;
import static com.ticketon.domain.account.ConsentItem.PERSONAL_INFORMATION_COLLECTION;
import static com.ticketon.domain.account.ConsentItem.TERMS_OF_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("관객 가입 규칙")
class AudienceAccountSignUpTest {

    private static final SignupConsentChoices REQUIRED_ONLY = SignupConsentChoices.agreeingToAllRequired(false);

    @Test
    @DisplayName("가입하면 인증 대기 상태이고, 이메일 인증 전이며, 아직 id가 없다")
    void newAccountIsPendingVerification() {
        AudienceAccount account = newlySignedUpAccount();

        assertThat(account.status()).isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
        assertThat(account.isEmailVerified()).isFalse();
        assertThat(account.emailVerifiedAt()).isEmpty();
        assertThat(account.id()).isEmpty();
        assertThat(account.signedUpAt()).isEqualTo(SIGNUP_TIME);
        assertThat(account.personalInformation()).isEqualTo(adultPersonalInformation());
        assertThat(account.passwordHash()).contains(PASSWORD_HASH);
    }

    @Nested
    @DisplayName("만 14세 미만 차단 — 가입일(한국 날짜)의 만 나이")
    class MinimumAge {

        private static final LocalDate BORN_2012_10_15 = LocalDate.of(2012, 10, 15);

        @Test
        @DisplayName("2012-10-15생은 만 14세 생일 전날(2026-10-14)에 가입할 수 없다")
        void rejectedOnDayBeforeFourteenthBirthday() {
            Instant dayBeforeBirthday = koreanTime(2026, 10, 14, 23, 59);

            assertRejected(() -> signUpBornOn(BORN_2012_10_15, dayBeforeBirthday),
                    AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE);
        }

        @Test
        @DisplayName("2012-10-15생은 만 14세 생일 당일(2026-10-15) 0시부터 가입할 수 있다")
        void allowedOnFourteenthBirthday() {
            Instant birthdayMidnight = koreanTime(2026, 10, 15, 0, 0);

            assertThat(signUpBornOn(BORN_2012_10_15, birthdayMidnight).status())
                    .isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("날짜는 한국 시각으로 센다 — UTC로는 아직 10-14인 한국 10-15 0시 30분에도 가입된다")
        void countsAgeInKoreanDate() {
            Instant koreanBirthdayUtcDayBefore = Instant.parse("2026-10-14T15:30:00Z");

            assertThat(signUpBornOn(BORN_2012_10_15, koreanBirthdayUtcDayBefore).status())
                    .isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("2012-02-29생은 평년 2026-02-28에는 만 13세라 막히고, 03-01에 가입된다")
        void leapDayBirth() {
            LocalDate bornOnLeapDay = LocalDate.of(2012, 2, 29);

            assertRejected(() -> signUpBornOn(bornOnLeapDay, koreanTime(2026, 2, 28, 12, 0)),
                    AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE);
            assertThat(signUpBornOn(bornOnLeapDay, koreanTime(2026, 3, 1, 12, 0)).status())
                    .isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("'만 14세 이상입니다'에 체크해도 생년월일이 만 14세 미만이면 막는다 (docs/99 A-3)")
        void birthDateWinsOverAgeConfirmation() {
            assertThat(REQUIRED_ONLY.agreesTo(MINIMUM_AGE_CONFIRMATION)).isTrue();

            assertRejected(() -> signUpBornOn(LocalDate.of(2013, 1, 1), SIGNUP_TIME),
                    AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE);
        }

        @Test
        @DisplayName("문구는 '만 14세 미만은 가입할 수 없습니다.'다")
        void underAgeMessage() {
            assertThat(AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE.message()).isEqualTo("만 14세 미만은 가입할 수 없습니다.");
        }

        @Test
        @DisplayName("생년월일이 가입일보다 늦으면 거부한다")
        void rejectsBirthDateAfterSignupDate() {
            assertThatThrownBy(() -> signUpBornOn(LocalDate.of(2026, 10, 16), SIGNUP_TIME))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private AudienceAccount signUpBornOn(LocalDate birthDate, Instant now) {
            return AudienceAccount.signUp(personalInformationBornOn(birthDate), PASSWORD_HASH, REQUIRED_ONLY,
                    PhoneNumberAvailability.AVAILABLE, EmailAvailability.AVAILABLE, now);
        }
    }

    @Nested
    @DisplayName("동의")
    class Consents {

        @ParameterizedTest(name = "{0} 누락")
        @EnumSource(value = ConsentItem.class, names = {"TERMS_OF_SERVICE", "PERSONAL_INFORMATION_COLLECTION",
                "MINIMUM_AGE_CONFIRMATION"})
        @DisplayName("필수 동의가 하나라도 빠지면 가입할 수 없다")
        void rejectsWhenRequiredConsentMissing(ConsentItem missingItem) {
            Set<ConsentItem> agreedItems = EnumSet.allOf(ConsentItem.class);
            agreedItems.remove(missingItem);

            assertRejected(() -> signUpWith(new SignupConsentChoices(agreedItems)),
                    AccountErrorCode.SIGNUP_REQUIRED_CONSENT_MISSING);
        }

        @Test
        @DisplayName("마케팅 수신만 동의하면 가입할 수 없다")
        void marketingAloneIsNotEnough() {
            assertRejected(() -> signUpWith(SignupConsentChoices.agreeingTo(MARKETING)),
                    AccountErrorCode.SIGNUP_REQUIRED_CONSENT_MISSING);
        }

        @Test
        @DisplayName("마케팅 수신에 동의하지 않아도 가입된다 — '동의하지 않음'과 그 일시가 남는다")
        void signsUpWithoutMarketing() {
            AudienceAccount account = signUpWith(REQUIRED_ONLY);

            assertThat(account.isMarketingAgreed()).isFalse();
            assertThat(account.latestConsent(MARKETING)).contains(new ConsentRecord(MARKETING, false, SIGNUP_TIME));
        }

        @Test
        @DisplayName("마케팅 수신에 따로 동의하면 동의 상태로 가입된다")
        void signsUpWithMarketing() {
            AudienceAccount account = signUpWith(SignupConsentChoices.agreeingToAllRequired(true));

            assertThat(account.isMarketingAgreed()).isTrue();
        }

        @Test
        @DisplayName("네 항목 모두 동의 여부와 가입 일시가 기록된다")
        void recordsEveryConsentAtSignupTime() {
            AudienceAccount account = signUpWith(REQUIRED_ONLY);

            assertThat(account.consentRecords()).containsExactly(
                    new ConsentRecord(TERMS_OF_SERVICE, true, SIGNUP_TIME),
                    new ConsentRecord(PERSONAL_INFORMATION_COLLECTION, true, SIGNUP_TIME),
                    new ConsentRecord(MINIMUM_AGE_CONFIRMATION, true, SIGNUP_TIME),
                    new ConsentRecord(MARKETING, false, SIGNUP_TIME));
        }

        @Test
        @DisplayName("한 항목의 지금 상태는 가장 최근 기록이다 — 이전 기록은 지우지 않는다")
        void latestConsentIsMostRecentRecord() {
            Instant agreedLater = SIGNUP_TIME.plusSeconds(86_400);
            AudienceAccount account = AudienceAccount.restore(7, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                    List.of(new ConsentRecord(MARKETING, true, agreedLater), new ConsentRecord(MARKETING, false, SIGNUP_TIME)),
                    AudienceAccountStatus.ACTIVE, SIGNUP_TIME, List.of());

            assertThat(account.latestConsent(MARKETING)).contains(new ConsentRecord(MARKETING, true, agreedLater));
            assertThat(account.isMarketingAgreed()).isTrue();
            assertThat(account.consentRecords()).hasSize(2);
        }

        private AudienceAccount signUpWith(SignupConsentChoices consentChoices) {
            return AudienceAccount.signUp(adultPersonalInformation(), PASSWORD_HASH, consentChoices,
                    PhoneNumberAvailability.AVAILABLE, EmailAvailability.AVAILABLE, SIGNUP_TIME);
        }
    }

    @Nested
    @DisplayName("전화번호 하나에 계정 하나")
    class OneAccountPerPhoneNumber {

        @Test
        @DisplayName("같은 번호로 가입한 계정이 있으면 가입할 수 없다 — 문구는 docs/01 그대로다")
        void rejectsAlreadyRegisteredNumber() {
            assertRejected(() -> signUpWhenNumberIs(PhoneNumberAvailability.ALREADY_REGISTERED),
                    AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
            assertThat(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED.message())
                    .isEqualTo("이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요.");
        }

        @Test
        @DisplayName("이용 정지 후 탈퇴로 차단된 번호도 같은 코드·문구로 막는다 — 정지 이력이 드러나지 않게")
        void rejectsBlockedNumberWithSameCode() {
            assertRejected(() -> signUpWhenNumberIs(PhoneNumberAvailability.BLOCKED),
                    AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("아무도 쓰지 않는 번호면 가입된다")
        void allowsAvailableNumber() {
            assertThat(signUpWhenNumberIs(PhoneNumberAvailability.AVAILABLE).personalInformation().phoneNumber())
                    .isEqualTo(PhoneNumber.of("010-1234-5678"));
        }

        private AudienceAccount signUpWhenNumberIs(PhoneNumberAvailability availability) {
            return AudienceAccount.signUp(adultPersonalInformation(), PASSWORD_HASH, REQUIRED_ONLY, availability,
                    EmailAvailability.AVAILABLE, SIGNUP_TIME);
        }
    }

    @Nested
    @DisplayName("이메일 하나에 관객 계정 하나")
    class OneAccountPerEmail {

        @Test
        @DisplayName("같은 이메일의 관객 계정이 있으면 가입할 수 없다")
        void rejectsAlreadyRegisteredEmail() {
            assertRejected(() -> signUpWhenEmailIs(EmailAvailability.ALREADY_REGISTERED),
                    AccountErrorCode.AUDIENCE_EMAIL_ALREADY_REGISTERED);
            assertThat(AccountErrorCode.AUDIENCE_EMAIL_ALREADY_REGISTERED.message()).isEqualTo("이미 가입된 이메일입니다.");
        }

        @Test
        @DisplayName("번호와 이메일이 둘 다 가입돼 있으면 번호 문구로 막는다 — 번호를 먼저 본다")
        void phoneNumberIsCheckedBeforeEmail() {
            assertRejected(() -> AudienceAccount.signUp(adultPersonalInformation(), PASSWORD_HASH, REQUIRED_ONLY,
                    PhoneNumberAvailability.ALREADY_REGISTERED, EmailAvailability.ALREADY_REGISTERED, SIGNUP_TIME),
                    AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("만 14세 미만이면 이메일이 가입돼 있어도 나이 문구로 막는다 — 나이를 먼저 본다")
        void ageIsCheckedBeforeEmail() {
            assertRejected(() -> AudienceAccount.signUp(personalInformationBornOn(LocalDate.of(2013, 1, 1)), PASSWORD_HASH,
                    REQUIRED_ONLY, PhoneNumberAvailability.AVAILABLE, EmailAvailability.ALREADY_REGISTERED, SIGNUP_TIME),
                    AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE);
        }

        private AudienceAccount signUpWhenEmailIs(EmailAvailability availability) {
            return AudienceAccount.signUp(adultPersonalInformation(), PASSWORD_HASH, REQUIRED_ONLY,
                    PhoneNumberAvailability.AVAILABLE, availability, SIGNUP_TIME);
        }
    }

    @Nested
    @DisplayName("이메일 인증")
    class EmailVerification {

        @Test
        @DisplayName("인증하면 활동 상태가 되고 인증 일시가 남는다")
        void verifyingActivatesAccount() {
            AudienceAccount account = newlySignedUpAccount();
            Instant verifiedAt = SIGNUP_TIME.plusSeconds(600);

            account.verifyEmail(verifiedAt);

            assertThat(account.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
            assertThat(account.isEmailVerified()).isTrue();
            assertThat(account.emailVerifiedAt()).contains(verifiedAt);
        }

        @Test
        @DisplayName("이미 인증한 계정은 다시 인증하지 않는다")
        void cannotVerifyTwice() {
            AudienceAccount account = newlySignedUpAccount();
            account.verifyEmail(SIGNUP_TIME.plusSeconds(600));

            assertRejected(() -> account.verifyEmail(SIGNUP_TIME.plusSeconds(1200)), AccountErrorCode.EMAIL_ALREADY_VERIFIED);
            assertThat(account.emailVerifiedAt()).contains(SIGNUP_TIME.plusSeconds(600));
        }

        @Test
        @DisplayName("탈퇴한 계정은 인증할 수 없다")
        void withdrawnAccountCannotVerify() {
            AudienceAccount account = AudienceAccount.restore(7, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                    REQUIRED_ONLY.recordsAt(SIGNUP_TIME), AudienceAccountStatus.WITHDRAWN, null, List.of());

            assertRejected(() -> account.verifyEmail(SIGNUP_TIME), AccountErrorCode.AUDIENCE_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("저장소에서 되살리기")
    class Restore {

        @Test
        @DisplayName("저장된 값 그대로 되살린다")
        void restoresSavedValues() {
            AudienceAccount account = AudienceAccount.restore(7, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                    REQUIRED_ONLY.recordsAt(SIGNUP_TIME), AudienceAccountStatus.ACTIVE, SIGNUP_TIME.plusSeconds(60),
                    List.of());

            assertThat(account.id()).hasValue(7);
            assertThat(account.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
            assertThat(account.emailVerifiedAt()).contains(SIGNUP_TIME.plusSeconds(60));
            assertThat(account.personalInformation().birthDate()).isEqualTo(ADULT_BIRTH_DATE);
            assertThat(account.passwordHash()).contains(PASSWORD_HASH);
        }

        @Test
        @DisplayName("비밀번호 해시가 없는 계정(카카오·구글로 가입하고 비밀번호를 정하지 않음)도 비밀번호 없이 되살린다 (#17)")
        void restoresAccountWithoutPassword() {
            AudienceAccount account = AudienceAccount.restore(7, adultPersonalInformation(), null, SIGNUP_TIME,
                    REQUIRED_ONLY.recordsAt(SIGNUP_TIME), AudienceAccountStatus.ACTIVE, SIGNUP_TIME, List.of());

            assertThat(account.passwordHash()).isEmpty();
        }

        @Test
        @DisplayName("상태와 이메일 인증 여부가 맞지 않으면 거부한다")
        void rejectsStatusVerificationMismatch() {
            List<ConsentRecord> records = REQUIRED_ONLY.recordsAt(SIGNUP_TIME);

            assertThatThrownBy(() -> AudienceAccount.restore(7, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                    records, AudienceAccountStatus.PENDING_VERIFICATION, SIGNUP_TIME, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AudienceAccount.restore(7, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                    records, AudienceAccountStatus.ACTIVE, null, List.of())).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("id는 1 이상이다")
        void idMustBePositive() {
            assertThatThrownBy(() -> AudienceAccount.restore(0, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                    REQUIRED_ONLY.recordsAt(SIGNUP_TIME), AudienceAccountStatus.ACTIVE, SIGNUP_TIME, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("가입 정보·비밀번호 해시·동의·번호 확인·이메일 확인·일시가 없으면 거부한다")
    void rejectsMissingArguments() {
        AudiencePersonalInformation information = adultPersonalInformation();
        PhoneNumberAvailability phone = PhoneNumberAvailability.AVAILABLE;
        EmailAvailability email = EmailAvailability.AVAILABLE;

        assertThatThrownBy(() -> AudienceAccount.signUp(null, PASSWORD_HASH, REQUIRED_ONLY, phone, email, SIGNUP_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AudienceAccount.signUp(information, null, REQUIRED_ONLY, phone, email, SIGNUP_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AudienceAccount.signUp(information, PASSWORD_HASH, null, phone, email, SIGNUP_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AudienceAccount.signUp(information, PASSWORD_HASH, REQUIRED_ONLY, null, email, SIGNUP_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AudienceAccount.signUp(information, PASSWORD_HASH, REQUIRED_ONLY, phone, null, SIGNUP_TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AudienceAccount.signUp(information, PASSWORD_HASH, REQUIRED_ONLY, phone, email, null))
                .isInstanceOf(NullPointerException.class);
    }
}
