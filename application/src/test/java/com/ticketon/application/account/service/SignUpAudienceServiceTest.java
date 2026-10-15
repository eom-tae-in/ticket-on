package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.account.port.in.SignUpAudienceCommand;
import com.ticketon.application.account.port.in.SignUpAudienceResult;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.ConsentItem;
import com.ticketon.domain.account.ConsentRecord;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.EmailMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("관객 직접 가입 유스케이스")
class SignUpAudienceServiceTest {

    private static final LocalDateTime SIGNUP_TIME = LocalDateTime.of(2026, 10, 15, 10, 0);

    private static final String TOKEN_VALUE = "verifyAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String RAW_PASSWORD = "seoyun1234";

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final FakeEmailVerificationTokenRepository tokenRepository = new FakeEmailVerificationTokenRepository();
    private final RecordingEmailSendPort emailSendPort = new RecordingEmailSendPort();
    private final MutableClock clock = MutableClock.at(SIGNUP_TIME);

    private final SignUpAudienceService service = new SignUpAudienceService(accountRepository, tokenRepository,
            new FakePasswordHashPort(), emailSendPort, new FixedTokenGenerator(TOKEN_VALUE, TOKEN_VALUE.replace('A', 'B')),
            WebAddress.of("https://ticket-on.example"), clock);

    @Test
    @DisplayName("가입하면 인증 대기 계정이 저장되고, 결과로 새 계정 id와 인증 메일을 보낸 이메일을 돌려준다")
    void savesPendingAccount() {
        SignUpAudienceResult result = service.signUp(command("seoyun@example.com", "010-1234-5678", false));

        AudienceAccount saved = accountRepository.findById(result.audienceAccountId()).orElseThrow();
        assertThat(saved.status()).isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
        assertThat(saved.signedUpAt()).isEqualTo(KoreanTime.toInstant(SIGNUP_TIME));
        assertThat(saved.personalInformation().phoneNumber()).isEqualTo(PhoneNumber.of("01012345678"));
        assertThat(result.email()).isEqualTo("seoyun@example.com");
    }

    @Test
    @DisplayName("비밀번호는 해시로만 저장한다 — 원문은 남지 않는다")
    void storesOnlyPasswordHash() {
        SignUpAudienceResult result = service.signUp(command("seoyun@example.com", "010-1234-5678", false));

        AudienceAccount saved = accountRepository.findById(result.audienceAccountId()).orElseThrow();
        assertThat(saved.passwordHash()).contains(FakePasswordHashPort.hashOf(RAW_PASSWORD));
        assertThat(saved.passwordHash().orElseThrow().value()).doesNotContain(RAW_PASSWORD);
    }

    @Test
    @DisplayName("네 동의 항목의 동의 여부가 가입 일시로 남는다 — 마케팅을 고르지 않았으면 '동의하지 않음'")
    void recordsConsents() {
        SignUpAudienceResult result = service.signUp(command("seoyun@example.com", "010-1234-5678", false));

        Instant signupInstant = KoreanTime.toInstant(SIGNUP_TIME);
        assertThat(accountRepository.findById(result.audienceAccountId()).orElseThrow().consentRecords())
                .contains(new ConsentRecord(ConsentItem.MARKETING, false, signupInstant))
                .hasSize(4);
    }

    @Nested
    @DisplayName("인증 메일")
    class VerificationMail {

        @Test
        @DisplayName("추측 불가 값 32자로 24시간짜리 인증 링크를 만들어 저장한다")
        void issuesTwentyFourHourToken() {
            SignUpAudienceResult result = service.signUp(command("seoyun@example.com", "010-1234-5678", false));

            assertThat(tokenRepository.savedTokens()).singleElement().satisfies(token -> {
                assertThat(token.value()).isEqualTo(TOKEN_VALUE).hasSize(EmailVerificationToken.LENGTH);
                assertThat(token.audienceAccountId()).isEqualTo(result.audienceAccountId());
                assertThat(token.expiresAt()).isEqualTo(KoreanTime.toInstant(SIGNUP_TIME).plus(Duration.ofHours(24)));
            });
        }

        @Test
        @DisplayName("가입한 이메일로 접두사 없는 제목의 메일을 보내고, 본문에 화면의 인증 페이지 링크와 유효 시간이 있다")
        void sendsMailWithLink() {
            service.signUp(command("Seoyun@Example.com", "010-1234-5678", false));

            EmailMessage message = emailSendPort.sentMessages().getFirst();
            assertThat(emailSendPort.sentMessages()).hasSize(1);
            assertThat(message.recipientAddress()).isEqualTo("Seoyun@Example.com");
            assertThat(message.subject().value()).isEqualTo("이메일 인증을 마쳐 주세요");
            assertThat(message.body())
                    .contains("https://ticket-on.example/verify-email?token=" + TOKEN_VALUE)
                    .contains("24시간");
        }

        @Test
        @DisplayName("메일을 보내지 못하면 예외로 가입을 되돌린다 — 인증 대기 계정이 번호를 쥔 채 남지 않게")
        void failsWhenMailCannotBeSent() {
            emailSendPort.failWith("메일 서버 응답 없음");

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.signUp(command("seoyun@example.com", "010-1234-5678", false)))
                    .withMessageContaining("메일 서버 응답 없음");
        }
    }

    @Nested
    @DisplayName("가입을 막는 경우 — 계정·링크를 저장하지 않고 메일도 보내지 않는다")
    class Rejections {

        @Test
        @DisplayName("같은 번호로 가입한 계정이 있으면 '이미 가입된 번호' (docs/01)")
        void rejectsRegisteredPhoneNumber() {
            service.signUp(command("first@example.com", "010-1234-5678", false));

            assertRejectedWithoutSideEffects(() -> service.signUp(command("second@example.com", "01012345678", false)),
                    AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED, 1);
        }

        @Test
        @DisplayName("이용 정지 후 탈퇴로 차단된 번호도 같은 문구로 막는다 — 정지 이력이 드러나지 않게")
        void rejectsBlockedPhoneNumber() {
            accountRepository.block(PhoneNumber.of("010-1234-5678"));

            assertRejectedWithoutSideEffects(() -> service.signUp(command("seoyun@example.com", "010-1234-5678", false)),
                    AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED, 0);
            assertThat(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED.message())
                    .isEqualTo("이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요.");
        }

        @Test
        @DisplayName("대소문자만 다른 같은 이메일의 관객 계정이 있으면 '이미 가입된 이메일'")
        void rejectsRegisteredEmail() {
            service.signUp(command("seoyun@example.com", "010-1234-5678", false));

            assertRejectedWithoutSideEffects(() -> service.signUp(command("SEOYUN@example.com", "010-9876-5432", false)),
                    AccountErrorCode.AUDIENCE_EMAIL_ALREADY_REGISTERED, 1);
        }

        @Test
        @DisplayName("2012-10-15생은 2026-10-14에 만 13세라 가입할 수 없다")
        void rejectsUnderFourteen() {
            clock.moveTo(LocalDateTime.of(2026, 10, 14, 23, 59));
            AudiencePersonalInformation thirteen = AudiencePersonalInformation.of("한서윤", LocalDate.of(2012, 10, 15),
                    "seoyun@example.com", "010-1234-5678");

            assertRejectedWithoutSideEffects(() -> service.signUp(new SignUpAudienceCommand(thirteen,
                    new NewPassword(RAW_PASSWORD), SignupConsentChoices.agreeingToAllRequired(false))),
                    AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE, 0);
        }

        @Test
        @DisplayName("필수 동의가 빠지면 가입할 수 없다")
        void rejectsMissingRequiredConsent() {
            SignUpAudienceCommand marketingOnly = new SignUpAudienceCommand(personalInformation("seoyun@example.com",
                    "010-1234-5678"), new NewPassword(RAW_PASSWORD), SignupConsentChoices.agreeingTo(ConsentItem.MARKETING));

            assertRejectedWithoutSideEffects(() -> service.signUp(marketingOnly),
                    AccountErrorCode.SIGNUP_REQUIRED_CONSENT_MISSING, 0);
        }

        @Test
        @DisplayName("확인은 통과했지만 저장 직전 같은 번호의 다른 가입이 먼저 저장되면 충돌이 그대로 나간다")
        void propagatesSignupRaceConflict() {
            accountRepository.letAnotherSignupWinNextAdd();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> service.signUp(command("seoyun@example.com", "010-1234-5678", false)))
                    .satisfies(conflict -> assertThat(conflict.errorCode())
                            .isEqualTo(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED));
            assertThat(tokenRepository.savedTokens()).isEmpty();
            assertThat(emailSendPort.sentMessages()).isEmpty();
        }

        private void assertRejectedWithoutSideEffects(ThrowingCallable signUp, ErrorCode expectedCode,
                int accountsBefore) {
            int tokensBefore = tokenRepository.savedTokens().size();
            int mailsBefore = emailSendPort.sentMessages().size();

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(signUp)
                    .extracting(DomainException::errorCode)
                    .isEqualTo(expectedCode);
            assertThat(accountRepository.savedAccounts()).hasSize(accountsBefore);
            assertThat(tokenRepository.savedTokens()).hasSize(tokensBefore);
            assertThat(emailSendPort.sentMessages()).hasSize(mailsBefore);
        }
    }

    static SignUpAudienceCommand command(String email, String phoneNumber, boolean marketingAgreed) {
        return new SignUpAudienceCommand(personalInformation(email, phoneNumber), new NewPassword(RAW_PASSWORD),
                SignupConsentChoices.agreeingToAllRequired(marketingAgreed));
    }

    private static AudiencePersonalInformation personalInformation(String email, String phoneNumber) {
        return AudiencePersonalInformation.of("한서윤", LocalDate.of(1998, 4, 2), email, phoneNumber);
    }
}
