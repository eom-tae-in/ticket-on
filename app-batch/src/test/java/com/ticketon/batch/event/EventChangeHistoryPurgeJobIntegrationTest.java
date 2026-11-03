package com.ticketon.batch.event;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.AddTimedSessionUseCase;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeUseCase;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.RecordChangeHistoryFirstVersionCommand;
import com.ticketon.application.event.port.in.RecordChangeHistoryFirstVersionUseCase;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.application.event.port.in.SaveEventBasicInformationUseCase;
import com.ticketon.application.event.port.in.UploadEventPosterCommand;
import com.ticketon.application.event.port.in.UploadEventPosterUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.batch.TicketOnBatchApplication;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.TestImages;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.event.EventPosterImage;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffRole;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 배치 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)과 로컬 파일 저장소(임시 디렉터리)로 띄우고 보관 기간이 지난 행사 변경 이력 삭제 작업을 직접
 * 돌린다(#46 완료 조건 — "행사 종료 6개월 후 삭제 배치", #231 — 상세 안내 · 지도 링크 · 공개용 연락처 이력도 함께). 배치 서버는 마이그레이션을
 * 돌리지 않으므로 이 테스트만 Flyway를 켜고, 실제 시각에 맞춰 작업이 끼어들지 않게 스케줄러는 끈다. 시각은 시계를 옮겨 정한다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원이 10/15 10:00에 ○○밴드 단독공연(11/14 19:00~21:00)과 가을 낭독회(12/20 19:00~21:00)의
 * 판매를 시작했다. 판매 시작(#58)이 아직 없어 행사를 판매 대기로 바꾸는 일은 표에 바로 하고, 첫 버전은 판매 시작이 부를 유스케이스를 같은
 * 트랜잭션에서 부른다. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣는다.
 * <p>
 * 잠금이 엇갈리는 순간은 맞서는 트랜잭션을 다른 스레드에서 쥐어 둔 채 작업을 돌리고, 작업이 잠금을 기다리기 시작한 것을 PostgreSQL의 잠금 표
 * (pg_locks)로 확인한 뒤 맞서는 트랜잭션을 커밋해 본다.
 */
@SpringBootTest(classes = TicketOnBatchApplication.class, webEnvironment = WebEnvironment.NONE,
        properties = {"spring.flyway.enabled=true", "ticketon.batch.scheduling.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("보관 기간이 지난 행사 변경 이력 삭제 작업 (진짜 PostgreSQL · 로컬 저장소)")
class EventChangeHistoryPurgeJobIntegrationTest {

    private static final Instant PUBLISHED_AT = korean(2026, 10, 15, 10, 0);
    private static final Instant OPENED_AT = korean(2026, 10, 18, 14, 0);
    private static final Instant CHANGED_AT = korean(2026, 10, 20, 14, 0);
    private static final LocalDate NOVEMBER_14 = LocalDate.of(2026, 11, 14);
    private static final LocalDate DECEMBER_20 = LocalDate.of(2026, 12, 20);

    /** 11/14에 끝난 행사의 보관 마지막 날(5/14)에 작업이 도는 시각. */
    private static final Instant LAST_DAY_KEPT = korean(2027, 5, 14, 4, 0);

    /** 11/14에 끝난 행사의 6개월 + 1일(5/15)에 작업이 도는 시각. */
    private static final Instant SIX_MONTHS_AND_A_DAY = korean(2027, 5, 15, 4, 0);

    private static final String POSTER_BUNDLE = "event-posters";

    private static final MutableClock MUTABLE_CLOCK = MutableClock.at(PUBLISHED_AT);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @TempDir
    private static Path storageDirectory;

    @DynamicPropertySource
    static void localStorage(DynamicPropertyRegistry registry) {
        registry.add("ticketon.storage.local.directory", () -> storageDirectory.toString());
    }

    @TestBean(name = "clock")
    private Clock clock;

    @Autowired
    private EventChangeHistoryPurgeJob job;

    @Autowired
    private CreateEventUseCase createEventUseCase;

    @Autowired
    private ChooseSessionModeUseCase chooseSessionModeUseCase;

    @Autowired
    private AddTimedSessionUseCase addTimedSessionUseCase;

    @Autowired
    private UploadEventPosterUseCase uploadEventPosterUseCase;

    @Autowired
    private SaveEventBasicInformationUseCase saveEventBasicInformationUseCase;

    @Autowired
    private RecordChangeHistoryFirstVersionUseCase recordFirstVersionUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private long twelveMonths;
    private long junghoon;
    private long planner;

    static Clock clock() {
        return MUTABLE_CLOCK;
    }

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    @BeforeEach
    void storeOrganizer() {
        jdbcTemplate.execute("TRUNCATE organizer, staff_account, notification_send_record CASCADE");
        MUTABLE_CLOCK.moveTo(PUBLISHED_AT);
        junghoon = storeStaff("조용한고래", "junghoon@example.com");
        planner = storeStaff("푸른바람", "planner@example.com");
        twelveMonths = storeOrganizer("극단 열두달");
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("11/14에 끝난 ○○밴드는 5/14에는 이력을 남기고 6개월 + 1일인 5/15에 모두 지운다 — 12/20에 끝나는 가을 낭독회는 남긴다")
    void purgesSixMonthsAndADayAfterEventEnd() {
        long band = publishedEvent("○○밴드 단독공연", NOVEMBER_14);
        long reading = publishedEvent("가을 낭독회", DECEMBER_20);
        MUTABLE_CLOCK.moveTo(CHANGED_AT);
        saveEventBasicInformationUseCase.save(new SaveEventBasicInformationCommand(acting(planner), band, items("○○밴드 앵콜공연")));

        MUTABLE_CLOCK.moveTo(LAST_DAY_KEPT);
        job.run();
        assertThat(historyCountOf(band)).isEqualTo(4);

        MUTABLE_CLOCK.moveTo(SIX_MONTHS_AND_A_DAY);
        job.run();
        assertThat(historyCountOf(band)).isZero();
        assertThat(historyCountOf(reading)).isEqualTo(3);
    }

    @Test
    @DisplayName("판매 중에 상세 안내에 \"주차 불가\"를 넣은 ○○밴드도 5/14에는 상세 안내 이력까지 남기고, 5/15에 함께 지운다")
    void purgesVenueGuideHistoryWithOthers() {
        long band = publishedEvent("○○밴드 단독공연", NOVEMBER_14);
        jdbcTemplate.update("UPDATE event SET status = 'ON_SALE', booking_opened_at = ? WHERE id = ?", Timestamp.from(OPENED_AT), band);
        MUTABLE_CLOCK.moveTo(CHANGED_AT);
        EventBasicInformationItems items = items("○○밴드 단독공연");
        saveEventBasicInformationUseCase.save(new SaveEventBasicInformationCommand(acting(planner), band, new EventBasicInformationItems(
                items.title(), items.type(), items.categoryName(), items.genreName(), items.introduction(), items.venue(),
                "지하 1층, 주차 불가", null, null)));
        assertThat(historyTargetsOf(band)).containsExactly("TITLE", "INTRODUCTION", "POSTER", "VENUE_DETAILS");

        MUTABLE_CLOCK.moveTo(LAST_DAY_KEPT);
        job.run();
        assertThat(historyTargetsOf(band)).containsExactly("TITLE", "INTRODUCTION", "POSTER", "VENUE_DETAILS");

        MUTABLE_CLOCK.moveTo(SIX_MONTHS_AND_A_DAY);
        job.run();
        assertThat(historyCountOf(band)).isZero();
    }

    @Test
    @DisplayName("이력을 지우면 판매를 시작한 뒤 바꾼 포스터의 이전 파일도 지우고, 지금 포스터 파일은 남긴다")
    void discardsReplacedPosterFileWithHistory() {
        long band = publishedEvent("○○밴드 단독공연", NOVEMBER_14);
        String publishedPoster = posterFileKeyOf(band);
        MUTABLE_CLOCK.moveTo(CHANGED_AT);
        String changedPoster = fileKeyOf(uploadPoster(band));
        assertThat(posterFile(publishedPoster)).exists();

        MUTABLE_CLOCK.moveTo(SIX_MONTHS_AND_A_DAY);
        job.run();

        assertThat(historyCountOf(band)).isZero();
        assertThat(posterFile(publishedPoster)).doesNotExist();
        assertThat(posterFile(changedPoster)).exists();
        assertThat(posterFileKeyOf(band)).isEqualTo(changedPoster);
    }

    @Test
    @DisplayName("다시 돌아도 안전하다 — 같은 날 또, 다음 날에도 지울 것이 없다")
    void rerunIsSafe() {
        long band = publishedEvent("○○밴드 단독공연", NOVEMBER_14);
        MUTABLE_CLOCK.moveTo(SIX_MONTHS_AND_A_DAY);
        job.run();

        job.run();
        MUTABLE_CLOCK.moveTo(SIX_MONTHS_AND_A_DAY.plusSeconds(86_400));
        job.run();

        assertThat(historyCountOf(band)).isZero();
    }

    @Test
    @DisplayName("회차가 모두 취소돼 끝난 날을 알 수 없는 행사의 이력은 지우지 않는다 (확인 필요 #46)")
    void keepsHistoryOfAllCancelledEvent() {
        long band = publishedEvent("○○밴드 단독공연", NOVEMBER_14);
        jdbcTemplate.update("UPDATE event_session SET cancelled_at = ? WHERE event_id = ?", Timestamp.from(CHANGED_AT), band);

        MUTABLE_CLOCK.moveTo(korean(2030, 1, 1, 4, 0));
        job.run();

        assertThat(historyCountOf(band)).isEqualTo(3);
    }

    @Test
    @DisplayName("편집이 행사 줄을 쥐고 마지막 회차를 1/10로 옮기는 중이면 기다렸다가 옮긴 날로 판정한다 — 아직 6개월이 지나지 않아 지우지 않는다")
    void waitsForEditAndRereadsSchedule() throws Exception {
        long band = publishedEvent("○○밴드 단독공연", NOVEMBER_14);
        MUTABLE_CLOCK.moveTo(SIX_MONTHS_AND_A_DAY);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> scheduleEdit = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT id FROM event WHERE id = ? FOR UPDATE", Long.class, band);
            jdbcTemplate.update("UPDATE event_date SET calendar_date = ? WHERE event_id = ?", Date.valueOf(LocalDate.of(2027, 1, 10)),
                    band);
            held.countDown();
            awaitOrFail(release);
        }));
        awaitOrFail(held);

        Future<?> run = executor.submit(job::run);
        awaitLockWaiter();
        release.countDown();
        scheduleEdit.get(30, SECONDS);
        run.get(30, SECONDS);

        assertThat(historyCountOf(band)).isEqualTo(3);
    }

    /** 10/15 10:00에 판매를 시작한 행사 — 작성 중에 그날 저녁 회차와 포스터를 갖추고 판매 대기로 둔 뒤 첫 버전(제목 · 소개 · 포스터)을 쌓는다. */
    private long publishedEvent(String title, LocalDate date) {
        MUTABLE_CLOCK.moveTo(PUBLISHED_AT);
        long eventId = createEventUseCase.create(new CreateEventCommand(acting(planner), items(title))).eventId();
        chooseSessionModeUseCase.choose(new ChooseSessionModeCommand(acting(planner), eventId, SessionMode.TIMED, null));
        addTimedSessionUseCase.add(new AddTimedSessionCommand(acting(planner), eventId, date,
                TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0))));
        uploadPoster(eventId);
        jdbcTemplate.update("UPDATE event SET status = 'AWAITING_OPEN' WHERE id = ?", eventId);
        transactionTemplate.executeWithoutResult(status -> recordFirstVersionUseCase.record(new RecordChangeHistoryFirstVersionCommand(
                new StaffPermissionResult(twelveMonths, planner, StaffRole.OPERATOR, StaffAction.EDIT_EVENT), eventId, PUBLISHED_AT)));
        return eventId;
    }

    private EventWizardResult uploadPoster(long eventId) {
        return uploadEventPosterUseCase.upload(new UploadEventPosterCommand(acting(planner), eventId,
                EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400))));
    }

    private static String fileKeyOf(EventWizardResult result) {
        String posterUrl = result.posterUrl();
        return posterUrl.substring(posterUrl.lastIndexOf('/') + 1);
    }

    private String posterFileKeyOf(long eventId) {
        return jdbcTemplate.queryForObject("SELECT poster_file_key FROM event WHERE id = ?", String.class, eventId);
    }

    private static Path posterFile(String fileKey) {
        return storageDirectory.resolve(POSTER_BUNDLE).resolve(fileKey);
    }

    /** 그 행사의 이력 대상 칸 — 쌓은 차례. */
    private List<String> historyTargetsOf(long eventId) {
        return jdbcTemplate.queryForList("SELECT target FROM event_change_history WHERE event_id = ? ORDER BY id", String.class,
                eventId);
    }

    private int historyCountOf(long eventId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM event_change_history WHERE event_id = ?", Integer.class,
                eventId);
        assertThat(count).isNotNull();
        return count;
    }

    private ActingStaff acting(long staffAccountId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), twelveMonths);
    }

    private static EventBasicInformationItems items(String title) {
        return new EventBasicInformationItems(EventTitle.of(title), EventType.PERFORMANCE, "콘서트", null,
                "홍대 클럽에서 여는 단독공연입니다.", new Venue("○○라이브홀", "서울특별시 마포구 와우산로 1"), null, null, null);
    }

    /** 이메일 인증까지 마친 운영진 로그인 한 줄 — 표에 바로 넣는다. */
    private long storeStaff(String nickname, String email) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', '운영진', ?, ?, lower(?), '{test}hashed-password', now() - interval '30 days',
                        now() - interval '30 days')
                RETURNING id""", Long.class, nickname, email, email);
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
