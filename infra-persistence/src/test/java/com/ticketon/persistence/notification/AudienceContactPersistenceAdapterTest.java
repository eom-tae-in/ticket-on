package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.persistence.account.AudienceAccountPersistenceAdapter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
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
 * 진짜 PostgreSQL에 마이그레이션을 올리고 관객 계정의 연락처를 찾는다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관객 계정 연락처 조회 (진짜 PostgreSQL)")
class AudienceContactPersistenceAdapterTest {

    private static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private AudienceContactPersistenceAdapter adapter;

    private long accountId;

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
        dsl.truncate(AUDIENCE_ACCOUNT).cascade().execute();
        AudienceAccount account = AudienceAccount.signUp(
                AudiencePersonalInformation.of("한서윤", LocalDate.of(1998, 4, 2), "Seoyun@Example.com", "010-1234-5678"),
                new PasswordHash("{test}hashed-password"), SignupConsentChoices.agreeingToAllRequired(false),
                PhoneNumberAvailability.AVAILABLE, EmailAvailability.AVAILABLE, SIGNUP_TIME);
        accountId = new AudienceAccountPersistenceAdapter(dsl).add(account).id().getAsLong();
        adapter = new AudienceContactPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("가입 때 받은 이메일(적은 그대로)과 숫자만 남긴 전화번호를 돌려준다 — 인증 대기 계정도")
    void findsContact() {
        assertThat(adapter.findContact(accountId)).contains(
                ContactPoint.of(new EmailAddress("Seoyun@Example.com"), new PhoneNumber("01012345678")));
    }

    @Test
    @DisplayName("이용 정지된 계정도 연락처가 있다 — 거래 알림은 계속 간다")
    void suspendedAccountHasContact() {
        setStatus(AudienceAccountStatus.SUSPENDED);

        assertThat(adapter.findContact(accountId)).isPresent();
    }

    @Test
    @DisplayName("탈퇴한 계정은 찾지 않는다")
    void withdrawnAccountHasNoContact() {
        setStatus(AudienceAccountStatus.WITHDRAWN);

        assertThat(adapter.findContact(accountId)).isEmpty();
    }

    @Test
    @DisplayName("없는 계정이면 비어 있다")
    void unknownAccount() {
        assertThat(adapter.findContact(accountId + 1)).isEmpty();
    }

    private void setStatus(AudienceAccountStatus status) {
        dsl.update(AUDIENCE_ACCOUNT)
                .set(AUDIENCE_ACCOUNT.STATUS, status.name())
                .set(AUDIENCE_ACCOUNT.EMAIL_VERIFIED_AT, SIGNUP_TIME.atOffset(ZoneOffset.UTC))
                .where(AUDIENCE_ACCOUNT.ID.eq(accountId))
                .execute();
    }
}
