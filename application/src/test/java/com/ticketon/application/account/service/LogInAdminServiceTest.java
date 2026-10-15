package com.ticketon.application.account.service;

import static com.ticketon.application.account.service.FakePasswordHashPort.Comparison.MATCHES;
import static com.ticketon.application.account.service.FakePasswordHashPort.Comparison.SIMULATED_MATCHING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.LogInAdminCommand;
import com.ticketon.application.account.port.in.LogInAdminResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 로그인 유스케이스")
class LogInAdminServiceTest {

    private static final String RAW_PASSWORD = "dohyun-admin-1234";

    private static final String DOHYUN_EMAIL = "dohyun@ticket-on.example";

    private static final Instant ISSUE_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private final FakeAdminAccountRepository accountRepository = new FakeAdminAccountRepository();
    private final FakePasswordHashPort passwordHashPort = new FakePasswordHashPort();

    private final LogInAdminService service = new LogInAdminService(accountRepository, passwordHashPort);

    @Nested
    @DisplayName("로그인된다")
    class Succeeds {

        @Test
        @DisplayName("최고 관리자는 맞는 이메일·비밀번호로 로그인하고, 관리자 주체(ADMIN:id)와 등급 SUPER_ADMIN을 돌려받는다")
        void superAdminLogsIn() {
            long id = stored(DOHYUN_EMAIL, AdminGrade.SUPER_ADMIN);

            LogInAdminResult result = service.logIn(command(DOHYUN_EMAIL, RAW_PASSWORD));

            assertThat(result.account()).isEqualTo(AccountRef.of(AccountKind.ADMIN, id));
            assertThat(result.account().principalName()).isEqualTo("ADMIN:" + id);
            assertThat(result.grade()).isEqualTo(AdminGrade.SUPER_ADMIN);
        }

        @Test
        @DisplayName("관리자는 등급 ADMIN을 돌려받는다")
        void adminLogsIn() {
            long id = stored("admin@ticket-on.example", AdminGrade.ADMIN);

            LogInAdminResult result = service.logIn(command("admin@ticket-on.example", RAW_PASSWORD));

            assertThat(result.account()).isEqualTo(AccountRef.of(AccountKind.ADMIN, id));
            assertThat(result.grade()).isEqualTo(AdminGrade.ADMIN);
        }

        @Test
        @DisplayName("이메일의 대소문자는 가리지 않는다")
        void emailIgnoresCase() {
            long id = stored(DOHYUN_EMAIL, AdminGrade.SUPER_ADMIN);

            LogInAdminResult result = service.logIn(command("DOHYUN@Ticket-On.example", RAW_PASSWORD));

            assertThat(result.account().id()).isEqualTo(id);
        }
    }

    @Nested
    @DisplayName("로그인되지 않는다 — 까닭과 상관없이 같은 코드·문구 (계정이 있는지 드러나지 않게)")
    class Fails {

        @Test
        @DisplayName("그 이메일의 관리자 계정이 없음")
        void unknownEmail() {
            stored(DOHYUN_EMAIL, AdminGrade.SUPER_ADMIN);

            assertLoginFailed(() -> service.logIn(command("other@ticket-on.example", RAW_PASSWORD)));
        }

        @Test
        @DisplayName("비밀번호가 틀림")
        void wrongPassword() {
            stored(DOHYUN_EMAIL, AdminGrade.SUPER_ADMIN);

            assertLoginFailed(() -> service.logIn(command(DOHYUN_EMAIL, "dohyun-admin-12345")));
        }

        @Test
        @DisplayName("비밀번호의 앞뒤 공백을 걷지 않는다 — 적은 그대로 비교한다")
        void passwordIsNotStripped() {
            stored(DOHYUN_EMAIL, AdminGrade.SUPER_ADMIN);

            assertLoginFailed(() -> service.logIn(command(DOHYUN_EMAIL, " " + RAW_PASSWORD)));
        }

        @Test
        @DisplayName("같은 이메일의 운영진 로그인만 있으면 관리자로는 로그인되지 않는다 — 다른 종류의 계정이다")
        void staffAccountDoesNotLogInAsAdmin() {
            new StoredStaffAccounts(new FakeStaffAccountRepository()).active(DOHYUN_EMAIL, "조용한고래");

            assertLoginFailed(() -> service.logIn(command(DOHYUN_EMAIL, StoredStaffAccounts.RAW_PASSWORD)));
        }

        private void assertLoginFailed(ThrowingCallable logIn) {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(logIn)
                    .satisfies(exception -> {
                        assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.ADMIN_LOGIN_FAILED);
                        assertThat(exception.errorCode().message()).isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다.");
                    });
        }
    }

    @Nested
    @DisplayName("응답 시간을 같게 — 어느 경우에나 해시 비교를 꼭 한 번 한다 (관객·운영진 로그인과 같은 방식)")
    class EqualComparisonCost {

        @Test
        @DisplayName("맞는 비밀번호도 틀린 비밀번호도 저장된 해시와 비교는 한 번이다")
        void existingAccountComparesOnce() {
            stored(DOHYUN_EMAIL, AdminGrade.SUPER_ADMIN);

            service.logIn(command(DOHYUN_EMAIL, RAW_PASSWORD));
            attemptLogIn(DOHYUN_EMAIL, "dohyun-admin-12345");

            assertThat(passwordHashPort.comparisons()).containsExactly(MATCHES, MATCHES);
        }

        @Test
        @DisplayName("그 이메일의 관리자 계정이 없으면 비교할 해시가 없어도 같은 만큼 시간을 들이는 비교를 꼭 한 번 한다")
        void unknownEmailSimulatesComparisonOnce() {
            stored(DOHYUN_EMAIL, AdminGrade.SUPER_ADMIN);

            attemptLogIn("other@ticket-on.example", RAW_PASSWORD);

            assertThat(passwordHashPort.comparisons()).containsExactly(SIMULATED_MATCHING);
        }

        /** 로그인을 시도한다. 막혔는지는 위 Fails가 보므로 여기서는 해시 비교만 본다. */
        private void attemptLogIn(String email, String rawPassword) {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> service.logIn(command(email, rawPassword)));
        }
    }

    private long stored(String email, AdminGrade grade) {
        return accountRepository.store(email, FakePasswordHashPort.hashOf(RAW_PASSWORD), grade, ISSUE_TIME);
    }

    private static LogInAdminCommand command(String email, String rawPassword) {
        return new LogInAdminCommand(EmailAddress.of(email), new EnteredPassword(rawPassword));
    }
}
