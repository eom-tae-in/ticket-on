package com.ticketon.api.event;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.event.port.in.AddSeatBlockCommand;
import com.ticketon.application.event.port.in.AddSeatBlockUseCase;
import com.ticketon.application.event.port.in.AddZoneCommand;
import com.ticketon.application.event.port.in.AddZoneUseCase;
import com.ticketon.application.event.port.in.AssignSeatsToZoneCommand;
import com.ticketon.application.event.port.in.AssignSeatsToZoneUseCase;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.EventZonesResult;
import com.ticketon.application.event.port.in.FloorPlanResult;
import com.ticketon.application.event.port.in.GetEventFloorPlanQuery;
import com.ticketon.application.event.port.in.GetEventFloorPlanUseCase;
import com.ticketon.application.event.port.in.GetEventZonesQuery;
import com.ticketon.application.event.port.in.GetEventZonesUseCase;
import com.ticketon.application.event.port.in.SeatBlockResult;
import com.ticketon.application.event.port.in.SeatResult;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneSettings;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
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
 * 행사 줄을 잠그지 않는 도면 조회(도면 전체 · 구역 목록)는 도면을 여러 문장(구역 → 좌석 → 블록 → 무대)으로 읽는다. 다른 운영진의 편집이 그 문장
 * 사이에 커밋돼도 조회가 한 시점의 도면을 읽어, 좌석 없는 블록이나 도면에 없는 구역을 가리키는 좌석을 만나 500이 되지 않는 것을 진짜
 * PostgreSQL로 고정한다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 가장 나쁜 순간을 PostgreSQL 표 잠금으로 만든다. 편집 트랜잭션이 먼저 그 표(블록 또는 좌석)를 ACCESS EXCLUSIVE로 잠가 두면, 조회는 앞의
 * 문장(구역 · 좌석)을 읽고 그 표의 SELECT에서 잠금을 기다린다(PostgreSQL의 잠금 표 pg_locks로 확인). 그동안 같은 편집 트랜잭션에서 편집
 * 유스케이스를 끝까지 돌려 커밋하면 조회가 이어 간다 — 잠금을 얻은 뒤에 문장의 스냅숏을 잡으므로, READ COMMITTED처럼 문장마다 새 스냅숏을 잡으면
 * 이어 간 SELECT가 방금 커밋된 줄을 읽어 도면이 맞지 않는다. 조회 트랜잭션이 스냅숏 하나(REPEATABLE READ — 첫 문장에서 잡는다)로 읽어야 통과한다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈, 운영자 기획 단원. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 행사는 유스케이스로 만든다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("잠그지 않는 도면 조회는 동시 편집 중에도 한 시점의 도면을 읽는다 (진짜 PostgreSQL)")
class FloorPlanSnapshotReadIntegrationTest {

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
    private AddSeatBlockUseCase addSeatBlockUseCase;

    @Autowired
    private AddZoneUseCase addZoneUseCase;

    @Autowired
    private AssignSeatsToZoneUseCase assignSeatsToZoneUseCase;

    @Autowired
    private GetEventFloorPlanUseCase getEventFloorPlanUseCase;

    @Autowired
    private GetEventZonesUseCase getEventZonesUseCase;

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
    }

    @Test
    @DisplayName("도면 전체 조회가 좌석을 읽고 블록을 읽기 직전에 다른 운영진의 블록 얹기가 커밋돼도, 조회는 얹기 전 도면을 그대로 돌려준다")
    void floorPlanReadIgnoresBlockCommittedBetweenStatements() throws Exception {
        FloorPlanResult duringAddition = readWhileAnotherFlowCommits("event_block",
                () -> getEventFloorPlanUseCase.get(new GetEventFloorPlanQuery(acting(junghoon), springPlay)),
                () -> addFirstFloor(planner));

        assertThat(duringAddition.blocks()).isEmpty();
        FloorPlanResult afterAddition = getEventFloorPlanUseCase.get(new GetEventFloorPlanQuery(acting(junghoon), springPlay));
        assertThat(afterAddition.blocks()).singleElement().satisfies(block -> assertThat(block.seats()).hasSize(50));
    }

    @Test
    @DisplayName("구역 목록 조회가 구역을 읽고 좌석을 읽기 직전에 구역 추가와 좌석 배정이 커밋돼도, 조회는 그 전 도면의 구역을 그대로 돌려준다")
    void zonesReadIgnoresZoneAndAssignmentCommittedBetweenStatements() throws Exception {
        SeatBlockResult firstFloor = addFirstFloor(junghoon);

        EventZonesResult duringAssignment = readWhileAnotherFlowCommits("event_seat",
                () -> getEventZonesUseCase.get(new GetEventZonesQuery(acting(junghoon), springPlay)),
                () -> {
                    long rSeat = addZoneUseCase.add(new AddZoneCommand(acting(planner), springPlay, ZoneKind.DESIGNATED,
                            ZoneSettings.designated(ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000)))).zoneId();
                    assignSeatsToZoneUseCase.assign(new AssignSeatsToZoneCommand(acting(planner), springPlay,
                            firstFloor.seats().stream().map(SeatResult::seatId).collect(Collectors.toSet()), rSeat));
                    return rSeat;
                });

        assertThat(duringAssignment.zones()).isEmpty();
        assertThat(getEventZonesUseCase.get(new GetEventZonesQuery(acting(junghoon), springPlay)).zones()).hasSize(1);
    }

    /**
     * 편집 트랜잭션이 그 표를 ACCESS EXCLUSIVE로 잠근 채 조회를 다른 스레드에서 부르고, 조회가 그 표의 SELECT에서 잠금을 기다리기 시작하면 같은
     * 트랜잭션에서 편집 흐름을 끝까지 돌려 커밋한다. 커밋으로 잠금이 풀리면 조회가 이어 가고, 그 결과를 돌려준다. 조회가 실패하면 그 예외로
     * 테스트가 실패한다. 편집 유스케이스는 바깥 트랜잭션에 참여하므로 잠근 표에 그대로 쓴다.
     */
    private <T> T readWhileAnotherFlowCommits(String lockedTable, Callable<T> read, Callable<?> committedMeanwhile) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> reading = new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.execute("LOCK TABLE " + lockedTable + " IN ACCESS EXCLUSIVE MODE");
                Future<T> started = executor.submit(read);
                awaitReaderWaitingOn(lockedTable);
                try {
                    committedMeanwhile.call();
                } catch (Exception exception) {
                    throw new IllegalStateException("조회 사이에 커밋할 흐름이 실패했습니다", exception);
                }
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

    /** 그 운영진이 봄 정기공연에 블록 "1층"(5행 × 10열)을 얹는다. */
    private SeatBlockResult addFirstFloor(long staffAccountId) {
        return addSeatBlockUseCase.add(new AddSeatBlockCommand(acting(staffAccountId), springPlay, BlockName.of("1층"),
                BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE));
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
