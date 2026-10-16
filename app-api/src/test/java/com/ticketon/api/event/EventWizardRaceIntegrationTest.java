package com.ticketon.api.event;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.application.event.port.in.SaveEventBasicInformationUseCase;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RemoveOperatorCommand;
import com.ticketon.application.organizer.port.in.RemoveOperatorUseCase;
import com.ticketon.application.organizer.port.in.RenameOrganizerCommand;
import com.ticketon.application.organizer.port.in.RenameOrganizerUseCase;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.service.StaffAccountLocks;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
 * 진짜 PostgreSQL로 행사를 만들거나 위저드 단계를 저장하는 흐름이 다른 흐름과 가장 나쁜 순간에 엇갈려도 교착(40P01) 없이 차례로 처리되는 것을
 * 고정한다 — 잠금 순서 운영진 계정 줄 → 주최자 줄 → 행사 줄({@link StaffAccountLocks} 표, #195). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 가장 나쁜 순간은 한 흐름이 첫 잠금만 쥐고 다음 잠금으로 가기 직전이다. 붙잡을 흐름의 첫 잠금만 한 트랜잭션에서 걸어 둔 채 다른 스레드에서 맞서는
 * 흐름을 부르고, 맞서는 흐름이 잠금을 기다리기 시작한 것을 PostgreSQL의 잠금 표(pg_locks)로 확인한다. 그 순간 맞서는 흐름이 쥐지 않았어야 할 줄을
 * NOWAIT로 잡아 보고, 같은 트랜잭션에서 붙잡을 흐름을 끝까지 돌려 커밋한다 — 유스케이스는 바깥 트랜잭션에 참여하므로 첫 잠금은 이미 쥔 줄이다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈, 운영자 기획 단원. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 행사는 유스케이스로 만든다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 만들기 · 위저드 단계 저장의 잠금 순서 — 가장 나쁜 순간에 엇갈려도 교착 없이 차례로 (진짜 PostgreSQL)")
class EventWizardRaceIntegrationTest {

    private static final String SPRING_PLAY_TITLE = "2026 봄 정기공연 〈열두 번째 달〉";

    private static final String ENCORE_TITLE = "2026 봄 앵콜공연 〈열두 번째 달〉";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private OrganizerRepository organizerRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CreateEventUseCase createEventUseCase;

    @Autowired
    private SaveEventBasicInformationUseCase saveEventBasicInformationUseCase;

    @Autowired
    private RemoveOperatorUseCase removeOperatorUseCase;

    @Autowired
    private RenameOrganizerUseCase renameOrganizerUseCase;

    private String suffix;
    private long twelveMonths;
    private long junghoon;
    private long planner;
    private long springPlay;

    @BeforeEach
    void storeTwelveMonths() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoon = storeStaff("박정훈", "고래" + suffix, "junghoon-" + suffix + "@example.com");
        planner = storeStaff("김기획", "바람" + suffix, "planner-" + suffix + "@example.com");
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        springPlay = createEventUseCase.create(new CreateEventCommand(acting(junghoon), items(SPRING_PLAY_TITLE))).eventId();
    }

    @Test
    @DisplayName("두 운영진이 같은 행사의 1단계를 거의 동시에 저장하면 뒤의 저장은 행사 줄에서 기다렸다가 바뀐 값을 읽는다 — 같은 값이라 저장도 기록도 한 번")
    void concurrentSavesOfSameStepAreSerialized() throws Exception {
        Future<?> contender = runInterleaved(
                () -> {
                    StaffAccountLocks.lockForReferenceInIdOrder(staffAccountRepository, junghoon);
                    organizerRepository.findByIdForKeyShare(twelveMonths);
                    eventRepository.findByIdForUpdate(springPlay);
                },
                () -> saveTitle(junghoon, ENCORE_TITLE),
                () -> saveTitle(planner, ENCORE_TITLE),
                () -> assertThat(eventTitle()).isEqualTo(SPRING_PLAY_TITLE));

        contender.get(30, SECONDS);
        assertThat(eventTitle()).isEqualTo(ENCORE_TITLE);
        assertThat(eventOperations()).containsExactly("CREATE_EVENT " + junghoon, "EDIT_EVENT " + junghoon);
    }

    @Test
    @DisplayName("주최자명 변경이 주최자 줄을 잠근 순간 들어온 1단계 저장은 행사 줄을 잡기 전에 주최자 줄에서 기다린다 — 둘 다 끝까지 처리된다")
    void stepSaveWaitsOnOrganizerRowBeforeEventRow() throws Exception {
        Future<?> contender = runInterleaved(
                () -> {
                    StaffAccountLocks.lockForReferenceInIdOrder(staffAccountRepository, junghoon);
                    organizerRepository.findByIdForUpdate(twelveMonths);
                },
                () -> renameOrganizerUseCase.rename(
                        new RenameOrganizerCommand(acting(junghoon), OrganizerName.of("열두달 프로젝트 " + suffix))),
                () -> saveTitle(planner, ENCORE_TITLE),
                this::assertEventRowFree);

        contender.get(30, SECONDS);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM organizer WHERE id = ?", String.class, twelveMonths))
                .isEqualTo("열두달 프로젝트 " + suffix);
        assertThat(eventTitle()).isEqualTo(ENCORE_TITLE);
        assertThat(eventOperations()).containsExactly("CREATE_EVENT " + junghoon, "EDIT_EVENT " + planner);
    }

    @Test
    @DisplayName("운영자 내보내기가 계정 줄을 잠근 순간 들어온 그 운영자의 1단계 저장은 계정 줄에서 기다렸다가 다시 확인해 403으로 멈춘다 — 행사 줄을 잡지 않고 아무것도 남지 않는다")
    void removedOperatorsStepSaveWaitsAndStops() throws Exception {
        Future<?> contender = runInterleaved(
                () -> StaffAccountLocks.lockInIdOrder(staffAccountRepository, junghoon, planner),
                () -> removeOperatorUseCase.remove(new RemoveOperatorCommand(acting(junghoon), planner)),
                () -> saveTitle(planner, ENCORE_TITLE),
                () -> {
                    assertOrganizerRowUnlocked();
                    assertEventRowFree();
                });

        assertDeniedWith(contender, OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        assertThat(eventTitle()).isEqualTo(SPRING_PLAY_TITLE);
        assertThat(eventOperations()).containsExactly("CREATE_EVENT " + junghoon);
    }

    @Test
    @DisplayName("운영자 내보내기가 계정 줄을 잠근 순간 들어온 그 운영자의 행사 만들기는 계정 줄에서 기다렸다가 다시 확인해 403으로 멈춘다 — 행사가 생기지 않는다")
    void removedOperatorsCreationWaitsAndStops() throws Exception {
        Future<?> contender = runInterleaved(
                () -> StaffAccountLocks.lockInIdOrder(staffAccountRepository, junghoon, planner),
                () -> removeOperatorUseCase.remove(new RemoveOperatorCommand(acting(junghoon), planner)),
                () -> createEventUseCase.create(new CreateEventCommand(acting(planner), items(ENCORE_TITLE))),
                this::assertOrganizerRowUnlocked);

        assertDeniedWith(contender, OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event WHERE organizer_id = ?", Long.class, twelveMonths))
                .isEqualTo(1);
        assertThat(eventOperations()).containsExactly("CREATE_EVENT " + junghoon);
    }

    /**
     * 붙잡을 흐름의 첫 잠금만 한 트랜잭션에서 걸어 둔 채 다른 스레드에서 맞서는 흐름을 부른다. 맞서는 흐름이 잠금을 기다리기 시작하면 그 순간의
     * 확인을 하고, 같은 트랜잭션에서 붙잡을 흐름을 끝까지 돌려 커밋한다. 붙잡은 흐름이 실패(교착 40P01 포함)하면 그 예외로 테스트가 실패한다.
     *
     * @return 맞서는 흐름 — 끝난 뒤에 돌려준다. 성공했는지 막혔는지는 부르는 쪽이 본다.
     */
    private Future<?> runInterleaved(Runnable firstLocks, Runnable heldFlow, Runnable contender,
            Runnable whileContenderWaits) throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch mayContinue = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> held = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        firstLocks.run();
                        firstLocked.countDown();
                        awaitOrFail(mayContinue);
                        heldFlow.run();
                    }));
            awaitOrFail(firstLocked);
            Future<?> contention = executor.submit(contender);
            awaitLockWaiter();
            whileContenderWaits.run();
            mayContinue.countDown();
            held.get(30, SECONDS);
            executor.shutdown();
            assertThat(executor.awaitTermination(30, SECONDS)).as("맞서는 흐름이 30초 안에 끝난다").isTrue();
            return contention;
        } finally {
            mayContinue.countDown();
            executor.shutdown();
        }
    }

    /** 맞서는 흐름이 기다리는 동안 행사 줄은 잠겨 있지 않다 — 맞서는 흐름이 행사 줄을 먼저 잡았다면 여기서 잠금을 얻지 못한다. */
    private void assertEventRowFree() {
        assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                "SELECT id FROM event WHERE id = ? FOR UPDATE NOWAIT", Long.class, springPlay));
    }

    /**
     * 맞서는 흐름이 계정 줄에서 기다리는 동안 주최자 줄에는 아무 잠금도 없다 — 외래 키 확인 잠금(FOR KEY SHARE)끼리는 부딪히지 않으므로 FOR UPDATE로
     * 잡아 본다. 맞서는 흐름이 계정 줄보다 주최자 줄을 먼저 잡았다면(잠금 순서를 거꾸로) 여기서 잠금을 얻지 못한다.
     */
    private void assertOrganizerRowUnlocked() {
        assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                "SELECT id FROM organizer WHERE id = ? FOR UPDATE NOWAIT", Long.class, twelveMonths));
    }

    /** 맞서는 흐름이 잠근 뒤 다시 확인한 권한에서 그 오류로 막혔다. */
    private static void assertDeniedWith(Future<?> contention, OrganizerErrorCode expectedCode) {
        assertThatThrownBy(() -> contention.get(30, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PermissionDeniedException.class)
                .satisfies(exception -> assertThat(((DomainException) exception.getCause()).errorCode())
                        .isEqualTo(expectedCode));
    }

    private void saveTitle(long staffAccountId, String title) {
        saveEventBasicInformationUseCase.save(new SaveEventBasicInformationCommand(acting(staffAccountId), springPlay,
                items(title)));
    }

    private String eventTitle() {
        return jdbcTemplate.queryForObject("SELECT title FROM event WHERE id = ?", String.class, springPlay);
    }

    /** 이 주최자의 행사 조작 기록 — "한 일 운영진 id"를 넣은 차례대로. */
    private List<String> eventOperations() {
        return jdbcTemplate.queryForList("""
                SELECT staff_action || ' ' || staff_account_id FROM staff_operation_record
                WHERE organizer_id = ? AND target_kind = 'EVENT' ORDER BY id""", String.class, twelveMonths);
    }

    /** 다른 트랜잭션의 잠금을 기다리는 연결이 생길 때까지 기다린다 — 맞서는 흐름이 잠금에서 멈춘 것. */
    private void awaitLockWaiter() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Long waiters = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
            if (waiters != null && waiters > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("맞서는 흐름이 10초 안에 잠금을 기다리지 않았습니다");
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(30, SECONDS)) {
                throw new AssertionError("기다리는 신호가 30초 안에 오지 않았습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("신호를 기다리다 끊겼습니다", exception);
        }
    }

    private ActingStaff acting(long staffAccountId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), twelveMonths);
    }

    /** 극단 열두달의 봄 정기공연 1단계 — 제목만 바꿔 쓴다. */
    private static EventBasicInformationItems items(String title) {
        return new EventBasicInformationItems(EventTitle.of(title), EventType.PERFORMANCE, "연극", null,
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
