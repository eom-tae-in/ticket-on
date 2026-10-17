package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_STAGE_POSITION;
import static com.ticketon.persistence.jooq.Tables.EVENT_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneSettings;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * 진짜 PostgreSQL에 마이그레이션(V22 구역 · 무대 위치)을 올리고 도면의 구역 저장(새 구역 id · 더한 순서), 다시 읽기, 고쳐 저장하기, 지운 시각을
 * 적어 남기기, 지운 구역의 이름 다시 쓰기, 무대 위치, 행사끼리 나뉘는지, 지우지 않은 구역끼리의 이름 유일 색인을 본다. 블록 · 좌석(V23)은
 * {@link FloorPlanBlockSeatPersistenceTest}가 본다. 극단 열두달(docs/15 박정훈)의 봄 정기공연 — "R석, 5만원"(docs/02 "두 개의 축")과 "스탠딩A
 * 300"(docs/02 "구역"). Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 도면 저장소 — 구역 · 무대 위치 (진짜 PostgreSQL)")
class FloorPlanPersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final Instant CREATED_AT = Instant.parse("2026-02-01T01:00:00Z");
    private static final Instant REMOVED_AT = Instant.parse("2026-02-12T11:00:00Z");
    private static final ZoneColor RED = ZoneColor.of("#E5484D");
    private static final ZoneColor GREEN = ZoneColor.of("#30A46C");
    private static final ZoneSettings R_SEAT = ZoneSettings.designated(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000));
    private static final ZoneSettings STANDING_A_300 =
            ZoneSettings.quantity(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), ZoneCapacity.of(300));

    private static CloseableDSLContext dsl;

    private FloorPlanPersistenceAdapter adapter;
    private long springPlay;
    private long encore;

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
    void storeEvents() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        EventPersistenceAdapter events = new EventPersistenceAdapter(dsl);
        springPlay = events.add(Event.create(twelveMonths, new EventPublicAddress("Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0"),
                springPlayInformation("2026 봄 정기공연 〈열두 번째 달〉"), CREATED_AT)).id().getAsLong();
        encore = events.add(Event.create(twelveMonths, new EventPublicAddress("aB3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG9"),
                springPlayInformation("2026 봄 앵콜공연 〈열두 번째 달〉"), CREATED_AT)).id().getAsLong();
        adapter = new FloorPlanPersistenceAdapter(dsl);
    }

    private static EventBasicInformation springPlayInformation(String title) {
        return new EventBasicInformation(EventTitle.of(title), EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), null, null,
                null);
    }

    /** 봄 정기공연의 도면에 스탠딩A 300 · R석 5만원을 차례로 더해 저장한다. */
    private FloorPlan saveStandingAndRSeat() {
        FloorPlan floorPlan = adapter.findByEventId(springPlay);
        floorPlan.addZone(ZoneKind.QUANTITY, STANDING_A_300);
        floorPlan.addZone(ZoneKind.DESIGNATED, R_SEAT);
        return adapter.save(floorPlan, CREATED_AT);
    }

    private static long idOf(FloorPlan floorPlan, String zoneName) {
        return floorPlan.zones().stream()
                .filter(zone -> zone.name().value().equals(zoneName))
                .findFirst()
                .orElseThrow()
                .id()
                .getAsLong();
    }

    @Test
    @DisplayName("구역도 무대 위치도 없는 행사의 도면은 비어 있다")
    void emptyFloorPlan() {
        FloorPlan floorPlan = adapter.findByEventId(springPlay);

        assertThat(floorPlan.eventId()).isEqualTo(springPlay);
        assertThat(floorPlan.zones()).isEmpty();
        assertThat(floorPlan.blocks()).isEmpty();
        assertThat(floorPlan.stagePosition()).isEmpty();
    }

    @Test
    @DisplayName("새 구역을 저장하면 id가 붙고, 다시 읽으면 더한 순서로 종류 · 이름 · 색 · 기본가 · 정원이 그대로다")
    void savesAndReadsZones() {
        FloorPlan saved = saveStandingAndRSeat();

        assertThat(saved.zones()).allSatisfy(zone -> assertThat(zone.id()).isPresent());
        FloorPlan found = adapter.findByEventId(springPlay);
        assertThat(found.zones()).extracting(Zone::settings).containsExactly(STANDING_A_300, R_SEAT);
        assertThat(found.zones()).extracting(Zone::kind).containsExactly(ZoneKind.QUANTITY, ZoneKind.DESIGNATED);
        assertThat(found.zones()).extracting(zone -> zone.id().getAsLong())
                .containsExactly(idOf(saved, "스탠딩A"), idOf(saved, "R석"));
        var standingRow = dsl.selectFrom(EVENT_ZONE).where(EVENT_ZONE.ID.eq(idOf(saved, "스탠딩A"))).fetchSingle();
        assertThat(standingRow.getEventId()).isEqualTo(springPlay);
        assertThat(standingRow.getCapacity()).isEqualTo(300);
        assertThat(standingRow.getBasePrice()).isEqualTo(30_000L);
        assertThat(standingRow.getColor()).isEqualTo("#30A46C");
        assertThat(dsl.selectFrom(EVENT_ZONE).where(EVENT_ZONE.ID.eq(idOf(saved, "R석"))).fetchSingle().getCapacity()).isNull();
    }

    @Test
    @DisplayName("고친 구역을 저장하면 다시 읽을 때 고친 이름 · 색 · 기본가 · 정원이다 — id는 그대로다")
    void savesChangedZone() {
        FloorPlan floorPlan = saveStandingAndRSeat();
        long standingA = idOf(floorPlan, "스탠딩A");
        ZoneSettings standingB = ZoneSettings.quantity(ZoneName.of("스탠딩B"), RED, ZoneBasePrice.of(25_000), ZoneCapacity.of(500));

        floorPlan.changeZone(standingA, standingB);
        adapter.save(floorPlan, CREATED_AT);

        FloorPlan found = adapter.findByEventId(springPlay);
        assertThat(found.zone(standingA).settings()).isEqualTo(standingB);
        assertThat(found.zones()).hasSize(2);
    }

    @Test
    @DisplayName("도면에서 뺀 구역은 줄을 남기고 지운 시각을 적는다 — 다시 읽으면 없고, 그 이름으로 새 구역을 만들 수 있다")
    void removedZoneKeepsRow() {
        FloorPlan floorPlan = saveStandingAndRSeat();
        long standingA = idOf(floorPlan, "스탠딩A");

        floorPlan.removeZone(standingA);
        adapter.save(floorPlan, REMOVED_AT);

        var removedRow = dsl.selectFrom(EVENT_ZONE).where(EVENT_ZONE.ID.eq(standingA)).fetchSingle();
        assertThat(removedRow.getDeletedAt().toInstant()).isEqualTo(REMOVED_AT);
        assertThat(removedRow.getName()).isEqualTo("스탠딩A");
        FloorPlan found = adapter.findByEventId(springPlay);
        assertThat(found.zones()).extracting(zone -> zone.name().value()).containsExactly("R석");

        found.addZone(ZoneKind.QUANTITY, STANDING_A_300);
        FloorPlan again = adapter.save(found, REMOVED_AT.plusSeconds(60));

        assertThat(idOf(again, "스탠딩A")).isNotEqualTo(standingA);
        assertThat(dsl.fetchCount(EVENT_ZONE, EVENT_ZONE.EVENT_ID.eq(springPlay))).isEqualTo(3);
        assertThat(dsl.fetchCount(EVENT_ZONE, EVENT_ZONE.EVENT_ID.eq(springPlay).and(EVENT_ZONE.DELETED_AT.isNull()))).isEqualTo(2);
    }

    @Test
    @DisplayName("지운 구역은 이미 지운 시각이 있어 다음 저장에서도 그 시각 그대로다")
    void removedAtStaysOnLaterSaves() {
        FloorPlan floorPlan = saveStandingAndRSeat();
        long standingA = idOf(floorPlan, "스탠딩A");
        floorPlan.removeZone(standingA);
        adapter.save(floorPlan, REMOVED_AT);

        adapter.save(adapter.findByEventId(springPlay), REMOVED_AT.plusSeconds(3_600));

        assertThat(dsl.select(EVENT_ZONE.DELETED_AT).from(EVENT_ZONE).where(EVENT_ZONE.ID.eq(standingA)).fetchSingle().value1())
                .isEqualTo(REMOVED_AT.atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("무대 위치를 정해 저장하면 다시 읽을 때 그 자리이고, 옮겨 저장하면 옮긴 자리다 — 줄은 하나다")
    void savesStagePosition() {
        FloorPlan floorPlan = adapter.findByEventId(springPlay);
        floorPlan.placeStage(CanvasPoint.of(400, -30));
        adapter.save(floorPlan, CREATED_AT);

        FloorPlan found = adapter.findByEventId(springPlay);
        assertThat(found.stagePosition()).contains(CanvasPoint.of(400, -30));

        found.placeStage(CanvasPoint.of(420, 0));
        adapter.save(found, CREATED_AT);

        assertThat(adapter.findByEventId(springPlay).stagePosition()).contains(CanvasPoint.of(420, 0));
        assertThat(dsl.fetchCount(EVENT_STAGE_POSITION, EVENT_STAGE_POSITION.EVENT_ID.eq(springPlay))).isEqualTo(1);
    }

    @Test
    @DisplayName("행사마다 도면이 따로다 — 다른 행사의 구역은 읽지도 지우지도 않고, 같은 이름을 써도 된다")
    void floorPlansAreSeparatePerEvent() {
        saveStandingAndRSeat();
        FloorPlan encorePlan = adapter.findByEventId(encore);
        encorePlan.addZone(ZoneKind.QUANTITY, STANDING_A_300);

        adapter.save(encorePlan, CREATED_AT);

        assertThat(adapter.findByEventId(encore).zones()).extracting(zone -> zone.name().value()).containsExactly("스탠딩A");
        assertThat(adapter.findByEventId(springPlay).zones()).extracting(zone -> zone.name().value())
                .containsExactly("스탠딩A", "R석");
    }

    @Test
    @DisplayName("지운 구역이나 다른 행사의 구역을 저장하려 하면 아무것도 쓰지 않고 거부한다")
    void rejectsUnknownZones() {
        FloorPlan floorPlan = saveStandingAndRSeat();
        long standingA = idOf(floorPlan, "스탠딩A");
        Zone storedStanding = floorPlan.zone(standingA);
        floorPlan.removeZone(standingA);
        adapter.save(floorPlan, REMOVED_AT);

        FloorPlan stale = FloorPlan.restore(springPlay, List.of(storedStanding), List.of(), null);
        FloorPlan foreign = FloorPlan.restore(encore, List.of(adapter.findByEventId(springPlay).zones().getFirst()), List.of(), null);

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(stale, REMOVED_AT.plusSeconds(60)));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(foreign, REMOVED_AT.plusSeconds(60)));

        assertThat(adapter.findByEventId(springPlay).zones()).extracting(zone -> zone.name().value()).containsExactly("R석");
        assertThat(adapter.findByEventId(encore).zones()).isEmpty();
    }

    @Test
    @DisplayName("지우지 않은 구역끼리는 이름이 겹칠 수 없다 — 지운 구역과는 겹쳐도 된다 (유일 색인)")
    void zoneNameUniqueAmongLiveZones() {
        insertZone(springPlay, "스탠딩A", null);
        insertZone(springPlay, "스탠딩A", REMOVED_AT);

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> insertZone(springPlay, "스탠딩A", null))
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo("23505");
                    assertThat(rejected.getMessage()).contains("\"event_zone_event_name_ux\"");
                });
    }

    private void insertZone(long eventId, String name, Instant deletedAt) {
        dsl.insertInto(EVENT_ZONE)
                .set(EVENT_ZONE.EVENT_ID, eventId)
                .set(EVENT_ZONE.KIND, "QUANTITY")
                .set(EVENT_ZONE.NAME, name)
                .set(EVENT_ZONE.COLOR, "#30A46C")
                .set(EVENT_ZONE.BASE_PRICE, 30_000L)
                .set(EVENT_ZONE.CAPACITY, 300)
                .set(EVENT_ZONE.DELETED_AT, deletedAt == null ? null : deletedAt.atOffset(ZoneOffset.UTC))
                .execute();
    }
}
