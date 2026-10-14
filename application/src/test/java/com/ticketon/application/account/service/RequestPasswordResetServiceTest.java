package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.account.port.in.RequestPasswordResetCommand;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.notification.EmailMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("비밀번호 찾기 유스케이스")
class RequestPasswordResetServiceTest {

    /** 비밀번호 찾기를 한 일시 — 가입한 다음 날 2026-10-16 10:00 (한국 시각). */
    private static final LocalDateTime REQUEST_TIME = LocalDateTime.of(2026, 10, 16, 10, 0);

    private static final String TOKEN_VALUE = "resetAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String SECOND_TOKEN_VALUE = "resetBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private static final WebAddress WEB_ADDRESS = WebAddress.of("https://ticket-on.example");

    private static final String SEOYUN_EMAIL = "seoyun@example.com";

    private static final String SEOYUN_PHONE = "010-1234-5678";

    private static final String JUNGHOON_EMAIL = "junghoon@example.com";

    private final FakeAudienceAccountRepository audienceAccountRepository = new FakeAudienceAccountRepository();
    private final FakeStaffAccountRepository staffAccountRepository = new FakeStaffAccountRepository();
    private final WriteLog writeLog = new WriteLog();
    private final FakePasswordResetTokenRepository tokenRepository = new FakePasswordResetTokenRepository(writeLog);
    private final RecordingEmailSendPort emailSendPort = new RecordingEmailSendPort();
    private final MutableClock clock = MutableClock.at(REQUEST_TIME);
    private final StoredAudienceAccounts audienceAccounts = new StoredAudienceAccounts(audienceAccountRepository);
    private final StoredStaffAccounts staffAccounts = new StoredStaffAccounts(staffAccountRepository);

    private final RequestPasswordResetService service = new RequestPasswordResetService(audienceAccountRepository,
            staffAccountRepository, tokenRepository, emailSendPort,
            new FixedTokenGenerator(TOKEN_VALUE, SECOND_TOKEN_VALUE), WEB_ADDRESS, clock);

    @Nested
    @DisplayName("관객 쪽을 고르면 — 관객 계정만 본다")
    class AudienceSide {

        @Test
        @DisplayName("활동 중인 계정이면 추측 불가 값 32자로 1시간짜리 링크를 만들어 저장한다")
        void issuesOneHourToken() {
            long id = audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(audienceCommand(SEOYUN_EMAIL));

            assertThat(tokenRepository.savedTokens()).singleElement().satisfies(token -> {
                assertThat(token.value()).isEqualTo(TOKEN_VALUE).hasSize(PasswordResetToken.LENGTH);
                assertThat(token.account()).isEqualTo(AccountRef.of(AccountKind.AUDIENCE, id));
                assertThat(token.issuedAt()).isEqualTo(requestInstant());
                assertThat(token.expiresAt()).isEqualTo(requestInstant().plus(Duration.ofHours(1)));
            });
        }

        @Test
        @DisplayName("그 계정의 이메일로 '비밀번호 재설정 안내'(접두사 없음) 메일을 보내고, 본문에 관객 화면의 재설정 링크와 유효 시간이 있다")
        void sendsAudienceMailWithLink() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(audienceCommand(SEOYUN_EMAIL));

            EmailMessage message = emailSendPort.sentMessages().getFirst();
            assertThat(emailSendPort.sentMessages()).hasSize(1);
            assertThat(message.recipientAddress()).isEqualTo(SEOYUN_EMAIL);
            assertThat(message.subject().value()).isEqualTo("비밀번호 재설정 안내");
            assertThat(message.body())
                    .contains("https://ticket-on.example/reset-password?token=" + TOKEN_VALUE)
                    .contains("60분");
        }

        @Test
        @DisplayName("이메일의 대소문자는 가리지 않고, 메일은 계정에 저장된 이메일로 보낸다")
        void emailIgnoresCase() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(audienceCommand("SEOYUN@Example.com"));

            assertThat(emailSendPort.sentMessages()).singleElement()
                    .satisfies(message -> assertThat(message.recipientAddress()).isEqualTo(SEOYUN_EMAIL));
        }

        @Test
        @DisplayName("이용 정지 중인 계정에도 보낸다 — 정지 중에도 로그인은 된다 (docs/01 \"관객 이용 정지\")")
        void suspendedAccountGetsLink() {
            audienceAccounts.suspended(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(audienceCommand(SEOYUN_EMAIL));

            assertThat(tokenRepository.savedTokens()).hasSize(1);
            assertThat(emailSendPort.sentMessages()).hasSize(1);
        }

        @Test
        @DisplayName("같은 이메일의 운영진 로그인이 있어도 관객 계정의 링크만 만든다 — 메일은 한 통, [주최]가 아니다")
        void ignoresStaffAccountWithSameEmail() {
            long audienceId = audienceAccounts.active(JUNGHOON_EMAIL, "010-2345-6789");
            staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");

            service.requestReset(audienceCommand(JUNGHOON_EMAIL));

            assertThat(tokenRepository.savedTokens()).singleElement()
                    .satisfies(token -> assertThat(token.account()).isEqualTo(AccountRef.of(AccountKind.AUDIENCE, audienceId)));
            assertThat(emailSendPort.sentMessages()).singleElement()
                    .satisfies(message -> assertThat(message.subject().value()).isEqualTo("비밀번호 재설정 안내"));
        }
    }

    @Nested
    @DisplayName("주최자 쪽을 고르면 — 운영진 로그인만 본다")
    class StaffSide {

        @Test
        @DisplayName("활동 중인 운영진 로그인이면 링크를 저장하고 '[주최] 비밀번호 재설정 안내' 메일에 주최 화면의 재설정 링크를 보낸다")
        void sendsOrganizerMailWithLink() {
            long id = staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");

            service.requestReset(staffCommand(JUNGHOON_EMAIL));

            assertThat(tokenRepository.savedTokens()).singleElement()
                    .satisfies(token -> assertThat(token.account()).isEqualTo(AccountRef.of(AccountKind.STAFF, id)));
            EmailMessage message = emailSendPort.sentMessages().getFirst();
            assertThat(emailSendPort.sentMessages()).hasSize(1);
            assertThat(message.recipientAddress()).isEqualTo(JUNGHOON_EMAIL);
            assertThat(message.subject().value()).isEqualTo("[주최] 비밀번호 재설정 안내");
            assertThat(message.body()).contains("https://ticket-on.example/organizer/reset-password?token=" + TOKEN_VALUE);
        }

        @Test
        @DisplayName("같은 이메일의 관객 계정이 있어도 운영진 로그인의 링크만 만든다")
        void ignoresAudienceAccountWithSameEmail() {
            audienceAccounts.active(JUNGHOON_EMAIL, "010-2345-6789");
            long staffId = staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");

            service.requestReset(staffCommand(JUNGHOON_EMAIL));

            assertThat(tokenRepository.savedTokens()).singleElement()
                    .satisfies(token -> assertThat(token.account()).isEqualTo(AccountRef.of(AccountKind.STAFF, staffId)));
        }
    }

    @Nested
    @DisplayName("보내지 않는다 — 링크도 메일도 없이 예외 없이 끝나, 부른 쪽은 보낸 경우와 구분할 수 없다 (같은 안내, docs/01)")
    class SendsNothing {

        @Test
        @DisplayName("관객 쪽 — 가입한 적 없는 이메일")
        void unknownAudienceEmail() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(audienceCommand("nobody@example.com"));

            assertNothingSent();
        }

        @Test
        @DisplayName("관객 쪽 — 이메일 인증 전(인증 대기) 계정: 비밀번호가 맞아도 로그인할 수 없는 계정이다")
        void pendingAudienceAccount() {
            audienceAccounts.pending(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(audienceCommand(SEOYUN_EMAIL));

            assertNothingSent();
        }

        @Test
        @DisplayName("관객 쪽 — 탈퇴한 계정: 가입한 적 없는 이메일과 같다")
        void withdrawnAudienceAccount() {
            audienceAccounts.withdrawn(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(audienceCommand(SEOYUN_EMAIL));

            assertNothingSent();
        }

        @Test
        @DisplayName("관객 쪽 — 그 이메일로는 운영진 로그인만 있다")
        void onlyStaffAccountWithThatEmail() {
            staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");

            service.requestReset(audienceCommand(JUNGHOON_EMAIL));

            assertNothingSent();
        }

        @Test
        @DisplayName("주최자 쪽 — 그 이메일로는 관객 계정만 있다")
        void onlyAudienceAccountWithThatEmail() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(staffCommand(SEOYUN_EMAIL));

            assertNothingSent();
        }

        @Test
        @DisplayName("주최자 쪽 — 이메일 인증 전(인증 대기) 운영진 로그인")
        void pendingStaffAccount() {
            staffAccounts.pending(JUNGHOON_EMAIL, "조용한고래");

            service.requestReset(staffCommand(JUNGHOON_EMAIL));

            assertNothingSent();
        }

        @Test
        @DisplayName("주최자 쪽 — 탈퇴한 운영진 로그인")
        void withdrawnStaffAccount() {
            staffAccounts.withdrawn(JUNGHOON_EMAIL, "조용한고래");

            service.requestReset(staffCommand(JUNGHOON_EMAIL));

            assertNothingSent();
        }

        private void assertNothingSent() {
            assertThat(tokenRepository.savedTokens()).isEmpty();
            assertThat(emailSendPort.sentMessages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("잠금 순서 — 계정을 먼저 잠그고 링크를 바꾼다 (재설정과 같은 순서라 교착되지 않는다)")
    class LockOrder {

        @Test
        @DisplayName("관객 — 계정 잠금 → 이전 링크 대체 → 새 링크 저장")
        void audienceLocksAccountFirst() {
            long id = audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.requestReset(audienceCommand(SEOYUN_EMAIL));

            assertThat(writeLog.writes()).containsExactly("계정 잠금 AUDIENCE:" + id, "링크 대체 AUDIENCE:" + id,
                    "링크 저장 AUDIENCE:" + id);
        }

        @Test
        @DisplayName("운영진 — 계정 잠금 → 이전 링크 대체 → 새 링크 저장")
        void staffLocksAccountFirst() {
            long id = staffAccounts.active(JUNGHOON_EMAIL, "조용한고래");

            service.requestReset(staffCommand(JUNGHOON_EMAIL));

            assertThat(writeLog.writes()).containsExactly("계정 잠금 STAFF:" + id, "링크 대체 STAFF:" + id,
                    "링크 저장 STAFF:" + id);
        }

        @Test
        @DisplayName("보낼 계정이 없으면 아무것도 잠그지 않는다")
        void locksNothingWithoutAccount() {
            service.requestReset(audienceCommand("nobody@example.com"));

            assertThat(writeLog.writes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("다시 요청하면 — 쓸 수 있는 링크는 가장 최근 것 하나")
    class RequestAgain {

        @Test
        @DisplayName("10분 뒤 다시 하면 이전 링크는 그 시각으로 대체되고, 새 링크를 담은 메일이 한 통 더 간다")
        void secondRequestReplacesFirstLink() {
            audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
            service.requestReset(audienceCommand(SEOYUN_EMAIL));
            clock.advance(Duration.ofMinutes(10));

            service.requestReset(audienceCommand(SEOYUN_EMAIL));

            assertThat(tokenRepository.savedTokens()).satisfiesExactly(
                    first -> {
                        assertThat(first.value()).isEqualTo(TOKEN_VALUE);
                        assertThat(first.replacedAt()).contains(requestInstant().plus(Duration.ofMinutes(10)));
                    },
                    second -> {
                        assertThat(second.value()).isEqualTo(SECOND_TOKEN_VALUE);
                        assertThat(second.replacedAt()).isEmpty();
                        assertThat(second.issuedAt()).isEqualTo(requestInstant().plus(Duration.ofMinutes(10)));
                    });
            assertThat(emailSendPort.sentMessages()).hasSize(2);
            assertThat(emailSendPort.sentMessages().getLast().body()).contains("token=" + SECOND_TOKEN_VALUE);
        }
    }

    @Test
    @DisplayName("재설정 메일을 보내지 못하면 예외로 새 링크를 되돌린다(트랜잭션) — 부른 쪽이 로그에 남긴다")
    void mailFailureRollsBack() {
        audienceAccounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
        emailSendPort.failWith("메일 업체 응답 없음");

        assertThatIllegalStateException()
                .isThrownBy(() -> service.requestReset(audienceCommand(SEOYUN_EMAIL)))
                .withMessageContaining("되돌립니다")
                .withMessageContaining("메일 업체 응답 없음");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"ADMIN", "CHECKIN"})
    @DisplayName("관리자·검표 계정은 비밀번호 찾기를 하지 않는다 — 관객/주최자 중 고른다 (docs/13)")
    void onlyAudienceOrStaff(AccountKind kind) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RequestPasswordResetCommand(kind, EmailAddress.of(SEOYUN_EMAIL)));
    }

    private static Instant requestInstant() {
        return KoreanTime.toInstant(REQUEST_TIME);
    }

    private static RequestPasswordResetCommand audienceCommand(String email) {
        return new RequestPasswordResetCommand(AccountKind.AUDIENCE, EmailAddress.of(email));
    }

    private static RequestPasswordResetCommand staffCommand(String email) {
        return new RequestPasswordResetCommand(AccountKind.STAFF, EmailAddress.of(email));
    }
}
