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
import com.ticketon.application.event.port.in.DuplicateEventCommand;
import com.ticketon.application.event.port.in.DuplicateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.FloorPlanResult;
import com.ticketon.application.event.port.in.GetEventFloorPlanQuery;
import com.ticketon.application.event.port.in.GetEventFloorPlanUseCase;
import com.ticketon.application.event.port.in.SeatBlockResult;
import com.ticketon.application.event.port.in.SeatResult;
import com.ticketon.application.event.port.in.ZoneResult;
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
import java.util.concurrent.CountDownLatch;
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
 * 행사 복제는 원본의 도면을 여러 문장(구역 → 좌석 → 블록 → 무대)으로 읽는다. 원본 행사 줄에 외래 키 확인 잠금(FOR KEY SHARE)을 걸어, 읽는 동안
 * 다른 운영진의 원본 편집(행사 줄 FOR UPDATE)이 커밋되지 않고 한 시점의 도면을 복사하는 것을 진짜 PostgreSQL로 고정한다. Docker가 없는 환경에서는
 * 건너뛴다.
 * <ul>
 *   <li><b>복제가 먼저</b> — 복제가 구역을 읽고 좌석 표에서 멈춘 사이(다른 트랜잭션이 그 표를 ACCESS EXCLUSIVE로 잠가 만든다) 다른 운영진이 원본에
 *       구역을 더하고 좌석을 칠하려 하면, 편집은 원본 행사 줄에서 복제가 끝나기를 기다린다(PostgreSQL의 잠금 표 pg_locks에서 행 잠금 대기로
 *       본다). 잠그지 않으면 편집은 행사 줄을 바로 잡고 좌석 표에서 기다리다, 표 잠금이 풀리면 복제와 겨뤄 복제가 도면에 없는 구역을 가리키는
 *       좌석을 읽을 수 있다(도면을 되살리지 못해 500).</li>
 *   <li><b>편집이 먼저</b> — 원본을 고치는 중(행사 줄 FOR UPDATE를 쥔 채 커밋 전)에 복제하면 복제는 그 편집이 커밋되기를 기다렸다가 고친 원본을
 *       복사한다.</li>
 * </ul>
 * 극단 열두달(docs/15)의 소유자 박정훈, 운영자 기획 단원. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 행사 · 도면 · 복제는 유스케이스로 한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 복제는 원본을 읽는 동안 원본 편집을 막고 한 시점의 도면을 복사한다 (진짜 PostgreSQL)")
class EventDuplicationRaceIntegrationTest {

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
    private DuplicateEventUseCase duplicateEventUseCase;

    @Autowired
    private GetEventFloorPlanUseCase getEventFloorPlanUseCase;

    private long twelveMonths;
    private long junghoon;
    private long planner;
    private long springPlay;
    private SeatBlockResult firstFloor;

    @BeforeEach
    void storeTwelveMonths() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoon = storeStaff("박정훈", "고래" + suffix, "junghoon-" + suffix + "@example.com");
        planner = storeStaff("김기획", "바람" + suffix, "planner-" + suffix + "@example.com");
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        springPlay = createEventUseCase.create(new CreateEventCommand(acting(junghoon), springPlayItems())).eventId();
        firstFloor = addSeatBlockUseCase.add(new AddSeatBlockCommand(acting(junghoon), springPlay, BlockName.of("1층"),
                BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE));
    }

    @Test
    @DisplayName("복제가 원본 도면을 읽는 사이 다른 운영진이 구역을 더하고 좌석을 칠하려 하면, 편집은 복제가 끝날 때까지 기다리고 복제본은 편집 전 도면이다")
    void editWaitsForDuplicationInProgress() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Flows flows = new TransactionTemplate(transactionManager).execute(status -> {
                jdbcTemplate.execute("LOCK TABLE event_seat IN ACCESS EXCLUSIVE MODE");
                Future<Long> duplication = executor.submit(() -> duplicateEventUseCase.duplicate(
                        new DuplicateEventCommand(acting(junghoon), springPlay)).eventId());
                awaitWaiterOn("relation = 'event_seat'::regclass");
                Future<Long> edit = executor.submit(this::addRSeatAndPaintFirstFloor);
                awaitWaiterOn("locktype = 'transactionid'");
                return new Flows(duplication, edit);
            });
            assertThat(flows).isNotNull();

            long copyId = flows.duplication().get(30, SECONDS);
            long rSeat = flows.edit().get(30, SECONDS);

            FloorPlanResult copy = floorPlanOf(copyId);
            assertThat(copy.zones()).isEmpty();
            assertThat(copy.blocks()).singleElement().satisfies(block -> {
                assertThat(block.seats()).hasSize(50);
                assertThat(block.seats()).extracting(SeatResult::zoneId).containsOnlyNulls();
            });
            FloorPlanResult original = floorPlanOf(springPlay);
            assertThat(original.zones()).extracting(ZoneResult::zoneId).containsExactly(rSeat);
            assertThat(original.blocks().getFirst().seats()).extracting(SeatResult::zoneId).containsOnly(rSeat);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("다른 운영진이 원본을 고치는 중에 복제하면, 복제는 그 편집이 커밋되기를 기다렸다가 고친 원본을 복사한다")
    void duplicationWaitsForEditInProgress() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch edited = new CountDownLatch(1);
        CountDownLatch commitEdit = new CountDownLatch(1);
        try {
            Future<Long> edit = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                long rSeat = addRSeatAndPaintFirstFloor();
                edited.countDown();
                awaitLatch(commitEdit);
                return rSeat;
            }));
            assertThat(edited.await(30, SECONDS)).isTrue();
            Future<Long> duplication = executor.submit(() -> duplicateEventUseCase.duplicate(
                    new DuplicateEventCommand(acting(junghoon), springPlay)).eventId());
            awaitWaiterOn("locktype = 'transactionid'");
            commitEdit.countDown();

            long rSeat = edit.get(30, SECONDS);
            FloorPlanResult copy = floorPlanOf(duplication.get(30, SECONDS));

            assertThat(copy.zones()).singleElement().satisfies(zone -> {
                assertThat(zone.name()).isEqualTo("R석");
                assertThat(zone.zoneId()).isNotEqualTo(rSeat);
            });
            long copiedRSeat = copy.zones().getFirst().zoneId();
            assertThat(copy.blocks().getFirst().seats()).hasSize(50).extracting(SeatResult::zoneId).containsOnly(copiedRSeat);
        } finally {
            commitEdit.countDown();
            executor.shutdownNow();
        }
    }

    /** 기획 단원이 원본에 지정형 구역 R석(5만원)을 더하고 1층 좌석을 모두 칠한다 — 한 트랜잭션. 더한 구역의 id를 돌려준다. */
    private long addRSeatAndPaintFirstFloor() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            long rSeat = addZoneUseCase.add(new AddZoneCommand(acting(planner), springPlay, ZoneKind.DESIGNATED,
                    ZoneSettings.designated(ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000)))).zoneId();
            assignSeatsToZoneUseCase.assign(new AssignSeatsToZoneCommand(acting(planner), springPlay,
                    firstFloor.seats().stream().map(SeatResult::seatId).collect(Collectors.toSet()), rSeat));
            return rSeat;
        });
    }

    private FloorPlanResult floorPlanOf(long eventId) {
        return getEventFloorPlanUseCase.get(new GetEventFloorPlanQuery(acting(junghoon), eventId));
    }

    /** 함께 돌린 두 흐름 — 복제(복제본 id)와 편집(더한 구역 id). */
    private record Flows(Future<Long> duplication, Future<Long> edit) {
    }

    /** 다른 연결이 그 조건의 잠금을 기다리기 시작할 때까지 기다린다 — 표 잠금(relation)이나 다른 트랜잭션의 행 잠금(transactionid) 대기. */
    private void awaitWaiterOn(String condition) {
        try {
            for (int attempt = 0; attempt < 200; attempt++) {
                Long waiters = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted AND " + condition,
                        Long.class);
                if (waiters != null && waiters > 0) {
                    return;
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("잠금을 기다리는지 보다가 끊겼습니다", exception);
        }
        throw new AssertionError("10초 안에 잠금(" + condition + ")을 기다리는 연결이 없습니다");
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(30, SECONDS)) {
                throw new AssertionError("30초 안에 이어 가라는 신호가 오지 않았습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("이어 가라는 신호를 기다리다 끊겼습니다", exception);
        }
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
