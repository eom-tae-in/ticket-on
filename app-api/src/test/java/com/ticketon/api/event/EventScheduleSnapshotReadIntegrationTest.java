package com.ticketon.api.event;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.AddTimedSessionUseCase;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeUseCase;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.port.in.GetEventScheduleQuery;
import com.ticketon.application.event.port.in.GetEventScheduleUseCase;
import com.ticketon.application.event.port.in.ScheduleDateResult;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Venue;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 일정 조회는 행사 줄을 잠그지 않고 여러 문장(일정 → 회차 → 날짜)으로 읽는다. 그 사이에 다른 운영진의 회차 추가가 커밋돼도 조회가 한 시점의 일정을
 * 돌려주는지 진짜 PostgreSQL로 고정한다 — 문장마다 새 스냅숏을 잡으면(READ COMMITTED) 회차를 읽은 뒤 커밋된 새 날짜만 읽혀 회차 없는 날짜가 되고,
 * 일정을 되살리지 못해 500이 된다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 가장 나쁜 순간을 PostgreSQL 표 잠금으로 만든다({@link FloorPlanSnapshotReadIntegrationTest}와 같은 방식). 편집 트랜잭션이 먼저 날짜 표를 ACCESS
 * EXCLUSIVE로 잠가 두면, 조회는 일정 · 회차를 읽고 날짜 표의 SELECT에서 잠금을 기다린다(pg_locks로 확인). 그동안 같은 편집 트랜잭션에서 회차 추가를
 * 끝까지 돌려 커밋하면 조회가 이어 간다. 조회 트랜잭션이 스냅숏 하나(REPEATABLE READ)로 읽어야 통과한다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈, 운영자 기획 단원. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 행사 · 일정은 유스케이스로 만든다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("잠그지 않는 일정 조회는 동시 편집 중에도 한 시점의 일정을 읽는다 (진짜 PostgreSQL)")
class EventScheduleSnapshotReadIntegrationTest {

    private static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);
    private static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CreateEventUseCase createEventUseCase;

    @Autowired
    private ChooseSessionModeUseCase chooseSessionModeUseCase;

    @Autowired
    private AddTimedSessionUseCase addTimedSessionUseCase;

    @Autowired
    private GetEventScheduleUseCase getEventScheduleUseCase;

    private long twelveMonths;
    private long junghoon;
    private long planner;
    private long springPlay;

    @BeforeEach
    void storeTwelveMonths() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoon = storeStaff("박정훈", "고래" + suffix, "junghoon-" + suffix + "@example.com");
        planner = storeStaff("김기획", "바람" + suffix, "planner-" + suffix + "@example.com");
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        springPlay = createEventUseCase.create(new CreateEventCommand(acting(junghoon), springPlayItems())).eventId();
        chooseSessionModeUseCase.choose(new ChooseSessionModeCommand(acting(junghoon), springPlay, SessionMode.TIMED, null));
        addEvening(junghoon, MARCH_14);
    }

    @Test
    @DisplayName("일정 조회가 회차를 읽고 날짜를 읽기 직전에 다른 운영진의 3/15 회차 추가가 커밋돼도, 조회는 추가 전 일정을 그대로 돌려준다")
    void scheduleReadIgnoresSessionCommittedBetweenStatements() throws Exception {
        EventScheduleResult duringAddition = readWhileAnotherFlowCommits("event_date",
                () -> getEventScheduleUseCase.get(new GetEventScheduleQuery(acting(junghoon), springPlay)),
                () -> addEvening(planner, MARCH_15));

        assertThat(duringAddition.dates()).extracting(ScheduleDateResult::date).containsExactly(MARCH_14);
        EventScheduleResult afterAddition = getEventScheduleUseCase.get(new GetEventScheduleQuery(acting(junghoon), springPlay));
        assertThat(afterAddition.dates()).extracting(ScheduleDateResult::date).containsExactly(MARCH_14, MARCH_15);
    }

    /**
     * 편집 트랜잭션이 그 표를 ACCESS EXCLUSIVE로 잠근 채 조회를 다른 스레드에서 부르고, 조회가 그 표의 SELECT에서 잠금을 기다리기 시작하면 같은
     * 트랜잭션에서 편집 흐름을 끝까지 돌려 커밋한다. 커밋으로 잠금이 풀리면 조회가 이어 가고, 그 결과를 돌려준다. 조회가 실패하면 그 예외로
     * 테스트가 실패한다. 편집 유스케이스는 바깥 트랜잭션에 참여하므로 잠근 표에 그대로 쓴다.
     */
    private <T> T readWhileAnotherFlowCommits(String lockedTable, Callable<T> read, Runnable committedMeanwhile) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> reading = new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.execute("LOCK TABLE " + lockedTable + " IN ACCESS EXCLUSIVE MODE");
                Future<T> started = executor.submit(read);
                awaitReaderWaitingOn(lockedTable);
                committedMeanwhile.run();
                return started;
            });
            assertThat(reading).isNotNull();
            return reading.get(30, SECONDS);
        } finally {
            executor.shutdown();
        }
    }

    /** 다른 연결이 그 표의 잠금을 기다리기 시작할 때까지 기다린다 — 조회가 그 표의 SELECT에서 멈춘 것. */
    private void awaitReaderWaitingOn(String table) {
        try {
            for (int attempt = 0; attempt < 200; attempt++) {
                Long waiters = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_locks WHERE NOT granted AND relation = ?::regclass", Long.class, table);
                if (waiters != null && waiters > 0) {
                    return;
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("조회가 잠금을 기다리는지 보다가 끊겼습니다", exception);
        }
        throw new AssertionError("조회가 10초 안에 " + table + " 잠금을 기다리지 않았습니다");
    }

    /** 그 운영진이 봄 정기공연에 그 날짜 19:00~21:00 회차를 더한다. */
    private void addEvening(long staffAccountId, LocalDate date) {
        addTimedSessionUseCase.add(new AddTimedSessionCommand(acting(staffAccountId), springPlay, date,
                TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0))));
    }

    private ActingStaff acting(long staffAccountId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), twelveMonths);
    }

    private static EventBasicInformationItems springPlayItems() {
        return new EventBasicInformationItems(EventTitle.of("2026 봄 정기공연 〈열두 번째 달〉"), EventType.PERFORMANCE, "연극", null,
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), null, null,
                null);
    }

    /** 이메일 인증을 마친 운영진 로그인 한 줄 — 이 테스트는 로그인하지 않아 비밀번호 해시는 아무 값이다. */
    private long storeStaff(String name, String nickname, String email) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', ?, ?, ?, lower(?), '{test}hashed-password', now() - interval '30 days',
                        now() - interval '30 days')
                RETURNING id""", Long.class, name, nickname, email, email);
        assertThat(id).isNotNull();
        return id;
    }

    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '10 days')""", organizerId, staffAccountId, role);
    }
}
