package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.EVENT_CANCELLATION_FEE_TIER;
import static com.ticketon.persistence.jooq.Tables.EVENT_CATEGORY;
import static com.ticketon.persistence.jooq.Tables.EVENT_SALES_PAUSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventCategorySelection;
import com.ticketon.domain.event.EventPasswordHash;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventPublicContact;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventVisibility;
import com.ticketon.domain.event.EventVisibilitySetting;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.LeadTime;
import com.ticketon.domain.event.ReentrySetting;
import com.ticketon.domain.event.SalesPause;
import com.ticketon.domain.event.SalesPauseCause;
import com.ticketon.domain.event.SalesPauseReason;
import com.ticketon.domain.event.SeatsPerSelectionLimit;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.TicketsPerPersonBasis;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.policy.CancellationFeeTier;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 진짜 PostgreSQL에 마이그레이션(V19 행사 · 취소 규정 구간 · 판매 중단 이력)을 올리고 새 행사 저장(처음 값 · 공개 주소 · 진행 단계), 다시 읽기,
 * 위저드 단계를 고쳐 저장하기, 판매 중단 이력 저장, 잠그고 읽기, 표의 제약(공개 주소 유일 · 카테고리 유형 일치)을 본다. 극단 열두달(docs/15
 * 박정훈)의 봄 정기공연과 늦여름의 페스티벌이다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 저장소 (진짜 PostgreSQL)")
class EventPersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final EventPublicAddress SPRING_PLAY_ADDRESS = new EventPublicAddress("Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0");
    private static final EventPublicAddress FESTIVAL_ADDRESS = new EventPublicAddress("aB3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG9");
    private static final Instant CREATED_AT = Instant.parse("2026-02-01T01:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-02-03T12:00:00Z");

    /** "공연 3일 전까지 전액 환불"(docs/08 조정 예시). */
    private static final CancellationPolicy FULL_REFUND_UNTIL_THREE_DAYS_BEFORE = CancellationPolicy.of(List.of(
            CancellationFeeTier.of(3, 0), CancellationFeeTier.of(1, 30), CancellationFeeTier.of(0, 90)));

    private static CloseableDSLContext dsl;

    private EventPersistenceAdapter adapter;
    private long twelveMonths;
    private long junghoon;

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
    void storeTwelveMonths() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        dsl.truncate(ADMIN_ACCOUNT).cascade().execute();
        twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        junghoon = rows.staffAccount("junghoon@example.com");
        adapter = new EventPersistenceAdapter(dsl);
    }

    /** 극단 열두달의 봄 정기공연 — 공연 · 연극, 선택 항목까지 모두 적었다. */
    private static EventBasicInformation springPlay() {
        return new EventBasicInformation(EventTitle.of("2026 봄 정기공연 〈열두 번째 달〉"), EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.\n러닝타임 100분.",
                new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), "지하 1층, 주차 불가",
                ExternalLink.of("https://map.example/place/12"), EventPublicContact.of("twelve-months@example.com"));
    }

    /** 늦여름 페스티벌 — 페스티벌 · '기타' + 장르명 "낭독 축제", 선택 항목은 비웠다. */
    private static EventBasicInformation readingFestival() {
        return new EventBasicInformation(EventTitle.of("늦여름 낭독 축제"), EventType.FESTIVAL,
                EventCategorySelection.other(EventType.FESTIVAL, "낭독 축제"), "사흘 동안 낭독극을 올립니다.",
                new Venue("늦여름 야외무대", "경기도 가평군 가평읍 강변로 3"), null, null, null);
    }

    /** 오픈 3/1 14:00, 예매 종료 2시간 전, 검표 90분 전, 1회 6석, 날짜별 4매, 하루에 한 회차만, 재입장 켬, 만 19세 이상. */
    private static BookingSettings adjustedBookingSettings() {
        return new BookingSettings(Instant.parse("2026-03-01T05:00:00Z"), LeadTime.of(Duration.ofHours(2)),
                LeadTime.of(Duration.ofMinutes(90)), SeatsPerSelectionLimit.of(6),
                TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.PER_DATE), SessionChoiceRestriction.ONE_SESSION_PER_DATE,
                ReentrySetting.ALLOWED, AgeRestriction.atLeast(19));
    }

    private Event newSpringPlay() {
        return Event.create(twelveMonths, SPRING_PLAY_ADDRESS, springPlay(), CREATED_AT);
    }

    @Test
    @DisplayName("새 행사를 저장하면 id가 붙고, 표에는 작성 중 · 공개 주소 · 1단계 진행 · 5단계 처음 값과 기준표 취소 규정이 남는다")
    void addsNewEventWithDefaults() {
        Event saved = adapter.add(newSpringPlay());

        assertThat(saved.id()).isPresent();
        long id = saved.id().getAsLong();
        var row = dsl.selectFrom(EVENT).where(EVENT.ID.eq(id)).fetchSingle();
        assertThat(row.getOrganizerId()).isEqualTo(twelveMonths);
        assertThat(row.getPublicAddress()).isEqualTo(SPRING_PLAY_ADDRESS.value());
        assertThat(row.getStatus()).isEqualTo("DRAFT");
        assertThat(row.getWizardProgressStep()).isEqualTo("BASIC_INFORMATION");
        assertThat(row.getLastModifiedAt().toInstant()).isEqualTo(CREATED_AT);
        assertThat(row.getVisibility()).isEqualTo("PUBLIC");
        assertThat(row.getPasswordHash()).isNull();
        assertThat(row.getBookingOpensAt()).isNull();
        assertThat(row.getBookingCloseLeadMinutes()).isEqualTo(60);
        assertThat(row.getCheckinOpenLeadMinutes()).isEqualTo(60);
        assertThat(row.getMaxSeatsPerSelection()).isEqualTo(4);
        assertThat(row.getMaxTicketsPerPerson()).isNull();
        assertThat(row.getTicketsPerPersonBasis()).isNull();
        assertThat(row.getSessionChoice()).isEqualTo("NONE");
        assertThat(row.getReentry()).isEqualTo("NOT_ALLOWED");
        assertThat(row.getMinimumAge()).isZero();
        assertThat(row.getBookingOpenedAt()).isNull();
        assertThat(row.getDeletedAt()).isNull();
        assertThat(dsl.select(EVENT_CATEGORY.NAME).from(EVENT_CATEGORY).where(EVENT_CATEGORY.ID.eq(row.getCategoryId()))
                .fetchSingle().value1()).isEqualTo("연극");
        assertThat(row.getGenreName()).isNull();
        assertThat(dsl.select(EVENT_CANCELLATION_FEE_TIER.FROM_DAYS_BEFORE, EVENT_CANCELLATION_FEE_TIER.DEDUCTION_RATE_PERCENT)
                .from(EVENT_CANCELLATION_FEE_TIER)
                .where(EVENT_CANCELLATION_FEE_TIER.EVENT_ID.eq(id))
                .orderBy(EVENT_CANCELLATION_FEE_TIER.FROM_DAYS_BEFORE.desc())
                .fetch(record -> record.value1() + "일 전 " + record.value2() + "%"))
                .containsExactly("10일 전 0%", "7일 전 10%", "3일 전 20%", "1일 전 30%", "0일 전 90%");
    }

    @Test
    @DisplayName("저장한 행사를 다시 읽으면 저장한 값 그대로다 — 정식 카테고리와 선택 항목까지")
    void readsSavedEvent() {
        long id = adapter.add(newSpringPlay()).id().getAsLong();

        Event found = adapter.findById(id).orElseThrow();

        assertThat(found.id()).hasValue(id);
        assertThat(found.organizerId()).isEqualTo(twelveMonths);
        assertThat(found.publicAddress()).isEqualTo(SPRING_PLAY_ADDRESS);
        assertThat(found.basicInformation()).isEqualTo(springPlay());
        assertThat(found.poster()).isEmpty();
        assertThat(found.visibility()).isEqualTo(EventVisibilitySetting.publicByDefault());
        assertThat(found.bookingSettings()).isEqualTo(BookingSettings.defaults());
        assertThat(found.cancellationPolicy()).isEqualTo(CancellationPolicy.standard());
        assertThat(found.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(found.bookingOpenedAt()).isEmpty();
        assertThat(found.salesPauses()).isEmpty();
        assertThat(found.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
        assertThat(found.lastModifiedAt()).isEqualTo(CREATED_AT);
        assertThat(adapter.findById(id + 1000)).isEmpty();
    }

    @Test
    @DisplayName("'기타'를 고른 행사는 장르명만 남기고, 비운 선택 항목은 비어 있는 채로 다시 읽는다")
    void readsOtherCategoryWithGenreName() {
        long id = adapter.add(Event.create(twelveMonths, FESTIVAL_ADDRESS, readingFestival(), CREATED_AT)).id().getAsLong();

        var row = dsl.selectFrom(EVENT).where(EVENT.ID.eq(id)).fetchSingle();
        assertThat(row.getCategoryId()).isNull();
        assertThat(row.getGenreName()).isEqualTo("낭독 축제");
        Event found = adapter.findById(id).orElseThrow();
        assertThat(found.basicInformation()).isEqualTo(readingFestival());
        assertThat(found.basicInformation().category().isOther()).isTrue();
    }

    @Test
    @DisplayName("단계를 고쳐 저장하면 칸이 모두 지금 값이 된다 — 1 · 5단계, 암호 해시, 조정한 취소 규정, 진행 단계, 마지막 수정 시각")
    void savesWizardSteps() {
        long id = adapter.add(newSpringPlay()).id().getAsLong();
        Event event = adapter.findByIdForUpdate(id).orElseThrow();
        event.saveBasicInformationStep(readingFestival(), SAVED_AT);
        event.saveBookingStep(EventVisibility.PASSWORD_PROTECTED, new EventPasswordHash("{test}0315"), adjustedBookingSettings(),
                FULL_REFUND_UNTIL_THREE_DAYS_BEFORE, SAVED_AT);

        adapter.save(event);

        Event found = adapter.findById(id).orElseThrow();
        assertThat(found.basicInformation()).isEqualTo(readingFestival());
        assertThat(found.visibility()).isEqualTo(
                new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, new EventPasswordHash("{test}0315")));
        assertThat(found.bookingSettings()).isEqualTo(adjustedBookingSettings());
        assertThat(found.cancellationPolicy()).isEqualTo(FULL_REFUND_UNTIL_THREE_DAYS_BEFORE);
        assertThat(found.wizardProgress()).isEqualTo(EventWizardStep.BOOKING_SETTINGS);
        assertThat(found.lastModifiedAt()).isEqualTo(SAVED_AT);
        assertThat(found.publicAddress()).isEqualTo(SPRING_PLAY_ADDRESS);
        var row = dsl.selectFrom(EVENT).where(EVENT.ID.eq(id)).fetchSingle();
        assertThat(row.getBookingCloseLeadMinutes()).isEqualTo(120);
        assertThat(row.getCheckinOpenLeadMinutes()).isEqualTo(90);
        assertThat(row.getMaxTicketsPerPerson()).isEqualTo(4);
        assertThat(row.getTicketsPerPersonBasis()).isEqualTo("PER_DATE");
        assertThat(row.getMinimumAge()).isEqualTo(19);
        assertThat(dsl.fetchCount(EVENT_CANCELLATION_FEE_TIER, EVENT_CANCELLATION_FEE_TIER.EVENT_ID.eq(id))).isEqualTo(3);
    }

    @Test
    @DisplayName("판매 중단 이력은 멈춘 순서대로 남고, 다시 저장하면 새 중단은 더하고 끝난 중단은 끝난 시각만 적는다 — 상태와 예매가 열린 시각도")
    void savesSalesPauses() {
        long id = adapter.add(newSpringPlay()).id().getAsLong();
        long admin = adminAccount("admin@ticket-on.test");
        Instant openedAt = Instant.parse("2026-03-01T05:00:00Z");
        Instant pausedAt = Instant.parse("2026-03-05T01:00:00Z");
        Instant resumedAt = Instant.parse("2026-03-06T01:00:00Z");
        Instant heldAt = Instant.parse("2026-03-07T01:00:00Z");
        SalesPause organizerPause = new SalesPause(SalesPauseCause.ORGANIZER, junghoon,
                SalesPauseReason.fromInput("출연진 사정으로 잠시 멈춥니다").orElseThrow(), pausedAt, null);
        adapter.save(storedIn(id, EventStatus.SALES_PAUSED, openedAt, List.of(organizerPause)));

        SalesPause adminHold = new SalesPause(SalesPauseCause.ADMIN_SUSPENSION, admin, null, heldAt, null);
        adapter.save(storedIn(id, EventStatus.SALES_PAUSED, openedAt, List.of(
                new SalesPause(SalesPauseCause.ORGANIZER, junghoon, organizerPause.reason(), pausedAt, resumedAt), adminHold)));

        Event found = adapter.findById(id).orElseThrow();
        assertThat(found.status()).isEqualTo(EventStatus.SALES_PAUSED);
        assertThat(found.bookingOpenedAt()).contains(openedAt);
        assertThat(found.salesPauses()).containsExactly(
                new SalesPause(SalesPauseCause.ORGANIZER, junghoon, organizerPause.reason(), pausedAt, resumedAt), adminHold);
        assertThat(dsl.selectFrom(EVENT_SALES_PAUSE).where(EVENT_SALES_PAUSE.EVENT_ID.eq(id))
                .orderBy(EVENT_SALES_PAUSE.SEQUENCE_NUMBER).fetch())
                .satisfiesExactly(
                        first -> {
                            assertThat(first.getSequenceNumber()).isZero();
                            assertThat(first.getPausedByStaffAccountId()).isEqualTo(junghoon);
                            assertThat(first.getPausedByAdminAccountId()).isNull();
                            assertThat(first.getEndedAt().toInstant()).isEqualTo(resumedAt);
                        },
                        second -> {
                            assertThat(second.getSequenceNumber()).isEqualTo(1);
                            assertThat(second.getPausedByAdminAccountId()).isEqualTo(admin);
                            assertThat(second.getPausedByStaffAccountId()).isNull();
                            assertThat(second.getReason()).isNull();
                            assertThat(second.getEndedAt()).isNull();
                        });
    }

    @Test
    @DisplayName("잠그고 읽는 동안 다른 트랜잭션은 그 행사 줄을 잠그지 못한다 — 위저드 단계 저장끼리 차례로")
    void findByIdForUpdateLocksRow() throws Exception {
        long id = adapter.add(newSpringPlay()).id().getAsLong();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch checked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> holder = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                boolean found = transactionAdapter.findByIdForUpdate(id).isPresent();
                locked.countDown();
                assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
                return found;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatExceptionOfType(DataAccessException.class)
                    .isThrownBy(() -> dsl.selectFrom(EVENT).where(EVENT.ID.eq(id)).forUpdate().noWait().fetch())
                    .satisfies(lockNotAvailable -> assertThat(lockNotAvailable.sqlState()).isEqualTo("55P03"));
            checked.countDown();

            assertThat(holder.get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            checked.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("지운 행사는 찾지 않고 저장하지 않는다")
    void deletedEventIsAbsent() {
        long id = adapter.add(newSpringPlay()).id().getAsLong();
        dsl.update(EVENT).set(EVENT.DELETED_AT, SAVED_AT.atOffset(ZoneOffset.UTC)).where(EVENT.ID.eq(id)).execute();

        assertThat(adapter.findById(id)).isEmpty();
        assertThat(adapter.findByIdForUpdate(id)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(storedIn(id, EventStatus.DRAFT, null, List.of())));
    }

    @Test
    @DisplayName("저장 전 행사는 고쳐 저장하지 않고, 이미 저장된 행사는 새로 넣지 않는다")
    void requiresMatchingSaveMethod() {
        long id = adapter.add(newSpringPlay()).id().getAsLong();

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(newSpringPlay()));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.add(storedIn(id, EventStatus.DRAFT, null, List.of())));
        assertThat(dsl.fetchCount(EVENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("포스터 칸이 생기기 전(#40)에는 포스터가 있는 행사를 저장하지 않는다 — 말없이 빠뜨리지 않게")
    void refusesPosterUntilColumnExists() {
        Event event = adapter.add(newSpringPlay());
        event.changePoster(new EventPoster("poster-key.jpg"));

        assertThatIllegalStateException().isThrownBy(() -> adapter.save(event));
    }

    @Test
    @DisplayName("공개 주소는 두 행사가 함께 쓸 수 없다 — 한 링크가 두 행사를 가리키지 않게")
    void publicAddressIsUnique() {
        adapter.add(newSpringPlay());

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> adapter.add(Event.create(twelveMonths, SPRING_PLAY_ADDRESS, readingFestival(), CREATED_AT)));
    }

    @Test
    @DisplayName("표는 행사의 유형과 다른 유형의 카테고리를 가리키지 못하고, 암호 보호인데 암호 해시가 없는 줄을 받지 않는다")
    void tableKeepsCategoryTypeAndPasswordConsistent() {
        long id = adapter.add(newSpringPlay()).id().getAsLong();
        long painting = dsl.select(EVENT_CATEGORY.ID).from(EVENT_CATEGORY)
                .where(EVENT_CATEGORY.EVENT_TYPE.eq("EXHIBITION")).and(EVENT_CATEGORY.NAME.eq("회화"))
                .fetchSingle().value1();

        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.update(EVENT)
                .set(EVENT.CATEGORY_ID, painting).where(EVENT.ID.eq(id)).execute());
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.update(EVENT)
                .set(EVENT.VISIBILITY, "PASSWORD_PROTECTED").where(EVENT.ID.eq(id)).execute());
    }

    /** 그 id로 저장돼 있던 봄 정기공연 — 그 상태 · 예매가 열린 시각 · 판매 중단 이력으로. */
    private Event storedIn(long id, EventStatus status, Instant bookingOpenedAt, List<SalesPause> salesPauses) {
        return Event.restore(id, twelveMonths, SPRING_PLAY_ADDRESS, springPlay(), null, EventVisibilitySetting.publicByDefault(),
                BookingSettings.defaults(), CancellationPolicy.standard(), status, bookingOpenedAt, salesPauses,
                EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, SAVED_AT);
    }

    private long adminAccount(String email) {
        return dsl.insertInto(ADMIN_ACCOUNT)
                .set(ADMIN_ACCOUNT.GRADE, "ADMIN")
                .set(ADMIN_ACCOUNT.EMAIL, email)
                .set(ADMIN_ACCOUNT.EMAIL_LOWERCASE, email.toLowerCase())
                .set(ADMIN_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(ADMIN_ACCOUNT.ISSUED_AT, CREATED_AT.atOffset(ZoneOffset.UTC))
                .returningResult(ADMIN_ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }

    /** 제 연결 · 트랜잭션에서 저장소를 써서 일하고 커밋한다 — 두 트랜잭션의 잠금을 보려고 연결을 따로 연다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(transaction -> work.run(new EventPersistenceAdapter(transaction.dsl())));
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(EventPersistenceAdapter transactionAdapter) throws Exception;
    }
}
