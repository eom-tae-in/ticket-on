package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.account.port.in.SignUpAudienceResult;
import com.ticketon.application.account.port.in.SignUpStaffCommand;
import com.ticketon.application.account.port.in.SignUpStaffResult;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.EmailMessage;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("운영진 로그인 가입 유스케이스")
class SignUpStaffServiceTest {

    private static final LocalDateTime SIGNUP_TIME = LocalDateTime.of(2026, 10, 15, 10, 0);

    private static final String TOKEN_VALUE = "staffAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String RAW_PASSWORD = "junghoon1234";

    private static final WebAddress WEB_ADDRESS = WebAddress.of("https://ticket-on.example");

    private final FakeStaffAccountRepository accountRepository = new FakeStaffAccountRepository();
    private final FakeStaffEmailVerificationTokenRepository tokenRepository = new FakeStaffEmailVerificationTokenRepository();
    private final RecordingEmailSendPort emailSendPort = new RecordingEmailSendPort();
    private final MutableClock clock = MutableClock.at(SIGNUP_TIME);

    private final SignUpStaffService service = new SignUpStaffService(accountRepository, tokenRepository,
            new FakePasswordHashPort(), emailSendPort, new FixedTokenGenerator(TOKEN_VALUE, TOKEN_VALUE.replace('A', 'B')),
            WEB_ADDRESS, clock);

    @Test
    @DisplayName("가입하면 인증 대기 계정이 저장되고, 결과로 새 계정 id와 인증 메일을 보낸 이메일을 돌려준다")
    void savesPendingAccount() {
        SignUpStaffResult result = service.signUp(command("junghoon@example.com", "조용한고래"));

        StaffAccount saved = accountRepository.findById(result.staffAccountId()).orElseThrow();
        assertThat(saved.status()).isEqualTo(StaffAccountStatus.PENDING_VERIFICATION);
        assertThat(saved.personalInformation()).isEqualTo(StaffPersonalInformation.of("박정훈", "junghoon@example.com"));
        assertThat(saved.nickname()).isEqualTo(Nickname.of("조용한고래"));
        assertThat(saved.signedUpAt()).isEqualTo(KoreanTime.toInstant(SIGNUP_TIME));
        assertThat(result.email()).isEqualTo("junghoon@example.com");
    }

    @Test
    @DisplayName("비밀번호는 해시로만 저장한다 — 원문은 남지 않는다")
    void storesOnlyPasswordHash() {
        SignUpStaffResult result = service.signUp(command("junghoon@example.com", "조용한고래"));

        StaffAccount saved = accountRepository.findById(result.staffAccountId()).orElseThrow();
        assertThat(saved.passwordHash()).isEqualTo(FakePasswordHashPort.hashOf(RAW_PASSWORD));
        assertThat(saved.passwordHash().value()).doesNotContain(RAW_PASSWORD);
    }

    @Test
    @DisplayName("같은 이메일로 관객 계정과 운영진 로그인을 각각 가입할 수 있다 — 서로 다른 계정이다 (docs/01 \"같은 이메일로 양쪽 가입\")")
    void sameEmailCanSignUpOnBothSides() {
        SignUpAudienceService audienceSignUp = new SignUpAudienceService(new FakeAudienceAccountRepository(),
                new FakeEmailVerificationTokenRepository(), new FakePasswordHashPort(), emailSendPort,
                new FixedTokenGenerator("verifyAAAAAAAAAAAAAAAAAAAAAAAAAA"), WEB_ADDRESS, clock);

        SignUpAudienceResult audience = audienceSignUp.signUp(
                SignUpAudienceServiceTest.command("junghoon@example.com", "010-2345-6789", false));
        SignUpStaffResult staff = service.signUp(command("junghoon@example.com", "조용한고래"));

        assertThat(audience.email()).isEqualTo(staff.email());
        assertThat(accountRepository.savedAccounts()).singleElement()
                .satisfies(account -> assertThat(account.id()).hasValue(staff.staffAccountId()));
        assertThat(emailSendPort.sentMessages()).extracting(message -> message.subject().value())
                .containsExactly("이메일 인증을 마쳐 주세요", "[주최] 이메일 인증을 마쳐 주세요");
    }

    @Nested
    @DisplayName("인증 메일 — 주최자 쪽 메일이라 제목에 [주최] (docs/12)")
    class VerificationMail {

        @Test
        @DisplayName("추측 불가 값 32자로 관객과 같은 24시간짜리 인증 링크를 만들어 저장한다")
        void issuesTwentyFourHourToken() {
            SignUpStaffResult result = service.signUp(command("junghoon@example.com", "조용한고래"));

            assertThat(tokenRepository.savedTokens()).singleElement().satisfies(token -> {
                assertThat(token.value()).isEqualTo(TOKEN_VALUE).hasSize(EmailVerificationToken.LENGTH);
                assertThat(token.staffAccountId()).isEqualTo(result.staffAccountId());
                assertThat(token.expiresAt()).isEqualTo(KoreanTime.toInstant(SIGNUP_TIME).plus(Duration.ofHours(24)));
            });
        }

        @Test
        @DisplayName("가입한 이메일로 '[주최] 이메일 인증을 마쳐 주세요' 메일을 보내고, 본문에 주최 화면의 인증 페이지 링크와 유효 시간이 있다")
        void sendsOrganizerMailWithLink() {
            service.signUp(command("Junghoon@Example.com", "조용한고래"));

            assertThat(emailSendPort.sentMessages()).hasSize(1);
            EmailMessage message = emailSendPort.sentMessages().getFirst();
            assertThat(message.recipientAddress()).isEqualTo("Junghoon@Example.com");
            assertThat(message.subject().value()).isEqualTo("[주최] 이메일 인증을 마쳐 주세요");
            assertThat(message.body())
                    .contains("https://ticket-on.example/organizer/verify-email?token=" + TOKEN_VALUE)
                    .contains("24시간");
        }

        @Test
        @DisplayName("메일을 보내지 못하면 예외로 가입을 되돌린다 — 인증 대기 계정이 이메일·닉네임을 쥔 채 남지 않게")
        void failsWhenMailCannotBeSent() {
            emailSendPort.failWith("메일 서버 응답 없음");

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.signUp(command("junghoon@example.com", "조용한고래")))
                    .withMessageContaining("메일 서버 응답 없음");
        }
    }

    @Nested
    @DisplayName("가입을 막는 경우 — 계정·링크를 저장하지 않고 메일도 보내지 않는다")
    class Rejections {

        @Test
        @DisplayName("대소문자만 다른 같은 이메일의 운영진 로그인이 있으면 '이미 가입된 이메일입니다'")
        void rejectsRegisteredEmail() {
            service.signUp(command("junghoon@example.com", "조용한고래"));

            assertRejectedWithoutSideEffects(() -> service.signUp(command("JUNGHOON@example.com", "푸른바람")),
                    AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED, 1);
        }

        @Test
        @DisplayName("다른 운영진이 쓰는 닉네임이면 '이미 사용 중인 닉네임입니다' — 중복 불가 (docs/99 C)")
        void rejectsTakenNickname() {
            service.signUp(command("junghoon@example.com", "조용한고래"));

            assertRejectedWithoutSideEffects(() -> service.signUp(command("dohyun@example.com", " 조용한고래 ")),
                    AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN, 1);
        }

        @Test
        @DisplayName("자모를 늘어놓은 꼴(NFD)로 적은 같은 닉네임도 막는다 — 화면에서 똑같이 보이므로")
        void rejectsDecomposedFormOfTakenNickname() {
            service.signUp(command("junghoon@example.com", "조용한고래"));

            assertRejectedWithoutSideEffects(() -> service.signUp(
                    command("dohyun@example.com", Normalizer.normalize("조용한고래", Normalizer.Form.NFD))),
                    AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN, 1);
        }

        @Test
        @DisplayName("확인은 통과했지만 저장 직전 같은 이메일·닉네임의 다른 가입이 먼저 저장되면 충돌이 그대로 나간다")
        void propagatesSignupRaceConflict() {
            accountRepository.letAnotherSignupWinNextAdd(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> service.signUp(command("junghoon@example.com", "조용한고래")))
                    .satisfies(conflict -> assertThat(conflict.errorCode())
                            .isEqualTo(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN));
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

    static SignUpStaffCommand command(String email, String nickname) {
        return new SignUpStaffCommand(StaffPersonalInformation.of("박정훈", email), Nickname.of(nickname),
                new NewPassword(RAW_PASSWORD));
    }
}
