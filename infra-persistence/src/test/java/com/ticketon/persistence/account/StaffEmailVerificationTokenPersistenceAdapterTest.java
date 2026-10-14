package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffEmailVerificationToken;
import com.ticketon.domain.account.StaffPersonalInformation;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V5)을 올리고 운영진 로그인 이메일 인증 링크를 저장·조회한다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영진 로그인 이메일 인증 링크 저장소 (진짜 PostgreSQL)")
class StaffEmailVerificationTokenPersistenceAdapterTest {

    private static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final PasswordHash PASSWORD_HASH = new PasswordHash("{test}hashed-password");

    private static final String TOKEN_VALUE = "staffAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private StaffEmailVerificationTokenPersistenceAdapter adapter;

    private long staffAccountId;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void close() {
        dsl.close();
    }

    @BeforeEach
    void prepareAccount() {
        dsl.truncate(STAFF_ACCOUNT).cascade().execute();
        dsl.truncate(AUDIENCE_ACCOUNT).cascade().execute();
        StaffAccount account = StaffAccount.signUp(StaffPersonalInformation.of("박정훈", "junghoon@example.com"),
                Nickname.of("조용한고래"), PASSWORD_HASH, EmailAvailability.AVAILABLE, NicknameAvailability.AVAILABLE,
                SIGNUP_TIME);
        staffAccountId = new StaffAccountPersistenceAdapter(dsl).add(account).id().getAsLong();
        adapter = new StaffEmailVerificationTokenPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("저장한 링크를 값으로 찾으면 계정·보낸 일시·끝나는 일시가 그대로다")
    void addsAndFindsToken() {
        StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(TOKEN_VALUE, staffAccountId, SIGNUP_TIME);

        adapter.add(token);

        assertThat(adapter.findByValue(TOKEN_VALUE)).contains(token);
    }

    @Test
    @DisplayName("발급한 적 없는 값이면 비어 있다")
    void unknownValueIsEmpty() {
        adapter.add(StaffEmailVerificationToken.issue(TOKEN_VALUE, staffAccountId, SIGNUP_TIME));

        assertThat(adapter.findByValue("unknownAAAAAAAAAAAAAAAAAAAAAAAAA")).isEmpty();
    }

    @Test
    @DisplayName("관객 인증 링크의 값으로는 찾지 못한다 — 두 링크는 따로 저장된다")
    void audienceTokenIsNotStaffToken() {
        AudienceAccount audienceAccount = AudienceAccount.signUp(
                AudiencePersonalInformation.of("박정훈", LocalDate.of(1990, 3, 1), "junghoon@example.com", "010-2345-6789"),
                PASSWORD_HASH, SignupConsentChoices.agreeingToAllRequired(false), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNUP_TIME);
        long audienceAccountId = new AudienceAccountPersistenceAdapter(dsl).add(audienceAccount).id().getAsLong();
        new EmailVerificationTokenPersistenceAdapter(dsl)
                .add(EmailVerificationToken.issue(TOKEN_VALUE, audienceAccountId, SIGNUP_TIME));

        assertThat(adapter.findByValue(TOKEN_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("없는 운영진 계정의 링크는 표가 받지 않는다")
    void rejectsTokenOfUnknownAccount() {
        assertThatThrownBy(() -> adapter.add(StaffEmailVerificationToken.issue(TOKEN_VALUE, staffAccountId + 1, SIGNUP_TIME)))
                .isInstanceOf(DataAccessException.class);
    }
}
