package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.application.account.port.in.LogInAudienceCommand;
import com.ticketon.application.account.port.in.LogInStaffCommand;
import com.ticketon.application.account.port.in.RequestPasswordResetCommand;
import com.ticketon.application.account.port.in.ResetPasswordCommand;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 비밀번호 찾기로 링크를 받은 뒤 재설정까지 — 링크는 비밀번호 찾기 유스케이스로 만들고, 바꾼 뒤에는 로그인 유스케이스로 새 비밀번호를
 * 확인한다.
 */
@DisplayName("비밀번호 재설정 유스케이스")
class ResetPasswordServiceTest {

    /** 비밀번호 찾기를 한 일시 — 가입한 다음 날 2026-10-16 10:00 (한국 시각). */
    private static final LocalDateTime REQUEST_TIME = LocalDateTime.of(2026, 10, 16, 10, 0);

    private static final String FIRST_TOKEN = "resetAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String SECOND_TOKEN = "resetBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private static final String NEW_PASSWORD = "new-password-5678";

    private static final String SEOYUN_EMAIL = "seoyun@example.com";

    private static final String SEOYUN_PHONE = "010-1234-5678";

    private static final String JUNGHOON_EMAIL = "junghoon@example.com";

    private final FakeAudienceAccountRepository audienceAccountRepository = new FakeAudienceAccountRepository();
    private final FakeStaffAccountRepository staffAccountRepository = new FakeStaffAccountRepository();
    private final WriteLog writeLog = new WriteLog();
    private final FakePasswordResetTokenRepository tokenRepository = new FakePasswordResetTokenRepository(writeLog);
    private final FakePasswordHashPort passwordHashPort = new FakePasswordHashPort();
    private final RecordingSessionTerminationPort sessionTerminationPort = new RecordingSessionTerminationPort();
    private final MutableClock clock = MutableClock.at(REQUEST_TIME);
    private final StoredAudienceAccounts audienceAccounts = new StoredAudienceAccounts(audienceAccountRepository);
    private final StoredStaffAccounts staffAccounts = new StoredStaffAccounts(staffAccountRepository);

    private final RequestPasswordResetService requestService = new RequestPasswordResetService(audienceAccountRepository,
            staffAccountRepository, tokenRepository, new RecordingEmailSendPort(),
            new FixedTokenGenerator(FIRST_TOKEN, SECOND_TOKEN), WebAddress.of("https://ticket-on.example"), clock);

    private final ResetPasswordService service = new ResetPasswordService(tokenRepository, audienceAccountRepository,
            staffAccountRepository, passwordHashPort, sessionTerminationPort, clock);

    private final LogInAudienceService audienceLogin = new LogInAudienceService(audienceAccountRepository, passwordHashPort);

    private final LogInStaffService staffLogin = new LogInStaffService(staffAccountRepository, passwordHashPort);

    @Nested
    @DisplayName("바뀐다")
    class Succeeds {

        @Test
        @DisplayName("관객 — 59분 뒤 링크를 열어 새 비밀번호를 정하면 새 비밀번호로 로그인되고 이전 비밀번호로는 안 된다")
        void audiencePasswordIsReset() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            requestAudienceReset(SEOYUN_EMAIL);
            clock.advance(Duration.ofMinutes(59));

            service.resetPassword(audienceReset(FIRST_TOKEN));

            assertThat(audienceLogin.logIn(audienceLogIn(SEOYUN_EMAIL, NEW_PASSWORD)).account().kind())
                    .isEqualTo(AccountKind.AUDIENCE);
            assertRejected(() -> audienceLogin.logIn(audienceLogIn(SEOYUN_EMAIL, StoredAudienceAccounts.RAW_PASSWORD)),
                    AccountErrorCode.AUDIENCE_LOGIN_FAILED);
            assertThat(tokenRepository.savedTokens()).singleElement().satisfies(token -> assertThat(token.usedAt())
                    .contains(KoreanTime.toInstant(REQUEST_TIME.plusMinutes(59))));
        }

        @Test
        @DisplayName("운영진 — 주최자 쪽 링크로 새 비밀번호를 정하면 새 비밀번호로 로그인된다")
        void staffPasswordIsReset() {
            staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");
            requestService.requestReset(new RequestPasswordResetCommand(AccountKind.STAFF, EmailAddress.of(JUNGHOON_EMAIL)));

            service.resetPassword(staffReset(FIRST_TOKEN));

            assertThat(staffLogin.logIn(staffLogIn(JUNGHOON_EMAIL, NEW_PASSWORD)).account().kind())
                    .isEqualTo(AccountKind.STAFF);
        }

        @Test
        @DisplayName("관객 쪽 링크로 바꾸면 같은 이메일의 운영진 로그인 비밀번호는 그대로다 (docs/01 \"같은 이메일로 양쪽 가입\")")
        void audienceResetLeavesStaffPasswordAlone() {
            audienceAccounts.active(JUNGHOON_EMAIL, "010-2345-6789");
            staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");
            requestAudienceReset(JUNGHOON_EMAIL);

            service.resetPassword(audienceReset(FIRST_TOKEN));

            assertThat(staffLogin.logIn(staffLogIn(JUNGHOON_EMAIL, StoredStaffAccounts.RAW_PASSWORD)).account().kind())
                    .isEqualTo(AccountKind.STAFF);
            assertRejected(() -> staffLogin.logIn(staffLogIn(JUNGHOON_EMAIL, NEW_PASSWORD)),
                    AccountErrorCode.STAFF_LOGIN_FAILED);
        }

        @Test
        @DisplayName("비밀번호는 해시로만 저장한다 — 원문은 남지 않는다")
        void storesOnlyHash() {
            long id = audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            requestAudienceReset(SEOYUN_EMAIL);

            service.resetPassword(audienceReset(FIRST_TOKEN));

            AudienceAccount saved = audienceAccountRepository.findById(id).orElseThrow();
            assertThat(saved.passwordHash()).contains(FakePasswordHashPort.hashOf(NEW_PASSWORD));
            assertThat(saved.passwordHash().orElseThrow().value()).doesNotContain(NEW_PASSWORD);
        }
    }

    @Nested
    @DisplayName("잠금 순서 — 계정을 먼저 잠그고 링크·계정 줄을 바꾼다 (비밀번호 찾기와 같은 순서라 교착되지 않는다)")
    class LockOrder {

        @Test
        @DisplayName("관객 — 계정 잠금 → 링크 사용 저장 → 비밀번호 저장")
        void audienceLocksAccountFirst() {
            long id = audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            audienceAccountRepository.recordPasswordSavesIn(writeLog);
            requestAudienceReset(SEOYUN_EMAIL);
            writeLog.clear();

            service.resetPassword(audienceReset(FIRST_TOKEN));

            assertThat(writeLog.writes()).containsExactly("계정 잠금 AUDIENCE:" + id, "링크 사용 AUDIENCE:" + id,
                    "비밀번호 저장 AUDIENCE:" + id);
        }

        @Test
        @DisplayName("운영진 — 계정 잠금 → 링크 사용 저장 → 비밀번호 저장")
        void staffLocksAccountFirst() {
            long id = staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");
            staffAccountRepository.recordPasswordSavesIn(writeLog);
            requestService.requestReset(new RequestPasswordResetCommand(AccountKind.STAFF, EmailAddress.of(JUNGHOON_EMAIL)));
            writeLog.clear();

            service.resetPassword(staffReset(FIRST_TOKEN));

            assertThat(writeLog.writes()).containsExactly("계정 잠금 STAFF:" + id, "링크 사용 STAFF:" + id,
                    "비밀번호 저장 STAFF:" + id);
        }
    }

    @Nested
    @DisplayName("바꾸면 그 계정의 기존 로그인 세션을 모두 끊는다")
    class TerminatesSessions {

        @Test
        @DisplayName("관객 계정을 바꾸면 그 관객 주체(AUDIENCE:id)의 세션을 끊는다")
        void terminatesAudienceSessions() {
            long id = audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            requestAudienceReset(SEOYUN_EMAIL);

            service.resetPassword(audienceReset(FIRST_TOKEN));

            assertThat(sessionTerminationPort.terminatedAccounts()).containsExactly(AccountRef.of(AccountKind.AUDIENCE, id));
        }

        @Test
        @DisplayName("운영진 로그인을 바꾸면 그 주체(STAFF:id)의 세션만 끊는다 — 같은 이메일의 관객 세션은 다른 주체라 그대로다")
        void terminatesOnlyStaffSessions() {
            audienceAccounts.active(JUNGHOON_EMAIL, "010-2345-6789");
            long staffId = staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");
            requestService.requestReset(new RequestPasswordResetCommand(AccountKind.STAFF, EmailAddress.of(JUNGHOON_EMAIL)));

            service.resetPassword(staffReset(FIRST_TOKEN));

            assertThat(sessionTerminationPort.terminatedAccounts()).containsExactly(AccountRef.of(AccountKind.STAFF, staffId));
        }

        @Test
        @DisplayName("바뀌지 않으면(유효 시간이 지난 링크 등) 세션도 끊지 않는다")
        void keepsSessionsWhenNotReset() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            requestAudienceReset(SEOYUN_EMAIL);
            clock.advance(Duration.ofHours(1));

            assertRejected(() -> service.resetPassword(audienceReset(FIRST_TOKEN)),
                    AccountErrorCode.PASSWORD_RESET_LINK_EXPIRED);
            assertThat(sessionTerminationPort.terminatedAccounts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("바뀌지 않는다")
    class Fails {

        @Test
        @DisplayName("발급한 적 없는 링크 — 404 '비밀번호 재설정 링크가 올바르지 않습니다.'")
        void unknownToken() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> service.resetPassword(audienceReset(FIRST_TOKEN)))
                    .satisfies(exception -> {
                        assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.PASSWORD_RESET_LINK_NOT_FOUND);
                        assertThat(exception.errorCode().message()).isEqualTo("비밀번호 재설정 링크가 올바르지 않습니다.");
                    });
        }

        @Test
        @DisplayName("운영진 로그인 링크를 관객 쪽에서 열면 발급한 적 없는 링크다 — 어느 계정 비밀번호도 바뀌지 않는다")
        void staffLinkOnAudienceSide() {
            long audienceId = audienceAccounts.active(JUNGHOON_EMAIL, "010-2345-6789");
            staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");
            requestService.requestReset(new RequestPasswordResetCommand(AccountKind.STAFF, EmailAddress.of(JUNGHOON_EMAIL)));

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> service.resetPassword(audienceReset(FIRST_TOKEN)));
            assertThat(audienceAccountRepository.findById(audienceId).orElseThrow().passwordHash())
                    .contains(FakePasswordHashPort.hashOf(StoredAudienceAccounts.RAW_PASSWORD));
            assertThat(staffAccountRepository.savedAccounts()).singleElement().satisfies(account -> assertThat(
                    account.passwordHash()).isEqualTo(FakePasswordHashPort.hashOf(StoredStaffAccounts.RAW_PASSWORD)));
        }

        @Test
        @DisplayName("한 번 쓴 링크로는 다시 바꿀 수 없다 — 처음 정한 비밀번호가 남는다")
        void usedTokenCannotBeReused() {
            long id = audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            requestAudienceReset(SEOYUN_EMAIL);
            service.resetPassword(audienceReset(FIRST_TOKEN));

            assertRejected(() -> service.resetPassword(new ResetPasswordCommand(AccountKind.AUDIENCE, FIRST_TOKEN,
                    new NewPassword("another-password-9"))), AccountErrorCode.PASSWORD_RESET_LINK_ALREADY_USED);
            assertThat(audienceAccountRepository.findById(id).orElseThrow().passwordHash())
                    .contains(FakePasswordHashPort.hashOf(NEW_PASSWORD));
        }

        @Test
        @DisplayName("1시간이 지나 열면 유효 시간이 지난 링크다 — 이전 비밀번호 그대로다")
        void expiredToken() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            requestAudienceReset(SEOYUN_EMAIL);
            clock.advance(Duration.ofHours(1));

            assertRejected(() -> service.resetPassword(audienceReset(FIRST_TOKEN)),
                    AccountErrorCode.PASSWORD_RESET_LINK_EXPIRED);
            assertThat(audienceLogin.logIn(audienceLogIn(SEOYUN_EMAIL, StoredAudienceAccounts.RAW_PASSWORD))).isNotNull();
        }

        @Test
        @DisplayName("비밀번호 찾기를 다시 했으면 이전 링크는 대체된 링크다 — 새 링크로는 바뀐다")
        void replacedTokenCannotBeUsed() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            requestAudienceReset(SEOYUN_EMAIL);
            clock.advance(Duration.ofMinutes(5));
            requestAudienceReset(SEOYUN_EMAIL);

            assertRejected(() -> service.resetPassword(audienceReset(FIRST_TOKEN)),
                    AccountErrorCode.PASSWORD_RESET_LINK_REPLACED);
            service.resetPassword(audienceReset(SECOND_TOKEN));
            assertThat(audienceLogin.logIn(audienceLogIn(SEOYUN_EMAIL, NEW_PASSWORD))).isNotNull();
        }

        @Test
        @DisplayName("링크를 받은 뒤 탈퇴한 계정은 바꾸지 않고, 링크도 쓰지 않은 채로 둔다")
        void withdrawnAfterRequest() {
            long id = audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            requestAudienceReset(SEOYUN_EMAIL);
            AudienceAccount account = audienceAccountRepository.findById(id).orElseThrow();
            audienceAccountRepository.replace(AudienceAccount.restore(id, account.personalInformation(),
                    account.passwordHash().orElse(null), account.signedUpAt(), account.consentRecords(), AudienceAccountStatus.WITHDRAWN,
                    account.emailVerifiedAt().orElse(null), account.suspensions()));

            assertRejected(() -> service.resetPassword(audienceReset(FIRST_TOKEN)), AccountErrorCode.AUDIENCE_WITHDRAWN);
            assertThat(tokenRepository.savedTokens()).singleElement()
                    .satisfies(token -> assertThat(token.usedAt()).isEmpty());
        }

        @Test
        @DisplayName("관리자·검표 쪽으로는 재설정하지 않는다")
        void onlyAudienceOrStaff() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new ResetPasswordCommand(AccountKind.CHECKIN, FIRST_TOKEN, new NewPassword(NEW_PASSWORD)));
        }
    }

    @Test
    @DisplayName("입력을 문자열로 바꿔도 링크 값과 새 비밀번호는 드러나지 않는다")
    void commandHidesSecrets() {
        assertThat(audienceReset(FIRST_TOKEN).toString()).doesNotContain(FIRST_TOKEN).doesNotContain(NEW_PASSWORD);
    }

    private void requestAudienceReset(String email) {
        requestService.requestReset(new RequestPasswordResetCommand(AccountKind.AUDIENCE, EmailAddress.of(email)));
    }

    private static ResetPasswordCommand audienceReset(String token) {
        return new ResetPasswordCommand(AccountKind.AUDIENCE, token, new NewPassword(NEW_PASSWORD));
    }

    private static ResetPasswordCommand staffReset(String token) {
        return new ResetPasswordCommand(AccountKind.STAFF, token, new NewPassword(NEW_PASSWORD));
    }

    private static LogInAudienceCommand audienceLogIn(String email, String password) {
        return new LogInAudienceCommand(EmailAddress.of(email), new EnteredPassword(password));
    }

    private static LogInStaffCommand staffLogIn(String email, String password) {
        return new LogInStaffCommand(EmailAddress.of(email), new EnteredPassword(password));
    }

    private static void assertRejected(ThrowingCallable action, AccountErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
