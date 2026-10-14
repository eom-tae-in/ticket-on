package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.AUDIENCE_NOTIFICATION_INBOX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.notification.InboxNotification;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.persistence.account.AudienceAccountPersistenceAdapter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
 * 진짜 PostgreSQL에 마이그레이션(V4)을 올리고 관객 알림함을 쌓고·읽고·처음 본 시각을 저장한다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관객 알림함 저장소 (진짜 PostgreSQL)")
class NotificationInboxPersistenceAdapterTest {

    private static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));
    private static final Instant BOOKED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 12, 0));
    private static final Instant CANCELLED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 9, 0));
    private static final Instant FIRST_OPENED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 18, 0));
    private static final Instant SECOND_OPENED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 18, 1));

    private static final NotificationContent BOOKING_CONTENT = new NotificationContent("예매가 완료되었습니다", "티켓 링크");
    private static final NotificationContent CANCELLATION_CONTENT =
            new NotificationContent("회차가 취소되었습니다", "다른 회차 예매하기");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private NotificationInboxPersistenceAdapter adapter;

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
        adapter = new NotificationInboxPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("쌓은 알림을 최신순으로 읽고, 제목·본문·종류·보낸 시각이 그대로이며 새로 온 것이다")
    void addsAndReadsLatestFirst() {
        adapter.add(keptFor(seoyun, NotificationKind.BOOKING_COMPLETED, BOOKING_CONTENT, BOOKED_AT));
        adapter.add(keptFor(seoyun, NotificationKind.SESSION_CANCELLED, CANCELLATION_CONTENT, CANCELLED_AT));

        List<InboxNotification> inbox = adapter.findByAudienceAccountId(seoyun);

        assertThat(inbox).extracting(InboxNotification::kind)
                .containsExactly(NotificationKind.SESSION_CANCELLED, NotificationKind.BOOKING_COMPLETED);
        InboxNotification latest = inbox.getFirst();
        assertThat(latest.id()).isPresent();
        assertThat(latest.audienceAccountId()).isEqualTo(seoyun);
        assertThat(latest.content()).isEqualTo(CANCELLATION_CONTENT);
        assertThat(latest.sentAt()).isEqualTo(CANCELLED_AT);
        assertThat(inbox).allSatisfy(notification -> assertThat(notification.isNew()).isTrue());
    }

    @Test
    @DisplayName("보낸 시각이 같으면 나중에 쌓인 것이 먼저다")
    void sameSentAtLatestAddedFirst() {
        adapter.add(keptFor(seoyun, NotificationKind.BOOKING_COMPLETED, BOOKING_CONTENT, BOOKED_AT));
        adapter.add(keptFor(seoyun, NotificationKind.SESSION_CANCELLED, CANCELLATION_CONTENT, BOOKED_AT));

        assertThat(adapter.findByAudienceAccountId(seoyun)).extracting(InboxNotification::kind)
                .containsExactly(NotificationKind.SESSION_CANCELLED, NotificationKind.BOOKING_COMPLETED);
    }

    @Test
    @DisplayName("처음 본 시각을 저장하면 다시 읽을 때 새로 온 것이 아니다")
    void savesFirstSeen() {
        adapter.add(keptFor(seoyun, NotificationKind.BOOKING_COMPLETED, BOOKING_CONTENT, BOOKED_AT));
        List<InboxNotification> opened = adapter.findByAudienceAccountId(seoyun);
        opened.forEach(notification -> notification.markSeen(FIRST_OPENED_AT));

        adapter.saveFirstSeen(opened);

        assertThat(adapter.findByAudienceAccountId(seoyun)).singleElement().satisfies(notification -> {
            assertThat(notification.isNew()).isFalse();
            assertThat(notification.firstSeenAt()).contains(FIRST_OPENED_AT);
        });
    }

    @Test
    @DisplayName("거의 동시에 두 번 열어도 먼저 저장한 처음 본 시각이 남는다")
    void firstSaveWins() {
        adapter.add(keptFor(seoyun, NotificationKind.BOOKING_COMPLETED, BOOKING_CONTENT, BOOKED_AT));
        List<InboxNotification> firstOpen = adapter.findByAudienceAccountId(seoyun);
        List<InboxNotification> secondOpen = adapter.findByAudienceAccountId(seoyun);
        firstOpen.forEach(notification -> notification.markSeen(FIRST_OPENED_AT));
        secondOpen.forEach(notification -> notification.markSeen(SECOND_OPENED_AT));

        adapter.saveFirstSeen(firstOpen);
        adapter.saveFirstSeen(secondOpen);

        assertThat(adapter.findByAudienceAccountId(seoyun)).singleElement()
                .satisfies(notification -> assertThat(notification.firstSeenAt()).contains(FIRST_OPENED_AT));
    }

    @Test
    @DisplayName("계정마다 따로다 — 한 사람의 알림함을 열어도 다른 사람의 알림은 새로 온 것 그대로다")
    void perAccount() {
        adapter.add(keptFor(seoyun, NotificationKind.BOOKING_COMPLETED, BOOKING_CONTENT, BOOKED_AT));
        adapter.add(keptFor(jihoon, NotificationKind.BOOKING_COMPLETED, BOOKING_CONTENT, BOOKED_AT));
        List<InboxNotification> seoyunInbox = adapter.findByAudienceAccountId(seoyun);
        seoyunInbox.forEach(notification -> notification.markSeen(FIRST_OPENED_AT));

        adapter.saveFirstSeen(seoyunInbox);

        assertThat(adapter.findByAudienceAccountId(jihoon)).singleElement()
                .satisfies(notification -> assertThat(notification.isNew()).isTrue());
        assertThat(dsl.fetchCount(AUDIENCE_NOTIFICATION_INBOX)).isEqualTo(2);
    }

    @Test
    @DisplayName("저장할 것이 없으면 아무것도 하지 않는다")
    void savesNothing() {
        adapter.saveFirstSeen(List.of());

        assertThat(adapter.findByAudienceAccountId(seoyun)).isEmpty();
    }

    @Test
    @DisplayName("없는 계정의 알림함 줄은 표가 받지 않는다")
    void rejectsUnknownAccount() {
        assertThatThrownBy(() -> adapter.add(
                keptFor(jihoon + 100, NotificationKind.BOOKING_COMPLETED, BOOKING_CONTENT, BOOKED_AT)))
                .isInstanceOf(DataAccessException.class);
    }

    private static InboxNotification keptFor(long audienceAccountId, NotificationKind kind, NotificationContent content,
            Instant sentAt) {
        return InboxNotification.keptFor(NotificationRecipient.audience(audienceAccountId), kind, content, sentAt)
                .orElseThrow();
    }

    private long signUp(String email, String phoneNumber) {
        AudienceAccount account = AudienceAccount.signUp(
                AudiencePersonalInformation.of("한서윤", LocalDate.of(1998, 4, 2), email, phoneNumber),
                new PasswordHash("{test}hashed-password"), SignupConsentChoices.agreeingToAllRequired(false),
                PhoneNumberAvailability.AVAILABLE, EmailAvailability.AVAILABLE, SIGNUP_TIME);
        return new AudienceAccountPersistenceAdapter(dsl).add(account).id().getAsLong();
    }
}
