package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_ENTRANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.Entrance;
import com.ticketon.domain.event.EntranceName;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventEntrances;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.Venue;
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
 * 진짜 PostgreSQL에 마이그레이션(V22 입구)을 올리고 입구 목록 저장(새 입구 id · 더한 순서), 다시 읽기, 이름 바꾸기, 지운 시각을 적어 남기기, 지운
 * 입구의 이름 다시 쓰기, 행사끼리 나뉘는지, 지우지 않은 입구끼리의 이름 유일 색인을 본다. 문서의 "정문", "2층 전용", "VIP 입구"(docs/02 "입구
 * (게이트)")와 18:10의 "옆문"(docs/04). Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 입구 목록 저장소 (진짜 PostgreSQL)")
class EventEntrancePersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final Instant CREATED_AT = Instant.parse("2026-02-01T01:00:00Z");
    private static final Instant REMOVED_AT = Instant.parse("2026-03-14T09:10:00Z");

    private static CloseableDSLContext dsl;

    private EventEntrancePersistenceAdapter adapter;
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
        adapter = new EventEntrancePersistenceAdapter(dsl);
    }

    private static EventBasicInformation springPlayInformation(String title) {
        return new EventBasicInformation(EventTitle.of(title), EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), null, null,
                null);
    }

    /** 봄 정기공연에 정문 · 2층 전용 · VIP 입구를 차례로 더해 저장한다. */
    private EventEntrances saveThreeEntrances() {
        EventEntrances entrances = adapter.findByEventId(springPlay);
        entrances.add(EntranceName.of("정문"));
        entrances.add(EntranceName.of("2층 전용"));
        entrances.add(EntranceName.of("VIP 입구"));
        return adapter.save(entrances, CREATED_AT);
    }

    private static long idOf(EventEntrances entrances, String name) {
        return entrances.entrances().stream()
                .filter(entrance -> entrance.name().value().equals(name))
                .findFirst()
                .orElseThrow()
                .id()
                .getAsLong();
    }

    private static List<String> namesOf(EventEntrances entrances) {
        return entrances.entrances().stream().map(entrance -> entrance.name().value()).toList();
    }

    @Test
    @DisplayName("입구가 없는 행사의 목록은 비어 있다")
    void emptyEntrances() {
        assertThat(adapter.findByEventId(springPlay).entrances()).isEmpty();
    }

    @Test
    @DisplayName("입구 셋을 저장하면 id가 붙고, 다시 읽으면 더한 순서 그대로다")
    void savesAndReadsEntrances() {
        EventEntrances saved = saveThreeEntrances();

        assertThat(saved.entrances()).allSatisfy(entrance -> assertThat(entrance.id()).isPresent());
        EventEntrances found = adapter.findByEventId(springPlay);
        assertThat(namesOf(found)).containsExactly("정문", "2층 전용", "VIP 입구");
        assertThat(found.entrances()).extracting(entrance -> entrance.id().getAsLong())
                .containsExactly(idOf(saved, "정문"), idOf(saved, "2층 전용"), idOf(saved, "VIP 입구"));
    }

    @Test
    @DisplayName("이름을 바꿔 저장하면 같은 id로 바뀐 이름이 읽힌다")
    void savesRenamedEntrance() {
        EventEntrances entrances = saveThreeEntrances();
        long mainGate = idOf(entrances, "정문");

        entrances.rename(mainGate, EntranceName.of("정문(매표소 쪽)"));
        adapter.save(entrances, CREATED_AT);

        assertThat(adapter.findByEventId(springPlay).entrance(mainGate).name()).isEqualTo(EntranceName.of("정문(매표소 쪽)"));
    }

    @Test
    @DisplayName("목록에서 뺀 입구는 줄을 남기고 지운 시각을 적는다 — 다시 읽으면 없고, 그 이름으로 새 입구를 만들 수 있다")
    void removedEntranceKeepsRow() {
        EventEntrances entrances = saveThreeEntrances();
        long vipGate = idOf(entrances, "VIP 입구");

        entrances.remove(vipGate);
        entrances.add(EntranceName.of("옆문"));
        EventEntrances saved = adapter.save(entrances, REMOVED_AT);

        var removedRow = dsl.selectFrom(EVENT_ENTRANCE).where(EVENT_ENTRANCE.ID.eq(vipGate)).fetchSingle();
        assertThat(removedRow.getDeletedAt().toInstant()).isEqualTo(REMOVED_AT);
        assertThat(namesOf(saved)).containsExactly("정문", "2층 전용", "옆문");
        assertThat(namesOf(adapter.findByEventId(springPlay))).containsExactly("정문", "2층 전용", "옆문");

        EventEntrances found = adapter.findByEventId(springPlay);
        found.add(EntranceName.of("VIP 입구"));
        adapter.save(found, REMOVED_AT.plusSeconds(60));

        assertThat(namesOf(adapter.findByEventId(springPlay))).containsExactly("정문", "2층 전용", "옆문", "VIP 입구");
        assertThat(dsl.fetchCount(EVENT_ENTRANCE, EVENT_ENTRANCE.EVENT_ID.eq(springPlay))).isEqualTo(5);
    }

    @Test
    @DisplayName("행사마다 입구 목록이 따로다 — 다른 행사의 입구는 읽지도 지우지도 않는다")
    void entrancesAreSeparatePerEvent() {
        saveThreeEntrances();
        EventEntrances encoreEntrances = adapter.findByEventId(encore);
        encoreEntrances.add(EntranceName.of("정문"));

        adapter.save(encoreEntrances, CREATED_AT);

        assertThat(namesOf(adapter.findByEventId(encore))).containsExactly("정문");
        assertThat(namesOf(adapter.findByEventId(springPlay))).containsExactly("정문", "2층 전용", "VIP 입구");
    }

    @Test
    @DisplayName("지운 입구나 다른 행사의 입구를 저장하려 하면 아무것도 쓰지 않고 거부한다")
    void rejectsUnknownEntrances() {
        EventEntrances entrances = saveThreeEntrances();
        long vipGate = idOf(entrances, "VIP 입구");
        Entrance storedVip = entrances.entrance(vipGate);
        entrances.remove(vipGate);
        adapter.save(entrances, REMOVED_AT);

        EventEntrances stale = EventEntrances.restore(springPlay, List.of(storedVip));
        EventEntrances foreign = EventEntrances.restore(encore, List.of(entrances.entrances().getFirst()));

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(stale, REMOVED_AT.plusSeconds(60)));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(foreign, REMOVED_AT.plusSeconds(60)));

        assertThat(namesOf(adapter.findByEventId(springPlay))).containsExactly("정문", "2층 전용");
        assertThat(adapter.findByEventId(encore).entrances()).isEmpty();
    }

    @Test
    @DisplayName("지우지 않은 입구끼리는 이름이 겹칠 수 없다 — 지운 입구와는 겹쳐도 된다 (유일 색인)")
    void entranceNameUniqueAmongLiveEntrances() {
        insertEntrance("정문", null);
        insertEntrance("정문", REMOVED_AT);

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> insertEntrance("정문", null))
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo("23505");
                    assertThat(rejected.getMessage()).contains("\"event_entrance_event_name_ux\"");
                });
    }

    private void insertEntrance(String name, Instant deletedAt) {
        dsl.insertInto(EVENT_ENTRANCE)
                .set(EVENT_ENTRANCE.EVENT_ID, springPlay)
                .set(EVENT_ENTRANCE.NAME, name)
                .set(EVENT_ENTRANCE.DELETED_AT, deletedAt == null ? null : deletedAt.atOffset(ZoneOffset.UTC))
                .execute();
    }
}
