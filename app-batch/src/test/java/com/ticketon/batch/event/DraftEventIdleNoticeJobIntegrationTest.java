package com.ticketon.batch.event;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.application.event.port.in.SaveEventBasicInformationUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.batch.TicketOnBatchApplication;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.Venue;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 배치 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고 작성 중 행사 30일 미수정 알림 작업을 직접 돌린다(#45 완료 조건 — "30일 미수정 알림
 * 배치가 한 번만 보낸다"). 배치 서버는 마이그레이션을 돌리지 않으므로 이 테스트만 Flyway를 켜고, 실제 시각에 맞춰 작업이 끼어들지 않게 스케줄러는
 * 끈다. 시각은 시계를 옮겨 정한다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원이 ○○밴드 단독공연을 9/14 15:00(한국 시각)에 마지막으로 고쳤다. 알림은 운영진 전원에게
 * 이메일로 가고 발송 이력이 한 사람에 한 줄씩 남는다. 행사 · 고치기는 유스케이스로, 운영진 로그인 · 주최자 · 소속은 표에 바로 넣는다.
 * <p>
 * 잠금이 엇갈리는 순간은 맞서는 트랜잭션을 다른 스레드에서 쥐어 둔 채 작업을 돌리고, 작업이 잠금을 기다리기 시작한 것을 PostgreSQL의 잠금 표
 * (pg_locks)로 확인한 뒤 맞서는 트랜잭션을 커밋해 본다.
 */
@SpringBootTest(classes = TicketOnBatchApplication.class, webEnvironment = WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=true", "ticketon.batch.scheduling.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("작성 중 행사 30일 미수정 알림 작업 (진짜 PostgreSQL)")
class DraftEventIdleNoticeJobIntegrationTest {

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final LocalDateTime BAND_LAST_MODIFIED = LocalDateTime.of(2026, 9, 14, 15, 0);

    /** ○○밴드의 마지막 수정에서 꼭 30일 뒤 — 작업이 도는 시각. */
    private static final LocalDateTime THIRTY_DAYS_LATER = LocalDateTime.of(2026, 10, 14, 15, 0);

    private static final MutableClock MUTABLE_CLOCK = MutableClock.at(BAND_LAST_MODIFIED);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @TestBean(name = "clock")
    private Clock clock;

    @Autowired
    private DraftEventIdleNoticeJob job;

    @Autowired
    private CreateEventUseCase createEventUseCase;

    @Autowired
    private SaveEventBasicInformationUseCase saveEventBasicInformationUseCase;

    @Autowired
    private PasswordHashPort passwordHashPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private long junghoon;
    private long twelveMonths;

    static Clock clock() {
        return MUTABLE_CLOCK;
    }

    @BeforeEach
    void storeOrganizer() {
        jdbcTemplate.execute("TRUNCATE organizer, staff_account, notification_send_record CASCADE");
        MUTABLE_CLOCK.moveTo(BAND_LAST_MODIFIED);
        junghoon = storeStaff("조용한고래", "junghoon@example.com");
        long planner = storeStaff("푸른바람", "planner@example.com");
        twelveMonths = storeOrganizer("극단 열두달");
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("10/14 15:00에 돌면 30일 미수정인 ○○밴드만 알린다 — 9/15 15:00에 고친 가을 낭독회(29일)는 아직이다. 소유자 · 운영자에게 한 통씩")
    void notifiesThirtyDaysButNotTwentyNine() {
        long band = createEvent("○○밴드 단독공연");
        MUTABLE_CLOCK.moveTo(BAND_LAST_MODIFIED.plusDays(1));
        long reading = createEvent("가을 낭독회");
        MUTABLE_CLOCK.moveTo(THIRTY_DAYS_LATER);

        job.run();

        assertThat(noticesOf(band)).containsExactly(at(BAND_LAST_MODIFIED));
        assertThat(noticesOf(reading)).isEmpty();
        assertThat(idleMails()).containsExactlyInAnyOrder("EMAIL junghoon@example.com true", "EMAIL planner@example.com true");
    }

    @Test
    @DisplayName("알린 뒤 다시 돌아도 — 같은 시각에 또, 다음 날, 한 달 뒤에도 — 같은 미수정 구간에는 보내지 않는다")
    void doesNotNotifyTwiceForSameIdlePeriod() {
        long band = createEvent("○○밴드 단독공연");
        MUTABLE_CLOCK.moveTo(THIRTY_DAYS_LATER);
        job.run();

        job.run();
        MUTABLE_CLOCK.moveTo(THIRTY_DAYS_LATER.plusDays(1));
        job.run();
        MUTABLE_CLOCK.moveTo(THIRTY_DAYS_LATER.plusDays(31));
        job.run();

        assertThat(noticesOf(band)).hasSize(1);
        assertThat(idleMails()).hasSize(2);
    }

    @Test
    @DisplayName("알린 뒤 10/20에 고치면 새 구간이 시작된다 — 11/18에는 보내지 않고, 30일이 지난 11/19에 다시 보낸다")
    void notifiesAgainAfterEditingAndThirtyMoreDays() {
        long band = createEvent("○○밴드 단독공연");
        MUTABLE_CLOCK.moveTo(THIRTY_DAYS_LATER);
        job.run();
        LocalDateTime editedAt = LocalDateTime.of(2026, 10, 20, 15, 0);
        MUTABLE_CLOCK.moveTo(editedAt);
        saveEventBasicInformationUseCase.save(new SaveEventBasicInformationCommand(acting(junghoon), band,
                information("○○밴드 단독공연 — 앵콜")));

        MUTABLE_CLOCK.moveTo(LocalDateTime.of(2026, 11, 18, 15, 0));
        job.run();
        assertThat(idleMails()).hasSize(2);

        MUTABLE_CLOCK.moveTo(LocalDateTime.of(2026, 11, 19, 15, 0));
        job.run();
        assertThat(idleMails()).hasSize(4);
        assertThat(noticesOf(band)).containsExactly(at(BAND_LAST_MODIFIED), at(editedAt));
    }

    @Test
    @DisplayName("두 벌이 동시에 돌아도 한 번만 — 다른 트랜잭션이 같은 구간의 기록을 막 넣는 중이면 기다렸다가 보내지 않는다")
    void concurrentRunsNotifyOnce() throws Exception {
        long band = createEvent("○○밴드 단독공연");
        MUTABLE_CLOCK.moveTo(THIRTY_DAYS_LATER);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> otherRun = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO draft_event_idle_notice (event_id, last_modified_at, notified_at) VALUES (?, ?, ?)",
                    band, timestamp(BAND_LAST_MODIFIED), timestamp(THIRTY_DAYS_LATER));
            held.countDown();
            awaitOrFail(release);
        }));
        awaitOrFail(held);

        Future<?> run = executor.submit(job::run);
        awaitLockWaiter();
        release.countDown();
        otherRun.get(30, SECONDS);
        run.get(30, SECONDS);

        assertThat(noticesOf(band)).hasSize(1);
        assertThat(idleMails()).isEmpty();
    }

    @Test
    @DisplayName("위저드 저장이 행사 줄을 쥐고 있으면 기다렸다가 바뀐 마지막 수정 시각으로 판정한다 — 막 고친 행사에는 보내지 않는다")
    void waitsForWizardSaveAndRereads() throws Exception {
        long band = createEvent("○○밴드 단독공연");
        MUTABLE_CLOCK.moveTo(THIRTY_DAYS_LATER);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> wizardSave = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT id FROM event WHERE id = ? FOR UPDATE", Long.class, band);
            jdbcTemplate.update("UPDATE event SET last_modified_at = ? WHERE id = ?", timestamp(THIRTY_DAYS_LATER.minusMinutes(1)),
                    band);
            held.countDown();
            awaitOrFail(release);
        }));
        awaitOrFail(held);

        Future<?> run = executor.submit(job::run);
        awaitLockWaiter();
        release.countDown();
        wizardSave.get(30, SECONDS);
        run.get(30, SECONDS);

        assertThat(noticesOf(band)).isEmpty();
        assertThat(idleMails()).isEmpty();
    }

    @Test
    @DisplayName("한 행사의 메일이 실패하면 그 행사만 되돌려 기록이 남지 않고(다음에 다시 시도), 다른 행사는 보낸 뒤 실패를 알린다")
    void failureRollsBackOnlyThatEvent() {
        long band = createEvent("○○밴드 단독공연");
        long broken = createEvent("주인 없는 행사");
        long ownerless = storeOrganizer("소유자 없는 극단");
        jdbcTemplate.update("UPDATE event SET organizer_id = ? WHERE id = ?", ownerless, broken);
        MUTABLE_CLOCK.moveTo(THIRTY_DAYS_LATER);

        assertThatIllegalStateException().isThrownBy(job::run).withMessageContaining("행사 [" + broken + "]");

        assertThat(noticesOf(band)).hasSize(1);
        assertThat(noticesOf(broken)).isEmpty();
        assertThat(idleMails()).hasSize(2);
        assertThatIllegalStateException().isThrownBy(job::run).withMessageContaining("행사 [" + broken + "]");
        assertThat(idleMails()).hasSize(2);
    }

    /** 운영진 로그인 박정훈으로 열두달에서 행사를 만든다 — 지금 시계가 마지막 수정 시각이 된다. */
    private long createEvent(String title) {
        return createEventUseCase.create(new CreateEventCommand(acting(junghoon), information(title))).eventId();
    }

    private ActingStaff acting(long staffAccountId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), twelveMonths);
    }

    private static EventBasicInformationItems information(String title) {
        return new EventBasicInformationItems(EventTitle.of(title), EventType.PERFORMANCE, "콘서트", null,
                "홍대 클럽에서 여는 단독공연입니다.", new Venue("○○라이브홀", "서울특별시 마포구 와우산로 1"), null, null, null);
    }

    /** 그 행사에 알린 구간(마지막 수정 시각) — 이른 것부터. */
    private List<Instant> noticesOf(long eventId) {
        return jdbcTemplate.queryForList("SELECT last_modified_at FROM draft_event_idle_notice WHERE event_id = ? ORDER BY last_modified_at",
                OffsetDateTime.class, eventId).stream().map(OffsetDateTime::toInstant).toList();
    }

    /** 작성 중 행사 30일 미수정 메일의 발송 이력 — "수단 받는 곳 성공". */
    private List<String> idleMails() {
        return jdbcTemplate.queryForList("""
                SELECT channel || ' ' || recipient_address || ' ' || succeeded FROM notification_send_record
                WHERE kind = 'DRAFT_EVENT_IDLE' ORDER BY id""", String.class);
    }

    private static Instant at(LocalDateTime koreanDateTime) {
        return KoreanTime.toInstant(koreanDateTime);
    }

    private static Timestamp timestamp(LocalDateTime koreanDateTime) {
        return Timestamp.from(at(koreanDateTime));
    }

    /** 이메일 인증까지 마친 운영진 로그인 한 줄 — 표에 바로 넣는다. */
    private long storeStaff(String nickname, String email) {
        String passwordHash = passwordHashPort.hash(new NewPassword(STAFF_PASSWORD)).value();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', '운영진', ?, ?, lower(?), ?, now() - interval '30 days', now() - interval '30 days')
                RETURNING id""", Long.class, nickname, email, email, passwordHash);
        assertThat(id).isNotNull();
        return id;
    }

    /** 활동 중인 주최자 한 줄 — 신청 · 승인을 거치지 않고 표에 바로 넣는다. */
    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    /** 지금 소속 한 줄. */
    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '30 days')""", organizerId, staffAccountId, role);
    }

    /** 다른 트랜잭션의 잠금을 기다리는 연결이 생길 때까지 기다린다 — 작업이 잠금에서 멈춘 것. */
    private void awaitLockWaiter() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Long waiters = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
            if (waiters != null && waiters > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("작업이 10초 안에 잠금을 기다리지 않았습니다");
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(30, SECONDS)) {
                throw new AssertionError("기다리는 신호가 30초 안에 오지 않았습니다");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("기다리다 끊겼습니다", interrupted);
        }
    }
}
