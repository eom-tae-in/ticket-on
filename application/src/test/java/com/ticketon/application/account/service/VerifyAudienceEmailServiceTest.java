package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.SignUpAudienceResult;
import com.ticketon.application.account.port.in.VerifyAudienceEmailCommand;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관객 이메일 인증 확인 유스케이스")
class VerifyAudienceEmailServiceTest {

    private static final LocalDateTime SIGNUP_TIME = LocalDateTime.of(2026, 10, 15, 10, 0);

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final FakeEmailVerificationTokenRepository tokenRepository = new FakeEmailVerificationTokenRepository();
    private final RecordingEmailSendPort emailSendPort = new RecordingEmailSendPort();
    private final MutableClock clock = MutableClock.at(SIGNUP_TIME);

    private final VerifyAudienceEmailService service = new VerifyAudienceEmailService(tokenRepository, accountRepository,
            clock);

    private long accountId;

    private String tokenInMail;

    /** 실제 가입 유스케이스로 가입하고, 받은 메일의 링크에서 값을 꺼낸다. */
    @BeforeEach
    void signUp() {
        SignUpAudienceService signUpService = new SignUpAudienceService(accountRepository, tokenRepository,
                new FakePasswordHashPort(), emailSendPort, new FixedTokenGenerator("verifyAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                WebAddress.of("https://ticket-on.example"), clock);
        SignUpAudienceResult result = signUpService.signUp(
                SignUpAudienceServiceTest.command("seoyun@example.com", "010-1234-5678", false));
        accountId = result.audienceAccountId();
        Matcher link = TOKEN_IN_LINK.matcher(emailSendPort.sentMessages().getFirst().body());
        assertThat(link.find()).isTrue();
        tokenInMail = link.group(1);
    }

    @Test
    @DisplayName("메일의 링크를 열면 계정이 활동 상태가 되고, 연 시각이 인증 일시로 남는다")
    void verifiesWithLinkInMail() {
        clock.advance(Duration.ofHours(3));

        service.verifyEmail(new VerifyAudienceEmailCommand(tokenInMail));

        AudienceAccount account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
        assertThat(account.emailVerifiedAt()).contains(KoreanTime.toInstant(SIGNUP_TIME.plusHours(3)));
    }

    @Test
    @DisplayName("가입하고 24시간에서 1분 모자랄 때 열어도 인증된다")
    void verifiesJustBeforeExpiry() {
        clock.advance(Duration.ofHours(24).minusMinutes(1));

        service.verifyEmail(new VerifyAudienceEmailCommand(tokenInMail));

        assertThat(accountRepository.findById(accountId).orElseThrow().status()).isEqualTo(AudienceAccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("가입하고 24시간이 지나 열면 '유효 시간이 지났습니다'이고 계정은 인증 대기 그대로다")
    void rejectsAfterTwentyFourHours() {
        clock.advance(Duration.ofHours(24));

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.verifyEmail(new VerifyAudienceEmailCommand(tokenInMail)))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED);
        assertThat(accountRepository.findById(accountId).orElseThrow().status())
                .isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
    }

    @Test
    @DisplayName("같은 링크를 다시 열면 '이미 이메일 인증을 마친 계정입니다'")
    void secondOpenSaysAlreadyVerified() {
        service.verifyEmail(new VerifyAudienceEmailCommand(tokenInMail));

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.verifyEmail(new VerifyAudienceEmailCommand(tokenInMail)))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.EMAIL_ALREADY_VERIFIED);
    }

    @Test
    @DisplayName("발급한 적 없는 링크면 '인증 링크가 올바르지 않습니다'")
    void rejectsUnknownToken() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.verifyEmail(new VerifyAudienceEmailCommand("unknownAAAAAAAAAAAAAAAAAAAAAAAAA")))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.EMAIL_VERIFICATION_LINK_NOT_FOUND);
        assertThat(AccountErrorCode.EMAIL_VERIFICATION_LINK_NOT_FOUND.message()).isEqualTo("인증 링크가 올바르지 않습니다.");
    }
}
