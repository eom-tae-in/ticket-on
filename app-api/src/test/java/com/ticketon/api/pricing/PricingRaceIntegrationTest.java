package com.ticketon.api.pricing;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.AddAllDayDateCommand;
import com.ticketon.application.event.port.in.AddAllDayDateUseCase;
import com.ticketon.application.event.port.in.AddZoneCommand;
import com.ticketon.application.event.port.in.AddZoneUseCase;
import com.ticketon.application.event.port.in.ChangeZoneCommand;
import com.ticketon.application.event.port.in.ChangeZoneUseCase;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeUseCase;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RemoveOperatorCommand;
import com.ticketon.application.organizer.port.in.RemoveOperatorUseCase;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.service.StaffAccountLocks;
import com.ticketon.application.pricing.port.in.IssueCouponCommand;
import com.ticketon.application.pricing.port.in.IssueCouponUseCase;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneSettings;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.pricing.CouponCode;
import com.ticketon.domain.pricing.CouponTerms;
import com.ticketon.domain.pricing.Discount;
import com.ticketon.domain.pricing.DiscountPeriod;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.PricingErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * 진짜 PostgreSQL로 위저드 4단계 저장(쿠폰 발급)이 같은 행사의 다른 저장 · 가격 변경 · 운영자 내보내기와 가장 나쁜 순간에 엇갈려도 교착(40P01) 없이
 * 차례로 처리되고, 뒤의 흐름이 앞의 흐름이 커밋한 값을 읽고 판정하는 것을 고정한다 — 잠금 순서 운영진 계정 줄 → 주최자 줄 → 행사 줄 → 가격 줄
 * ({@link StaffAccountLocks} 표). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 붙잡을 흐름의 첫 잠금만 한 트랜잭션에서 걸어 둔 채 다른 스레드에서 맞서는 흐름을 부르고, 맞서는 흐름이 잠금을 기다리기 시작한 것을 PostgreSQL의
 * 잠금 표(pg_locks)로 확인한다. 그 순간 맞서는 흐름이 쥐지 않았어야 할 줄을 NOWAIT로 잡아 보고, 같은 트랜잭션에서 붙잡을 흐름을 끝까지 돌려 커밋한다.
 * <p>
 * 늦여름(docs/15)의 대표(소유자)와 운영자 이도현. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 행사 · 일정 · 구역은 유스케이스로 만든다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("위저드 4단계 저장의 잠금 순서 — 가장 나쁜 순간에 엇갈려도 교착 없이 차례로 (진짜 PostgreSQL)")
class PricingRaceIntegrationTest {

    private static final DiscountPeriod FAR_FUTURE_VALIDITY = DiscountPeriod.of(Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2099-01-01T00:00:00Z"));

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
    private ChooseSessionModeUseCase chooseSessionModeUseCase;

    @Autowired
    private AddAllDayDateUseCase addAllDayDateUseCase;

    @Autowired
    private AddZoneUseCase addZoneUseCase;

    @Autowired
    private ChangeZoneUseCase changeZoneUseCase;

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private RemoveOperatorUseCase removeOperatorUseCase;

    private long lateSummer;
    private long owner;
    private long dohyun;
    private long festival;
    private long standingA;

    @BeforeEach
    void storeFestival() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = storeStaff("대표" + suffix, "owner-" + suffix + "@example.com");
        dohyun = storeStaff("도현" + suffix, "dohyun-" + suffix + "@example.com");
        lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, owner, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
        festival = createEventUseCase.create(new CreateEventCommand(acting(owner), new EventBasicInformationItems(
                EventTitle.of("2027 늦여름 페스티벌"), EventType.FESTIVAL, "음악", null, "늦여름이 해마다 여는 3일간의 야외 인디 음악 페스티벌입니다.",
                new Venue("한강 난지공원", "서울특별시 마포구 하늘공원로 108"), null, null, null))).eventId();
        chooseSessionModeUseCase.choose(new ChooseSessionModeCommand(acting(owner), festival, SessionMode.ALL_DAY,
                OperatingHours.of(LocalTime.of(12, 0), LocalTime.of(22, 0))));
        addAllDayDateUseCase.add(new AddAllDayDateCommand(acting(owner), festival, LocalDate.of(2027, 5, 3)));
        standingA = addZoneUseCase.add(new AddZoneCommand(acting(owner), festival, ZoneKind.QUANTITY, standingA(35_000))).zoneId();
    }

    @Test
    @DisplayName("두 운영진이 같은 코드의 쿠폰을 거의 동시에 발급하면 뒤의 발급은 행사 줄에서 기다렸다가 앞의 쿠폰을 읽어 409로 멈춘다 — 쿠폰은 하나만 남는다")
    void concurrentCouponIssuesAreSerialized() throws Exception {
        Future<?> contender = runInterleaved(
                this::lockUpToEventRow,
                () -> issueCoupon(owner, "SPRING20", 5_000),
                () -> issueCoupon(dohyun, "spring20", 3_000),
                () -> assertThat(liveCoupons()).isZero());

        assertRejectedWith(contender, PricingErrorCode.COUPON_CODE_ALREADY_TAKEN);
        assertThat(liveCoupons()).isEqualTo(1);
        assertThat(eventOperations()).containsExactly("EDIT_EVENT 쿠폰 발급 · SPRING20 (4단계) " + owner);
    }

    @Test
    @DisplayName("기본가를 30,000원으로 내리는 저장이 행사 줄을 쥔 순간 들어온 30,000원 쿠폰 발급은 기다렸다가 바뀐 기본가를 읽어 409 DISCOUNT_MAKES_PRICE_ZERO로 멈춘다")
    void couponIssueReadsPriceChangedMeanwhile() throws Exception {
        Future<?> contender = runInterleaved(
                this::lockUpToEventRow,
                () -> changeZoneUseCase.change(new ChangeZoneCommand(acting(owner), festival, standingA, standingA(30_000))),
                () -> issueCoupon(dohyun, "FAN30000", 30_000),
                () -> assertThat(liveCoupons()).isZero());

        assertRejectedWith(contender, PricingErrorCode.DISCOUNT_MAKES_PRICE_ZERO);
        assertThat(jdbcTemplate.queryForObject("SELECT base_price FROM event_zone WHERE id = ?", Long.class, standingA)).isEqualTo(30_000);
        assertThat(liveCoupons()).isZero();
    }

    @Test
    @DisplayName("운영자 내보내기가 계정 줄을 잠근 순간 들어온 그 운영자의 쿠폰 발급은 계정 줄에서 기다렸다가 다시 확인해 403으로 멈춘다 — 주최자 · 행사 줄을 잡지 않고 아무것도 남지 않는다")
    void removedOperatorsCouponIssueWaitsAndStops() throws Exception {
        Future<?> contender = runInterleaved(
                () -> StaffAccountLocks.lockInIdOrder(staffAccountRepository, owner, dohyun),
                () -> removeOperatorUseCase.remove(new RemoveOperatorCommand(acting(owner), dohyun)),
                () -> issueCoupon(dohyun, "FAN5000", 5_000),
                () -> {
                    assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                            "SELECT id FROM organizer WHERE id = ? FOR UPDATE NOWAIT", Long.class, lateSummer));
                    assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                            "SELECT id FROM event WHERE id = ? FOR UPDATE NOWAIT", Long.class, festival));
                });

        assertThatThrownBy(() -> contender.get(30, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(PermissionDeniedException.class)
                .satisfies(exception -> assertThat(((DomainException) exception.getCause()).errorCode())
                        .isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThat(liveCoupons()).isZero();
    }

    /** 4단계 저장이 행사 줄까지 잡는 차례 — 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 행사 줄(FOR UPDATE). */
    private void lockUpToEventRow() {
        StaffAccountLocks.lockForReferenceInIdOrder(staffAccountRepository, owner);
        organizerRepository.findByIdForKeyShare(lateSummer);
        eventRepository.findByIdForUpdate(festival);
    }

    /**
     * 붙잡을 흐름의 첫 잠금을 한 트랜잭션에서 걸어 둔 채 맞서는 흐름을 다른 스레드에서 부르고, 맞서는 흐름이 잠금을 기다리기 시작하면 확인한 뒤 붙잡을
     * 흐름을 끝까지 돌려 커밋한다.
     *
     * @return 맞서는 흐름 — 끝난 뒤에 돌려준다. 성공했는지 막혔는지는 부르는 쪽이 본다.
     */
    private Future<?> runInterleaved(Runnable firstLocks, Runnable heldFlow, Runnable contender, Runnable whileContenderWaits)
            throws Exception {
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

    private static void assertRejectedWith(Future<?> contention, ErrorCode expectedCode) {
        assertThatThrownBy(() -> contention.get(30, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuleViolationException.class)
                .satisfies(exception -> assertThat(((DomainException) exception.getCause()).errorCode()).isEqualTo(expectedCode));
    }

    private void issueCoupon(long staffAccountId, String code, long amount) {
        issueCouponUseCase.issue(new IssueCouponCommand(acting(staffAccountId), festival, CouponCode.of(code),
                new CouponTerms(Discount.fixedAmount(Money.of(amount)), 100, true, FAR_FUTURE_VALIDITY)));
    }

    private static ZoneSettings standingA(long basePrice) {
        return ZoneSettings.quantity(ZoneName.of("스탠딩A"), ZoneColor.of("#30A46C"), ZoneBasePrice.of(basePrice), ZoneCapacity.of(800));
    }

    private long liveCoupons() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM coupon WHERE event_id = ? AND deleted_at IS NULL", Long.class,
                festival);
        assertThat(count).isNotNull();
        return count;
    }

    /** 4단계 저장의 조작 기록 — "한 일 요약 운영진 id". 행사 만들기 · 일정 · 구역 기록은 빼고 본다. */
    private List<String> eventOperations() {
        return jdbcTemplate.queryForList("""
                SELECT staff_action || ' ' || summary || ' ' || staff_account_id FROM staff_operation_record
                WHERE organizer_id = ? AND target_kind = 'EVENT' AND summary LIKE '%(4단계)' ORDER BY id""", String.class, lateSummer);
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
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), lateSummer);
    }

    /** 이메일 인증을 마친 운영진 로그인 한 줄 — 이 테스트는 로그인하지 않아 비밀번호 해시는 아무 값이다. */
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

    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id", Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '10 days')""", organizerId, staffAccountId, role);
    }
}
