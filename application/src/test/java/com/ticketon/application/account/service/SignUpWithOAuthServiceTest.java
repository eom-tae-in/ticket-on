package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.account.port.in.SignUpWithOAuthCommand;
import com.ticketon.application.account.port.in.SignUpWithOAuthResult;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.ConsentItem;
import com.ticketon.domain.account.ConsentRecord;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.EmailMessage;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("관객 카카오·구글 가입 유스케이스")
class SignUpWithOAuthServiceTest {

    private static final LocalDateTime SIGNUP_TIME = LocalDateTime.of(2026, 10, 15, 10, 0);

    private static final Instant SIGNUP_INSTANT = KoreanTime.toInstant(SIGNUP_TIME);

    private static final String TOKEN_VALUE = "verifyAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String SEOYUN_EMAIL = "Seoyun@Example.com";

    private static final String SEOYUN_PHONE = "010-1234-5678";

    private static final OAuthIdentity SEOYUN_GOOGLE = new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210");

    private static final OAuthIdentity SEOYUN_KAKAO = new OAuthIdentity(OAuthProvider.KAKAO, "3141592653");

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final FakeAudienceOAuthLinkRepository linkRepository = new FakeAudienceOAuthLinkRepository();
    private final FakeEmailVerificationTokenRepository tokenRepository = new FakeEmailVerificationTokenRepository();
    private final RecordingEmailSendPort emailSendPort = new RecordingEmailSendPort();
    private final RecordingSendNotificationUseCase sendNotificationUseCase = new RecordingSendNotificationUseCase();
    private final StoredAudienceAccounts accounts = new StoredAudienceAccounts(accountRepository);

    private final SignUpWithOAuthService service = new SignUpWithOAuthService(accountRepository, linkRepository,
            tokenRepository, emailSendPort, sendNotificationUseCase,
            new FixedTokenGenerator(TOKEN_VALUE, TOKEN_VALUE.replace('A', 'B')), WebAddress.of("https://ticket-on.example"),
            MutableClock.at(SIGNUP_TIME));

    @Nested
    @DisplayName("제공자가 확인한 이메일 — 이메일 인증을 거친 것으로 본다")
    class ProviderVerifiedEmail {

        @Test
        @DisplayName("비밀번호 없는 활동 계정이 저장되고 제공자 계정이 연결되며, 바로 로그인할 주체를 돌려받는다")
        void savesActiveAccountAndLink() {
            SignUpWithOAuthResult result = service.signUp(command(SEOYUN_GOOGLE, SEOYUN_EMAIL, true, false));

            AudienceAccount saved = accountRepository.findById(result.audienceAccountId()).orElseThrow();
            assertThat(saved.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
            assertThat(saved.emailVerifiedAt()).contains(SIGNUP_INSTANT);
            assertThat(saved.passwordHash()).isEmpty();
            assertThat(saved.personalInformation().phoneNumber()).isEqualTo(PhoneNumber.of("01012345678"));
            assertThat(linkRepository.savedLinks())
                    .containsExactly(new AudienceOAuthLink(result.audienceAccountId(), SEOYUN_GOOGLE, SIGNUP_INSTANT));
            assertThat(result.emailVerified()).isTrue();
            assertThat(result.email()).isEqualTo(SEOYUN_EMAIL);
            assertThat(result.accountToLogIn()).contains(AccountRef.of(AccountKind.AUDIENCE, result.audienceAccountId()));
        }

        @Test
        @DisplayName("인증 메일은 보내지 않고, 직접 가입이 인증을 마칠 때처럼 '회원가입 완료' 알림을 보낸다 (docs/12)")
        void sendsSignUpCompletedNotice() {
            SignUpWithOAuthResult result = service.signUp(command(SEOYUN_GOOGLE, SEOYUN_EMAIL, true, false));

            assertThat(emailSendPort.sentMessages()).isEmpty();
            assertThat(tokenRepository.savedTokens()).isEmpty();
            assertThat(sendNotificationUseCase.sentCommands()).singleElement().satisfies(sent -> {
                assertThat(sent.recipient()).isEqualTo(NotificationRecipient.audience(result.audienceAccountId()));
                assertThat(sent.kind()).isEqualTo(NotificationKind.SIGNUP_COMPLETED);
            });
        }

        @Test
        @DisplayName("동의 항목 넷이 가입 일시로 남고, 마케팅 수신은 따로 고른 대로다")
        void recordsConsents() {
            SignUpWithOAuthResult result = service.signUp(command(SEOYUN_KAKAO, SEOYUN_EMAIL, true, true));

            assertThat(accountRepository.findById(result.audienceAccountId()).orElseThrow().consentRecords())
                    .containsExactly(
                            new ConsentRecord(ConsentItem.TERMS_OF_SERVICE, true, SIGNUP_INSTANT),
                            new ConsentRecord(ConsentItem.PERSONAL_INFORMATION_COLLECTION, true, SIGNUP_INSTANT),
                            new ConsentRecord(ConsentItem.MINIMUM_AGE_CONFIRMATION, true, SIGNUP_INSTANT),
                            new ConsentRecord(ConsentItem.MARKETING, true, SIGNUP_INSTANT));
        }
    }

    @Nested
    @DisplayName("제공자가 이메일을 주지 않았거나 확인되지 않은 이메일 — 직접 가입처럼 인증 메일로 인증한다")
    class EmailEnteredByAudience {

        @Test
        @DisplayName("인증 대기 계정이 저장되고 연결되며, 로그인할 주체는 없다 — 인증을 마쳐야 로그인된다")
        void savesPendingAccount() {
            SignUpWithOAuthResult result = service.signUp(command(SEOYUN_KAKAO, SEOYUN_EMAIL, false, false));

            AudienceAccount saved = accountRepository.findById(result.audienceAccountId()).orElseThrow();
            assertThat(saved.status()).isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
            assertThat(saved.passwordHash()).isEmpty();
            assertThat(linkRepository.findByIdentity(SEOYUN_KAKAO)).isPresent();
            assertThat(result.emailVerified()).isFalse();
            assertThat(result.accountToLogIn()).isEmpty();
            assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
        }

        @Test
        @DisplayName("적은 이메일로 관객 화면의 인증 페이지 링크가 든 메일을 보낸다")
        void sendsVerificationMail() {
            SignUpWithOAuthResult result = service.signUp(command(SEOYUN_KAKAO, SEOYUN_EMAIL, false, false));

            assertThat(tokenRepository.savedTokens()).singleElement()
                    .satisfies(token -> assertThat(token.audienceAccountId()).isEqualTo(result.audienceAccountId()));
            EmailMessage message = emailSendPort.sentMessages().getFirst();
            assertThat(emailSendPort.sentMessages()).hasSize(1);
            assertThat(message.recipientAddress()).isEqualTo(SEOYUN_EMAIL);
            assertThat(message.body()).contains("https://ticket-on.example/verify-email?token=" + TOKEN_VALUE);
        }

        @Test
        @DisplayName("메일을 보내지 못하면 예외로 가입을 되돌린다 — 인증 대기 계정이 번호와 제공자 계정을 쥔 채 남지 않게")
        void failsWhenMailCannotBeSent() {
            emailSendPort.failWith("메일 서버 응답 없음");

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.signUp(command(SEOYUN_KAKAO, SEOYUN_EMAIL, false, false)))
                    .withMessageContaining("메일 서버 응답 없음");
        }
    }

    @Nested
    @DisplayName("가입을 막는 경우 — 직접 가입과 같은 규칙, 계정·연결을 저장하지 않는다")
    class Rejected {

        @Test
        @DisplayName("만 14세 미만이면 막는다 — 2012-10-15생은 2026-10-14에 가입할 수 없다")
        void underMinimumAge() {
            MutableClock dayBeforeBirthday = MutableClock.at(LocalDateTime.of(2026, 10, 14, 23, 59));
            SignUpWithOAuthService serviceOnDayBefore = new SignUpWithOAuthService(accountRepository, linkRepository,
                    tokenRepository, emailSendPort, sendNotificationUseCase, new FixedTokenGenerator(TOKEN_VALUE),
                    WebAddress.of("https://ticket-on.example"), dayBeforeBirthday);
            SignUpWithOAuthCommand command = new SignUpWithOAuthCommand(SEOYUN_GOOGLE,
                    AudiencePersonalInformation.of("한서윤", LocalDate.of(2012, 10, 15), SEOYUN_EMAIL, SEOYUN_PHONE), true,
                    SignupConsentChoices.agreeingToAllRequired(false));

            assertRejected(() -> serviceOnDayBefore.signUp(command), AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE);
            assertNothingSaved();
        }

        @Test
        @DisplayName("필수 동의가 빠지면 막는다")
        void requiredConsentMissing() {
            SignUpWithOAuthCommand command = new SignUpWithOAuthCommand(SEOYUN_GOOGLE,
                    personalInformation(SEOYUN_EMAIL, SEOYUN_PHONE), true,
                    SignupConsentChoices.agreeingTo(ConsentItem.TERMS_OF_SERVICE, ConsentItem.MARKETING));

            assertRejected(() -> service.signUp(command), AccountErrorCode.SIGNUP_REQUIRED_CONSENT_MISSING);
            assertNothingSaved();
        }

        @Test
        @DisplayName("같은 번호로 가입한 계정이 있으면 막는다 — 구글 계정이 셋이어도 번호가 하나면 계정은 하나 (docs/01)")
        void phoneNumberRegistered() {
            accounts.active("first@example.com", SEOYUN_PHONE);

            assertRejected(() -> service.signUp(command(SEOYUN_GOOGLE, SEOYUN_EMAIL, true, false)),
                    AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
            assertThat(linkRepository.savedLinks()).isEmpty();
        }

        @Test
        @DisplayName("차단된 번호도 같은 문구로 막는다")
        void phoneNumberBlocked() {
            accountRepository.block(PhoneNumber.of(SEOYUN_PHONE));

            assertRejected(() -> service.signUp(command(SEOYUN_KAKAO, SEOYUN_EMAIL, false, false)),
                    AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
            assertNothingSaved();
        }

        @Test
        @DisplayName("적은 이메일의 관객 계정이 있으면 자동으로 묶지 않고 그 제공자 계정을 연결하라고 안내한다")
        void emailRegistered() {
            accounts.active("seoyun@example.com", "010-9999-0000");

            assertRejected(() -> service.signUp(command(SEOYUN_KAKAO, SEOYUN_EMAIL, false, false)),
                    AccountErrorCode.KAKAO_SIGNUP_EMAIL_ALREADY_REGISTERED);
            assertThat(linkRepository.savedLinks()).isEmpty();
        }

        @Test
        @DisplayName("이미 관객 계정에 연결된 제공자 계정이면 막는다 — 가입 버튼을 두 번 눌러도 계정은 하나")
        void identityAlreadyLinked() {
            service.signUp(command(SEOYUN_GOOGLE, SEOYUN_EMAIL, true, false));

            assertRejected(() -> service.signUp(command(SEOYUN_GOOGLE, "another@example.com", true, false)),
                    AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED);
            assertThat(accountRepository.savedAccounts()).hasSize(1);
        }

        @Test
        @DisplayName("확인한 뒤 같은 제공자 계정으로 다른 가입이 먼저 저장되면 충돌로 끝난다(트랜잭션이 계정 저장도 되돌린다)")
        void lostLinkRace() {
            linkRepository.letAnotherRequestWinNextAdd(new AudienceOAuthLink(99, SEOYUN_GOOGLE, SIGNUP_INSTANT));

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> service.signUp(command(SEOYUN_GOOGLE, SEOYUN_EMAIL, true, false)))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED));
            assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
        }

        private void assertNothingSaved() {
            assertThat(accountRepository.savedAccounts()).isEmpty();
            assertThat(linkRepository.savedLinks()).isEmpty();
            assertThat(emailSendPort.sentMessages()).isEmpty();
            assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
        }
    }

    private static SignUpWithOAuthCommand command(OAuthIdentity identity, String email, boolean emailVerifiedByProvider,
            boolean marketingAgreed) {
        return new SignUpWithOAuthCommand(identity, personalInformation(email, SEOYUN_PHONE), emailVerifiedByProvider,
                SignupConsentChoices.agreeingToAllRequired(marketingAgreed));
    }

    private static AudiencePersonalInformation personalInformation(String email, String phoneNumber) {
        return AudiencePersonalInformation.of("한서윤", LocalDate.of(1998, 4, 2), email, phoneNumber);
    }

    private static void assertRejected(ThrowingCallable action, ErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
