package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_BLOCK;
import static com.ticketon.persistence.jooq.Tables.EVENT_SEAT;
import static com.ticketon.persistence.jooq.Tables.EVENT_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Seat;
import com.ticketon.domain.event.SeatAttribute;
import com.ticketon.domain.event.SeatBlock;
import com.ticketon.domain.event.SeatNumber;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneSettings;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
 * 진짜 PostgreSQL에 마이그레이션(V23 블록 · 좌석)을 올리고 도면의 블록 · 좌석 저장을 본다 — 10행 × 20열 블록을 얹으면 좌석 200줄, 다시 읽으면
 * 그대로, 블록을 옮기고 돌리고 이름을 고쳐도 좌석 줄과 id는 그대로, 좌석 구역 배정 · 속성 · 끄기는 바뀐 칸만, 블록을 지우면 블록 · 좌석에 같은
 * 지운 시각, 구역을 지우면 같은 저장에서 좌석 배정이 풀림, 묵은 · 다른 행사의 블록 · 좌석은 아무것도 쓰지 않고 거부, 50행 × 50열 2,500석.
 * 극단 열두달(docs/15 박정훈)의 봄 정기공연 — 200석 소극장(docs/02 "200석짜리 소극장이 5분 안에"), "R석, 5만원"(docs/02 "두 개의 축").
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 도면 저장소 — 블록 · 좌석 (진짜 PostgreSQL)")
class FloorPlanBlockSeatPersistenceTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final Instant CREATED_AT = Instant.parse("2026-02-01T01:00:00Z");
    private static final Instant REMOVED_AT = Instant.parse("2026-02-12T11:00:00Z");
    private static final ZoneSettings R_SEAT =
            ZoneSettings.designated(ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000));

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
                information("2026 봄 정기공연 〈열두 번째 달〉"), CREATED_AT)).id().getAsLong();
        encore = events.add(Event.create(twelveMonths, new EventPublicAddress("aB3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG9"),
                information("2026 봄 앵콜공연 〈열두 번째 달〉"), CREATED_AT)).id().getAsLong();
        adapter = new FloorPlanPersistenceAdapter(dsl);
    }

    private static EventBasicInformation information(String title) {
        return new EventBasicInformation(EventTitle.of(title), EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), null, null,
                null);
    }

    /** 봄 정기공연의 도면에 구역 R석과 블록 "1층"(10행 × 20열, (120, 80), 0°)을 더해 저장한다. */
    private FloorPlan saveRSeatAndFirstFloor() {
        FloorPlan floorPlan = adapter.findByEventId(springPlay);
        floorPlan.addZone(ZoneKind.DESIGNATED, R_SEAT);
        floorPlan.addBlock(BlockName.of("1층"), BlockSize.of(10, 20), CanvasPoint.of(120, 80), BlockAngle.NONE);
        return adapter.save(floorPlan, CREATED_AT);
    }

    private static long firstBlockId(FloorPlan floorPlan) {
        return floorPlan.blocks().getFirst().id().getAsLong();
    }

    private static long rSeatId(FloorPlan floorPlan) {
        return floorPlan.zones().getFirst().id().getAsLong();
    }

    /** 그 블록에서 그 행의 앞쪽 좌석 id들. */
    private static Set<Long> seatIdsInRow(FloorPlan floorPlan, int row, int count) {
        return floorPlan.blocks().getFirst().seats().stream()
                .filter(seat -> seat.number().row() == row && seat.number().number() <= count)
                .map(seat -> seat.id().getAsLong())
                .collect(Collectors.toSet());
    }

    private static List<Long> seatIdsOf(FloorPlan floorPlan) {
        return floorPlan.blocks().stream().flatMap(block -> block.seats().stream()).map(seat -> seat.id().getAsLong()).toList();
    }

    private int liveSeatRows(long eventId) {
        return dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.EVENT_ID.eq(eventId).and(EVENT_SEAT.DELETED_AT.isNull()));
    }

    @Test
    @DisplayName("10행 × 20열 블록을 저장하면 블록 한 줄 · 좌석 200줄이 생기고 id가 붙는다 — 다시 읽으면 이름 · 크기 · 위치 · 각도와 좌석 번호가 그대로다")
    void savesTwoHundredSeats() {
        FloorPlan floorPlan = adapter.findByEventId(springPlay);
        floorPlan.addBlock(BlockName.of("1층"), BlockSize.of(10, 20), CanvasPoint.of(120, 80), BlockAngle.ofDegrees(-15));

        FloorPlan saved = adapter.save(floorPlan, CREATED_AT);

        SeatBlock savedBlock = saved.blocks().getFirst();
        assertThat(savedBlock.id()).isPresent();
        assertThat(savedBlock.seats()).hasSize(200).allSatisfy(seat -> assertThat(seat.id()).isPresent());
        assertThat(liveSeatRows(springPlay)).isEqualTo(200);
        SeatBlock found = adapter.findByEventId(springPlay).blocks().getFirst();
        assertThat(found.id()).isEqualTo(savedBlock.id());
        assertThat(found.name()).isEqualTo(BlockName.of("1층"));
        assertThat(found.size()).isEqualTo(BlockSize.of(10, 20));
        assertThat(found.position()).isEqualTo(CanvasPoint.of(120, 80));
        assertThat(found.angle()).isEqualTo(BlockAngle.ofDegrees(345));
        assertThat(found.seats()).extracting(Seat::id).containsExactlyElementsOf(savedBlock.seats().stream().map(Seat::id).toList());
        assertThat(found.seats().getFirst().number()).isEqualTo(SeatNumber.of(1, 1));
        assertThat(found.seats().getLast().number()).isEqualTo(SeatNumber.of(10, 20));
        assertThat(found.seats()).allSatisfy(seat -> {
            assertThat(seat.isActive()).isTrue();
            assertThat(seat.attribute()).isEqualTo(SeatAttribute.GENERAL);
            assertThat(seat.zoneId()).isEmpty();
        });
    }

    @Test
    @DisplayName("블록을 옮기고 돌리고 이름을 고쳐 저장해도 좌석 줄과 id는 그대로다 — 좌석 줄을 다시 만들지 않는다")
    void blockEditsKeepSeatRows() {
        FloorPlan floorPlan = saveRSeatAndFirstFloor();
        long blockId = firstBlockId(floorPlan);
        List<Long> seatIdsBefore = seatIdsOf(floorPlan);

        floorPlan.renameBlock(blockId, BlockName.of("1층 객석"));
        floorPlan.moveBlock(blockId, CanvasPoint.of(-40, 260));
        floorPlan.rotateBlock(blockId, BlockAngle.ofDegrees(15));
        adapter.save(floorPlan, CREATED_AT);

        FloorPlan found = adapter.findByEventId(springPlay);
        SeatBlock block = found.block(blockId);
        assertThat(block.name()).isEqualTo(BlockName.of("1층 객석"));
        assertThat(block.position()).isEqualTo(CanvasPoint.of(-40, 260));
        assertThat(block.angle()).isEqualTo(BlockAngle.ofDegrees(15));
        assertThat(seatIdsOf(found)).containsExactlyElementsOf(seatIdsBefore);
        assertThat(dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.EVENT_ID.eq(springPlay))).isEqualTo(200);
    }

    @Test
    @DisplayName("맨 앞줄 20석을 R석에 배정하고 2석을 휠체어로, 3석을 끄면 그 좌석만 바뀐다 — 다시 읽으면 그대로다")
    void savesChangedSeats() {
        FloorPlan floorPlan = saveRSeatAndFirstFloor();
        long rSeat = rSeatId(floorPlan);
        Set<Long> frontRow = seatIdsInRow(floorPlan, 1, 20);
        Set<Long> wheelchair = seatIdsInRow(floorPlan, 10, 2);
        Set<Long> aisle = seatIdsInRow(floorPlan, 5, 3);

        floorPlan.assignSeatsToZone(frontRow, rSeat);
        floorPlan.changeSeatAttribute(wheelchair, SeatAttribute.WHEELCHAIR);
        floorPlan.deactivateSeats(aisle);
        adapter.save(floorPlan, CREATED_AT);

        FloorPlan found = adapter.findByEventId(springPlay);
        assertThat(found.seatsIn(rSeat)).extracting(seat -> seat.id().getAsLong()).containsExactlyInAnyOrderElementsOf(frontRow);
        assertThat(wheelchair).allSatisfy(seatId -> assertThat(found.seat(seatId).attribute()).isEqualTo(SeatAttribute.WHEELCHAIR));
        assertThat(aisle).allSatisfy(seatId -> assertThat(found.seat(seatId).isActive()).isFalse());
        assertThat(dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.EVENT_ID.eq(springPlay).and(EVENT_SEAT.ZONE_ID.isNotNull()))).isEqualTo(20);
        assertThat(dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.EVENT_ID.eq(springPlay).and(EVENT_SEAT.ACTIVE.isFalse()))).isEqualTo(3);
        assertThat(dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.EVENT_ID.eq(springPlay).and(EVENT_SEAT.ATTRIBUTE.eq("WHEELCHAIR"))))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("블록을 지우면 블록과 그 좌석 200줄에 같은 지운 시각을 적고 남긴다 — 좌석의 구역은 그대로 남고, 다시 읽으면 없으며, 그 이름으로 새 블록을 얹는다")
    void removedBlockKeepsRows() {
        FloorPlan floorPlan = saveRSeatAndFirstFloor();
        long blockId = firstBlockId(floorPlan);
        long rSeat = rSeatId(floorPlan);
        floorPlan.assignSeatsToZone(seatIdsInRow(floorPlan, 1, 20), rSeat);
        adapter.save(floorPlan, CREATED_AT);

        FloorPlan reread = adapter.findByEventId(springPlay);
        reread.removeBlock(blockId);
        adapter.save(reread, REMOVED_AT);

        assertThat(dsl.select(EVENT_BLOCK.DELETED_AT).from(EVENT_BLOCK).where(EVENT_BLOCK.ID.eq(blockId)).fetchSingle().value1())
                .isEqualTo(REMOVED_AT.atOffset(ZoneOffset.UTC));
        assertThat(dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.BLOCK_ID.eq(blockId).and(EVENT_SEAT.DELETED_AT.eq(REMOVED_AT.atOffset(ZoneOffset.UTC)))))
                .isEqualTo(200);
        assertThat(dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.BLOCK_ID.eq(blockId).and(EVENT_SEAT.ZONE_ID.eq(rSeat)))).isEqualTo(20);
        FloorPlan found = adapter.findByEventId(springPlay);
        assertThat(found.blocks()).isEmpty();

        found.addBlock(BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);
        FloorPlan again = adapter.save(found, REMOVED_AT.plusSeconds(60));

        assertThat(firstBlockId(again)).isNotEqualTo(blockId);
        assertThat(liveSeatRows(springPlay)).isEqualTo(50);
        assertThat(dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.EVENT_ID.eq(springPlay))).isEqualTo(250);
    }

    @Test
    @DisplayName("구역을 지우면 같은 저장에서 그 구역의 좌석 배정이 풀린다 — 지운 구역을 가리키는 지우지 않은 좌석이 남지 않는다 (#42)")
    void removingZoneUnassignsSeatsInSameSave() {
        FloorPlan floorPlan = saveRSeatAndFirstFloor();
        long rSeat = rSeatId(floorPlan);
        floorPlan.assignSeatsToZone(seatIdsInRow(floorPlan, 1, 20), rSeat);
        adapter.save(floorPlan, CREATED_AT);

        FloorPlan reread = adapter.findByEventId(springPlay);
        reread.removeZone(rSeat);
        adapter.save(reread, REMOVED_AT);

        assertThat(dsl.select(EVENT_ZONE.DELETED_AT).from(EVENT_ZONE).where(EVENT_ZONE.ID.eq(rSeat)).fetchSingle().value1()).isNotNull();
        assertThat(dsl.fetchCount(EVENT_SEAT, EVENT_SEAT.ZONE_ID.eq(rSeat))).isZero();
        assertThat(adapter.findByEventId(springPlay).blocks().getFirst().seats()).allSatisfy(seat -> assertThat(seat.zoneId()).isEmpty());
    }

    @Test
    @DisplayName("지운 구역이 든 묵은 도면, 지운 블록 · 다른 행사의 블록, 다른 블록의 좌석이 섞인 블록은 아무것도 쓰지 않고 거부한다 — 빠진 블록도 지우지 않는다")
    void rejectsStaleOrForeignBlocksAndSeats() {
        FloorPlan floorPlan = saveRSeatAndFirstFloor();
        long blockId = firstBlockId(floorPlan);
        long rSeat = rSeatId(floorPlan);
        floorPlan.addBlock(BlockName.of("2층"), BlockSize.of(10, 20), CanvasPoint.ORIGIN, BlockAngle.NONE);
        FloorPlan twoBlocks = adapter.save(floorPlan, CREATED_AT);
        SeatBlock firstFloor = twoBlocks.block(blockId);
        SeatBlock secondFloor = twoBlocks.blocks().getLast();
        long seatToAssign = firstFloor.seats().getFirst().id().getAsLong();

        FloorPlan staleZone = adapter.findByEventId(springPlay);
        staleZone.assignSeatsToZone(Set.of(seatToAssign), rSeat);
        FloorPlan withoutZone = adapter.findByEventId(springPlay);
        withoutZone.removeZone(rSeat);
        adapter.save(withoutZone, REMOVED_AT);
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(staleZone, REMOVED_AT));

        FloorPlan foreign = FloorPlan.restore(encore, List.of(), List.of(firstFloor), null);
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(foreign, REMOVED_AT));

        List<Seat> mixedSeats = new ArrayList<>(firstFloor.seats());
        Seat borrowed = secondFloor.seats().getFirst();
        mixedSeats.set(0, Seat.restore(borrowed.id().getAsLong(), SeatNumber.of(1, 1), null, SeatAttribute.GENERAL, true));
        FloorPlan mixed = FloorPlan.restore(springPlay, List.of(), List.of(SeatBlock.restore(blockId, firstFloor.name(), firstFloor.size(),
                CanvasPoint.of(999, 999), firstFloor.angle(), mixedSeats)), null);
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(mixed, REMOVED_AT));
        assertThat(adapter.findByEventId(springPlay).blocks()).hasSize(2);

        FloorPlan withoutSecondFloor = adapter.findByEventId(springPlay);
        withoutSecondFloor.removeBlock(secondFloor.id().getAsLong());
        adapter.save(withoutSecondFloor, REMOVED_AT);
        FloorPlan staleBlock = FloorPlan.restore(springPlay, List.of(), List.of(firstFloor, secondFloor), null);
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(staleBlock, REMOVED_AT.plusSeconds(60)));

        FloorPlan found = adapter.findByEventId(springPlay);
        assertThat(found.blocks()).extracting(block -> block.id().getAsLong()).containsExactly(blockId);
        assertThat(found.block(blockId).position()).isEqualTo(CanvasPoint.of(120, 80));
        assertThat(found.seat(seatToAssign).zoneId()).isEmpty();
        assertThat(adapter.findByEventId(encore).blocks()).isEmpty();
    }

    @Test
    @DisplayName("행사마다 도면이 따로다 — 다른 행사의 블록 · 좌석은 읽지도 지우지도 않고, 같은 블록 이름을 써도 된다")
    void floorPlansAreSeparatePerEvent() {
        saveRSeatAndFirstFloor();
        FloorPlan encorePlan = adapter.findByEventId(encore);
        encorePlan.addBlock(BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);

        adapter.save(encorePlan, CREATED_AT);

        assertThat(adapter.findByEventId(encore).blocks().getFirst().seats()).hasSize(50);
        assertThat(adapter.findByEventId(springPlay).blocks().getFirst().seats()).hasSize(200);
        assertThat(liveSeatRows(springPlay)).isEqualTo(200);
    }

    @Test
    @DisplayName("50행 × 50열 블록은 좌석 2,500줄을 몇 덩어리로 나눠 넣고 모두 id를 짝짓는다 — 2,500석에 한 번에 구역을 배정한다")
    void savesLargestBlock() {
        FloorPlan floorPlan = adapter.findByEventId(springPlay);
        floorPlan.addZone(ZoneKind.DESIGNATED, R_SEAT);
        floorPlan.addBlock(BlockName.of("대공연장"), BlockSize.of(50, 50), CanvasPoint.ORIGIN, BlockAngle.NONE);

        FloorPlan saved = adapter.save(floorPlan, CREATED_AT);
        Set<Long> allSeats = Set.copyOf(seatIdsOf(saved));
        saved.assignSeatsToZone(allSeats, rSeatId(saved));
        adapter.save(saved, CREATED_AT);

        assertThat(allSeats).hasSize(2_500);
        FloorPlan found = adapter.findByEventId(springPlay);
        assertThat(found.seatsIn(rSeatId(found))).hasSize(2_500);
        assertThat(found.blocks().getFirst().seats().get(2_499).number()).isEqualTo(SeatNumber.of(50, 50));
        assertThat(found.labelOf(found.blocks().getFirst().seats().get(1_234).id().getAsLong()).value()).isEqualTo("25열 35번");
    }

    @Test
    @DisplayName("지우지 않은 블록끼리는 이름이 겹칠 수 없다 — 지운 블록과는 겹쳐도 된다 (유일 색인)")
    void blockNameUniqueAmongLiveBlocks() {
        insertBlock(springPlay, "1층", REMOVED_AT);
        insertBlock(springPlay, "1층", null);

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> insertBlock(springPlay, "1층", null))
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo("23505");
                    assertThat(rejected.getMessage()).contains("\"event_block_event_name_ux\"");
                });
    }

    private void insertBlock(long eventId, String name, Instant deletedAt) {
        dsl.insertInto(EVENT_BLOCK)
                .set(EVENT_BLOCK.EVENT_ID, eventId)
                .set(EVENT_BLOCK.NAME, name)
                .set(EVENT_BLOCK.ROW_COUNT, 5)
                .set(EVENT_BLOCK.COLUMN_COUNT, 10)
                .set(EVENT_BLOCK.POSITION_X, 0)
                .set(EVENT_BLOCK.POSITION_Y, 0)
                .set(EVENT_BLOCK.ANGLE_DEGREES, 0)
                .set(EVENT_BLOCK.DELETED_AT, deletedAt == null ? null : deletedAt.atOffset(ZoneOffset.UTC))
                .execute();
    }
}
