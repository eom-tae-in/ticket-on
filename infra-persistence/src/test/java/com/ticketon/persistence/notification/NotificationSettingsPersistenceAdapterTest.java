package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.AUDIENCE_NOTIFICATION_PREFERENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.notification.NotificationPreference;
import com.ticketon.domain.notification.NotificationSettings;
import com.ticketon.persistence.account.AudienceAccountPersistenceAdapter;
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
 * 진짜 PostgreSQL에 마이그레이션(V3)을 올리고 관객 알림 수신 설정을 저장·조회한다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관객 알림 수신 설정 저장소 (진짜 PostgreSQL)")
class NotificationSettingsPersistenceAdapterTest {

    private static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private NotificationSettingsPersistenceAdapter adapter;

    private long seoyun;

    private long jihoon;

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
    void prepareAccounts() {
        dsl.truncate(AUDIENCE_ACCOUNT).cascade().execute();
        seoyun = signUp("seoyun@example.com", "010-1234-5678");
        jihoon = signUp("jihoon@example.com", "010-8765-4321");
        adapter = new NotificationSettingsPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("바꾼 적 없는 계정은 기본값 그대로다 — 모두 받는다")
    void defaultsWithoutRows() {
        assertThat(adapter.findByAudienceAccountId(seoyun)).isEqualTo(NotificationSettings.defaultsFor(seoyun));
    }

    @Test
    @DisplayName("리마인더를 끄면 그 묶음만 저장되고, 다시 읽으면 리마인더만 꺼져 있다")
    void savesOneChoice() {
        adapter.saveChoice(seoyun, NotificationPreference.DAY_BEFORE_REMINDER, false);

        assertThat(adapter.findByAudienceAccountId(seoyun)).isEqualTo(
                NotificationSettings.defaultsFor(seoyun).change(NotificationPreference.DAY_BEFORE_REMINDER, false));
        assertThat(dsl.fetchCount(AUDIENCE_NOTIFICATION_PREFERENCE)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 묶음을 다시 바꾸면 그 줄을 고친다 — 줄이 늘지 않는다")
    void updatesSameChoice() {
        adapter.saveChoice(seoyun, NotificationPreference.BOOKING_OPEN, false);

        adapter.saveChoice(seoyun, NotificationPreference.BOOKING_OPEN, true);

        assertThat(adapter.findByAudienceAccountId(seoyun).receives(NotificationPreference.BOOKING_OPEN)).isTrue();
        assertThat(dsl.fetchCount(AUDIENCE_NOTIFICATION_PREFERENCE)).isEqualTo(1);
    }

    @Test
    @DisplayName("한 묶음을 저장해도 다른 묶음의 저장된 값은 그대로다")
    void keepsOtherChoices() {
        adapter.saveChoice(seoyun, NotificationPreference.BOOKING_CONFIRMATION, false);

        adapter.saveChoice(seoyun, NotificationPreference.DAY_BEFORE_REMINDER, false);

        NotificationSettings settings = adapter.findByAudienceAccountId(seoyun);
        assertThat(settings.receives(NotificationPreference.BOOKING_CONFIRMATION)).isFalse();
        assertThat(settings.receives(NotificationPreference.DAY_BEFORE_REMINDER)).isFalse();
        assertThat(settings.receives(NotificationPreference.BOOKING_OPEN)).isTrue();
    }

    @Test
    @DisplayName("계정 단위다 — 한 사람의 설정이 다른 사람에게 번지지 않는다")
    void perAccount() {
        adapter.saveChoice(seoyun, NotificationPreference.DAY_BEFORE_REMINDER, false);

        assertThat(adapter.findByAudienceAccountId(jihoon)).isEqualTo(NotificationSettings.defaultsFor(jihoon));
    }

    @Test
    @DisplayName("없는 계정의 설정은 표가 받지 않는다")
    void rejectsUnknownAccount() {
        assertThatThrownBy(() -> adapter.saveChoice(jihoon + 100, NotificationPreference.BOOKING_OPEN, false))
                .isInstanceOf(DataAccessException.class);
    }

    private long signUp(String email, String phoneNumber) {
        AudienceAccount account = AudienceAccount.signUp(
                AudiencePersonalInformation.of("한서윤", LocalDate.of(1998, 4, 2), email, phoneNumber),
                new PasswordHash("{test}hashed-password"), SignupConsentChoices.agreeingToAllRequired(false),
                PhoneNumberAvailability.AVAILABLE, EmailAvailability.AVAILABLE, SIGNUP_TIME);
        return new AudienceAccountPersistenceAdapter(dsl).add(account).id().getAsLong();
    }
}
