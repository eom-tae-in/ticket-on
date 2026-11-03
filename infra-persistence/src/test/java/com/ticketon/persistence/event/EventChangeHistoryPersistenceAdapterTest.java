package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.EVENT_CHANGE_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketon.application.event.query.EventChangeHistoryEntry;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventChangeAuthor;
import com.ticketon.domain.event.EventChangeHistoryTarget;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.Field;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V26 변경 이력, V27 상세 안내 · 지도 링크 · 공개용 연락처)을 올리고 변경 이력 저장소 · 조회를 본다 — 버전이 유효
 * 기간과 함께 쌓이는지(제목을 두 번 바꾸면 버전 셋), 칸마다 · 회차마다 따로 닫히는지, 행사마다 지워지는지, CHECK · 유일 색인 · 외래 키가 어긋난
 * 줄을 거부하는지. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15 박정훈)의 ○○밴드 단독공연 — 3/1 10:00에 판매를 시작했고(첫 버전), 3/14 · 3/15 저녁 공연이다. docs/03의 예시("주연: 김배우"로
 * 등록 → 3/5 예매 → 3/10 "주연: 이배우"로 수정, "○○밴드 단독공연" → "○○밴드 앵콜공연")를 옮겼다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 변경 이력 저장소 · 조회 (진짜 PostgreSQL)")
class EventChangeHistoryPersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final String CHECK_VIOLATION = "23514";

    private static final Instant PUBLISHED_AT = korean(2026, 3, 1, 10, 0);
    private static final Instant BOOKED_AT = korean(2026, 3, 5, 20, 0);
    private static final Instant FIRST_CHANGE = korean(2026, 3, 10, 14, 0);
    private static final Instant SECOND_CHANGE = korean(2026, 3, 12, 9, 0);
    private static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);
    private static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);
    private static final String POSTER_KEY = "Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg";
    private static final String MAP_LINK = "https://map.example/place/12";
    private static final String PUBLIC_CONTACT = "twelve-months@example.com";

    private static CloseableDSLContext dsl;

    private EventChangeHistoryPersistenceAdapter adapter;
    private EventChangeHistoryQueryPersistenceAdapter query;
    private EventPersistenceAdapter events;
    private EventSchedulePersistenceAdapter schedules;
    private long twelveMonths;
    private long band;
    private long marchFourteenth;
    private long marchFifteenth;
    private EventChangeAuthor junghoon;
    private EventChangeAuthor planner;
    private int addressSequence;

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
    void storeBand() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        junghoon = new EventChangeAuthor(rows.staffAccount("junghoon@example.com"), Nickname.of("조용한고래"));
        planner = new EventChangeAuthor(rows.staffAccount("planner@example.com"), Nickname.of("푸른바람"));
        events = new EventPersistenceAdapter(dsl);
        schedules = new EventSchedulePersistenceAdapter(dsl);
        adapter = new EventChangeHistoryPersistenceAdapter(dsl);
        query = new EventChangeHistoryQueryPersistenceAdapter(dsl);
        addressSequence = 0;
        band = addEvent("○○밴드 단독공연");
        EventSchedule schedule = EventSchedule.timed(band);
        schedule.addSession(MARCH_14, TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)));
        schedule.addSession(MARCH_15, TimedSessionTime.startingAt(LocalTime.of(18, 30)));
        EventSchedule saved = schedules.save(schedule, PUBLISHED_AT);
        marchFourteenth = saved.requireDate(MARCH_14).sessions().getFirst().id().getAsLong();
        marchFifteenth = saved.requireDate(MARCH_15).sessions().getFirst().id().getAsLong();
    }

    /** 3/1 10:00 판매 시작 — 제목 · 소개 · 포스터와 3/14의 "주연: 김배우". */
    private void publishBand() {
        adapter.add(band, List.of(
                new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, null, "○○밴드 단독공연"),
                new EventChangeHistoryVersion(EventChangeHistoryTarget.INTRODUCTION, null, null, "주연: 김배우"),
                new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, null, POSTER_KEY),
                new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, marchFourteenth, null, "주연: 김배우")),
                junghoon, PUBLISHED_AT);
    }

    private static EventChangeHistoryVersion title(String before, String after) {
        return new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, before, after);
    }

    private List<EventChangeHistoryEntry> entriesOf(long eventId, EventChangeHistoryTarget target) {
        return query.findByEventId(eventId).stream().filter(entry -> entry.target() == target).toList();
    }

    @Nested
    @DisplayName("쌓기 · 조회")
    class AddAndRead {

        @Test
        @DisplayName("제목을 두 번 바꾸면 버전이 셋이다 — 3/1~3/10 · 3/10~3/12 · 3/12~(지금), 3/5 예매 때는 첫 버전이 보였다")
        void titleChangedTwiceHasThreeVersions() {
            publishBand();

            adapter.add(band, List.of(title("○○밴드 단독공연", "○○밴드 앵콜공연")), planner, FIRST_CHANGE);
            adapter.add(band, List.of(title("○○밴드 앵콜공연", "○○밴드 앵콜공연 — 추가 회차")), junghoon, SECOND_CHANGE);

            List<EventChangeHistoryEntry> titles = entriesOf(band, EventChangeHistoryTarget.TITLE);
            assertThat(titles).extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent,
                    EventChangeHistoryEntry::validFrom, EventChangeHistoryEntry::validUntil, EventChangeHistoryEntry::changedByNickname)
                    .containsExactly(
                            tuple(null, "○○밴드 단독공연", PUBLISHED_AT, FIRST_CHANGE, "조용한고래"),
                            tuple("○○밴드 단독공연", "○○밴드 앵콜공연", FIRST_CHANGE, SECOND_CHANGE, "푸른바람"),
                            tuple("○○밴드 앵콜공연", "○○밴드 앵콜공연 — 추가 회차", SECOND_CHANGE, null,
                                    "조용한고래"));
            assertThat(titles).filteredOn(entry -> entry.isValidAt(BOOKED_AT)).extracting(EventChangeHistoryEntry::newContent)
                    .containsExactly("○○밴드 단독공연");
            assertThat(titles).filteredOn(entry -> entry.isValidAt(FIRST_CHANGE)).extracting(EventChangeHistoryEntry::newContent)
                    .containsExactly("○○밴드 앵콜공연");
            assertThat(titles).allSatisfy(entry -> {
                assertThat(entry.noticeSent()).isFalse();
                assertThat(entry.sessionId()).isNull();
            });
            assertThat(titles.get(1).changedByStaffAccountId()).isEqualTo(planner.staffAccountId());
        }

        @Test
        @DisplayName("한 칸을 바꿔도 다른 칸의 지금 버전은 닫히지 않는다 — 제목만 바꾸면 소개 · 포스터는 그대로 지금 버전이다")
        void closesOnlyThatTarget() {
            publishBand();

            adapter.add(band, List.of(title("○○밴드 단독공연", "○○밴드 앵콜공연")), planner, FIRST_CHANGE);

            assertThat(entriesOf(band, EventChangeHistoryTarget.INTRODUCTION)).singleElement()
                    .satisfies(entry -> assertThat(entry.validUntil()).isNull());
            assertThat(entriesOf(band, EventChangeHistoryTarget.POSTER)).singleElement()
                    .satisfies(entry -> assertThat(entry.newContent()).isEqualTo(POSTER_KEY));
        }

        @Test
        @DisplayName("회차 출연은 회차마다 따로 닫힌다 — 3/14를 \"주연: 이배우\"로 고치고 비운 3/15에 적으면, 3/14만 버전이 둘이고 회차 날짜 · 시각이 함께 읽힌다")
        void performersPerSession() {
            publishBand();

            adapter.add(band, List.of(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, marchFourteenth, "주연: 김배우", "주연: 이배우"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, marchFifteenth, null, "주연: 김배우")),
                    planner, FIRST_CHANGE);

            List<EventChangeHistoryEntry> performers = entriesOf(band, EventChangeHistoryTarget.SESSION_PERFORMERS);
            assertThat(performers).extracting(EventChangeHistoryEntry::sessionId, EventChangeHistoryEntry::sessionDate,
                    EventChangeHistoryEntry::sessionStartTime, EventChangeHistoryEntry::newContent, EventChangeHistoryEntry::validUntil)
                    .containsExactly(
                            tuple(marchFourteenth, MARCH_14, LocalTime.of(19, 0), "주연: 김배우", FIRST_CHANGE),
                            tuple(marchFourteenth, MARCH_14, LocalTime.of(19, 0), "주연: 이배우", null),
                            tuple(marchFifteenth, MARCH_15, LocalTime.of(18, 30), "주연: 김배우", null));
        }

        @Test
        @DisplayName("출연을 비운 버전은 바꾼 뒤 내용이 없다")
        void clearedPerformers() {
            publishBand();

            adapter.add(band, List.of(new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, marchFourteenth,
                    "주연: 김배우", null)), planner, FIRST_CHANGE);

            assertThat(entriesOf(band, EventChangeHistoryTarget.SESSION_PERFORMERS)).last()
                    .satisfies(entry -> assertThat(entry.newContent()).isNull());
        }

        @Test
        @DisplayName("상세 안내 · 지도 링크 · 공개용 연락처도 칸마다 쌓이고 닫힌다 — \"주차 불가\"를 넣고 지도 링크를 비우면 그 두 칸만 버전이 둘이다")
        void venueGuideVersions() {
            adapter.add(band, List.of(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, null, "○○밴드 단독공연"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.VENUE_DETAILS, null, null, "지하 1층"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, null, MAP_LINK),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.PUBLIC_CONTACT, null, null, PUBLIC_CONTACT)),
                    junghoon, PUBLISHED_AT);

            adapter.add(band, List.of(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.VENUE_DETAILS, null, "지하 1층", "지하 1층, 주차 불가"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, MAP_LINK, null)),
                    planner, FIRST_CHANGE);

            assertThat(entriesOf(band, EventChangeHistoryTarget.VENUE_DETAILS))
                    .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent,
                            EventChangeHistoryEntry::validFrom, EventChangeHistoryEntry::validUntil, EventChangeHistoryEntry::sessionId)
                    .containsExactly(
                            tuple(null, "지하 1층", PUBLISHED_AT, FIRST_CHANGE, null),
                            tuple("지하 1층", "지하 1층, 주차 불가", FIRST_CHANGE, null, null));
            assertThat(entriesOf(band, EventChangeHistoryTarget.MAP_LINK))
                    .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent,
                            EventChangeHistoryEntry::validUntil)
                    .containsExactly(tuple(null, MAP_LINK, FIRST_CHANGE), tuple(MAP_LINK, null, null));
            assertThat(entriesOf(band, EventChangeHistoryTarget.PUBLIC_CONTACT)).singleElement().satisfies(entry -> {
                assertThat(entry.newContent()).isEqualTo(PUBLIC_CONTACT);
                assertThat(entry.validUntil()).isNull();
            });
            assertThat(entriesOf(band, EventChangeHistoryTarget.TITLE)).singleElement()
                    .satisfies(entry -> assertThat(entry.validUntil()).isNull());
        }

        @Test
        @DisplayName("한 번에 같은 칸을 두 번 쌓지 않는다 — 아무것도 넣지 않고 막는다")
        void rejectsSameItemTwice() {
            assertThatIllegalArgumentException().isThrownBy(() -> adapter.add(band,
                    List.of(title("○○밴드 단독공연", "○○밴드 앵콜공연"), title("○○밴드 앵콜공연", "○○밴드 단독공연")), planner, FIRST_CHANGE));

            assertThat(adapter.existsByEventId(band)).isFalse();
        }

        @Test
        @DisplayName("이력이 있는지 · 이력이 있는 행사 id를 행사 id 차례로 — 다른 행사의 이력은 섞이지 않는다")
        void existsAndEventIds() {
            long encore = addEvent("○○밴드 앵콜공연");
            assertThat(adapter.existsByEventId(band)).isFalse();
            assertThat(adapter.findEventIdsWithHistory()).isEmpty();

            adapter.add(encore, List.of(title(null, "○○밴드 앵콜공연")), junghoon, PUBLISHED_AT);
            publishBand();

            assertThat(adapter.existsByEventId(band)).isTrue();
            assertThat(adapter.findEventIdsWithHistory()).containsExactly(band, encore);
            assertThat(query.findByEventId(encore)).singleElement()
                    .satisfies(entry -> assertThat(entry.newContent()).isEqualTo("○○밴드 앵콜공연"));
        }
    }

    @Nested
    @DisplayName("지우기")
    class Delete {

        @Test
        @DisplayName("그 행사의 이력만 모두 지우고 지운 버전을 쌓은 차례로 돌려준다 — 바꾸기 전 포스터 열쇠가 담긴다")
        void deletesOnlyThatEvent() {
            long encore = addEvent("○○밴드 앵콜공연");
            adapter.add(encore, List.of(title(null, "○○밴드 앵콜공연")), junghoon, PUBLISHED_AT);
            publishBand();
            String newPoster = "Nw7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.png";
            adapter.add(band, List.of(new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, POSTER_KEY, newPoster)),
                    planner, FIRST_CHANGE);

            List<EventChangeHistoryVersion> deleted = adapter.deleteByEventId(band);

            assertThat(deleted).hasSize(5);
            assertThat(deleted.getFirst()).isEqualTo(new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, null, "○○밴드 단독공연"));
            assertThat(deleted.getLast())
                    .isEqualTo(new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, POSTER_KEY, newPoster));
            assertThat(query.findByEventId(band)).isEmpty();
            assertThat(adapter.existsByEventId(encore)).isTrue();
            assertThat(adapter.findEventIdsWithHistory()).containsExactly(encore);
            assertThat(adapter.deleteByEventId(band)).isEmpty();
        }

        @Test
        @DisplayName("상세 안내 · 지도 링크 · 공개용 연락처 버전도 함께 지우고 돌려준다 — 비운 버전도 그대로 읽힌다")
        void deletesVenueGuideVersions() {
            publishBand();
            adapter.add(band, List.of(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.VENUE_DETAILS, null, null, "지하 1층, 주차 불가"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, null, MAP_LINK),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.PUBLIC_CONTACT, null, null, PUBLIC_CONTACT)),
                    planner, FIRST_CHANGE);
            adapter.add(band, List.of(new EventChangeHistoryVersion(EventChangeHistoryTarget.PUBLIC_CONTACT, null, PUBLIC_CONTACT, null)),
                    junghoon, SECOND_CHANGE);

            List<EventChangeHistoryVersion> deleted = adapter.deleteByEventId(band);

            assertThat(deleted).hasSize(8);
            assertThat(deleted.subList(4, 8)).containsExactly(
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.VENUE_DETAILS, null, null, "지하 1층, 주차 불가"),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, null, MAP_LINK),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.PUBLIC_CONTACT, null, null, PUBLIC_CONTACT),
                    new EventChangeHistoryVersion(EventChangeHistoryTarget.PUBLIC_CONTACT, null, PUBLIC_CONTACT, null));
            assertThat(query.findByEventId(band)).isEmpty();
        }

        @Test
        @DisplayName("지우기 전 잠금은 행사 줄이 있으면 잡는다 — 지운 행사도 잡고, 없는 행사는 false")
        void locksEventRowForPurge() {
            dsl.update(EVENT).set(EVENT.DELETED_AT, OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                    .where(EVENT.ID.eq(band)).execute();

            boolean locked = dsl.transactionResult(configuration ->
                    new EventChangeHistoryPersistenceAdapter(DSL.using(configuration)).lockEventForPurge(band));

            assertThat(locked).isTrue();
            assertThat(adapter.lockEventForPurge(band + 100)).isFalse();
        }
    }

    @Nested
    @DisplayName("표가 막는 줄")
    class Constraints {

        @Test
        @DisplayName("대상 칸 · 회차 · 내용 · 유효 기간 · 닉네임 CHECK가 어긋난 줄을 그 이름으로 거부한다")
        void checks() {
            assertRejectedBy("event_change_history_target_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.TARGET, "BLOCK_NAME")));
            assertRejectedBy("event_change_history_session_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.SESSION_ID, marchFourteenth)));
            assertRejectedBy("event_change_history_session_ck", () -> insert(values(EVENT_CHANGE_HISTORY.TARGET, "SESSION_PERFORMERS",
                    EVENT_CHANGE_HISTORY.SESSION_ID, null)));
            assertRejectedBy("event_change_history_new_content_ck", () -> insert(values(EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT,
                    "○○밴드 단독공연", EVENT_CHANGE_HISTORY.NEW_CONTENT, null)));
            assertRejectedBy("event_change_history_content_not_empty_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.NEW_CONTENT, "")));
            assertRejectedBy("event_change_history_content_not_empty_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT, "")));
            assertRejectedBy("event_change_history_changed_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT,
                    "○○밴드 단독공연")));
            assertRejectedBy("event_change_history_changed_ck", () -> insert(values(EVENT_CHANGE_HISTORY.TARGET, "SESSION_PERFORMERS",
                    EVENT_CHANGE_HISTORY.SESSION_ID, marchFourteenth, EVENT_CHANGE_HISTORY.NEW_CONTENT, null)));
            assertRejectedBy("event_change_history_period_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.VALID_UNTIL,
                    PUBLISHED_AT.minusSeconds(1).atOffset(ZoneOffset.UTC))));
            assertRejectedBy("event_change_history_changed_by_nickname_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.CHANGED_BY_NICKNAME,
                    "")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"VENUE_DETAILS", "MAP_LINK", "PUBLIC_CONTACT"})
        @DisplayName("상세 안내 · 지도 링크 · 공개용 연락처 줄을 받고, 비운 버전(바꾼 뒤 내용 NULL)도 받는다 — 회차를 가리키면 거부한다 (V27)")
        void acceptsVenueGuideTargets(String target) {
            String content = switch (EventChangeHistoryTarget.valueOf(target)) {
                case MAP_LINK -> MAP_LINK;
                case PUBLIC_CONTACT -> PUBLIC_CONTACT;
                default -> "지하 1층, 주차 불가";
            };
            insert(values(EVENT_CHANGE_HISTORY.TARGET, target, EVENT_CHANGE_HISTORY.NEW_CONTENT, content,
                    EVENT_CHANGE_HISTORY.VALID_UNTIL, FIRST_CHANGE.atOffset(ZoneOffset.UTC)));
            insert(values(EVENT_CHANGE_HISTORY.TARGET, target, EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT, content,
                    EVENT_CHANGE_HISTORY.NEW_CONTENT, null, EVENT_CHANGE_HISTORY.VALID_FROM, FIRST_CHANGE.atOffset(ZoneOffset.UTC)));

            assertThat(entriesOf(band, EventChangeHistoryTarget.valueOf(target)))
                    .extracting(EventChangeHistoryEntry::previousContent, EventChangeHistoryEntry::newContent)
                    .containsExactly(tuple(null, content), tuple(content, null));
            assertRejectedBy("event_change_history_session_ck", () -> insert(values(EVENT_CHANGE_HISTORY.TARGET, target,
                    EVENT_CHANGE_HISTORY.SESSION_ID, marchFourteenth, EVENT_CHANGE_HISTORY.VALID_UNTIL,
                    FIRST_CHANGE.atOffset(ZoneOffset.UTC))));
            assertRejectedBy("event_change_history_changed_ck", () -> insert(values(EVENT_CHANGE_HISTORY.TARGET, target,
                    EVENT_CHANGE_HISTORY.NEW_CONTENT, null, EVENT_CHANGE_HISTORY.VALID_UNTIL, FIRST_CHANGE.atOffset(ZoneOffset.UTC))));
        }

        @Test
        @DisplayName("대상 칸 CHECK는 V27 뒤에도 모르는 칸을 거부하고, 제목 · 소개 · 포스터의 비운 버전은 바꾼 뒤 내용 CHECK가 여전히 거부한다")
        void stillRejectsUnknownTargetAndEmptyRequiredContent() {
            assertRejectedBy("event_change_history_target_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.TARGET, "VENUE")));
            assertRejectedBy("event_change_history_target_ck", () -> insert(Map.of(EVENT_CHANGE_HISTORY.TARGET, "venue_details")));
            for (String required : List.of("TITLE", "INTRODUCTION", "POSTER")) {
                assertRejectedBy("event_change_history_new_content_ck", () -> insert(values(EVENT_CHANGE_HISTORY.TARGET, required,
                        EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT, "○○밴드 단독공연", EVENT_CHANGE_HISTORY.NEW_CONTENT, null)));
            }
            assertThat(query.findByEventId(band)).isEmpty();
        }

        @Test
        @DisplayName("한 칸(회차 출연은 회차마다)에 지금 버전은 하나다 — 닫힌 버전은 여럿이어도 된다")
        void oneCurrentVersionPerItem() {
            insert(Map.of());
            assertUniqueRejected("event_change_history_current_event_target_ux", () -> insert(Map.of()));
            insert(Map.of(EVENT_CHANGE_HISTORY.VALID_UNTIL, FIRST_CHANGE.atOffset(ZoneOffset.UTC)));

            Map<Field<?>, Object> performers = values(EVENT_CHANGE_HISTORY.TARGET, "SESSION_PERFORMERS", EVENT_CHANGE_HISTORY.SESSION_ID,
                    marchFourteenth, EVENT_CHANGE_HISTORY.NEW_CONTENT, "주연: 김배우");
            insert(performers);
            assertUniqueRejected("event_change_history_current_session_ux", () -> insert(performers));
            insert(values(EVENT_CHANGE_HISTORY.TARGET, "SESSION_PERFORMERS", EVENT_CHANGE_HISTORY.SESSION_ID, marchFifteenth,
                    EVENT_CHANGE_HISTORY.NEW_CONTENT, "주연: 김배우"));
        }

        @Test
        @DisplayName("다른 행사의 회차를 가리키는 줄은 event_change_history_session_fk가 거부한다")
        void sessionMustBelongToSameEvent() {
            long encore = addEvent("○○밴드 앵콜공연");

            assertForeignKeyRejected("event_change_history_session_fk", () -> insert(values(EVENT_CHANGE_HISTORY.EVENT_ID, encore,
                    EVENT_CHANGE_HISTORY.TARGET, "SESSION_PERFORMERS", EVENT_CHANGE_HISTORY.SESSION_ID, marchFourteenth)));
        }

        /** 봄 정기공연의 제목 첫 버전(박정훈, 3/1 10:00, 지금 버전) 한 줄에 그 칸들만 덮어써 넣는다. */
        private void insert(Map<Field<?>, Object> overrides) {
            Map<Field<?>, Object> row = new HashMap<>();
            row.put(EVENT_CHANGE_HISTORY.EVENT_ID, band);
            row.put(EVENT_CHANGE_HISTORY.TARGET, "TITLE");
            row.put(EVENT_CHANGE_HISTORY.SESSION_ID, null);
            row.put(EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT, null);
            row.put(EVENT_CHANGE_HISTORY.NEW_CONTENT, "○○밴드 단독공연");
            row.put(EVENT_CHANGE_HISTORY.VALID_FROM, PUBLISHED_AT.atOffset(ZoneOffset.UTC));
            row.put(EVENT_CHANGE_HISTORY.VALID_UNTIL, null);
            row.put(EVENT_CHANGE_HISTORY.CHANGED_BY_STAFF_ACCOUNT_ID, junghoon.staffAccountId());
            row.put(EVENT_CHANGE_HISTORY.CHANGED_BY_NICKNAME, "조용한고래");
            row.put(EVENT_CHANGE_HISTORY.NOTICE_SENT, false);
            row.putAll(overrides);
            dsl.insertInto(EVENT_CHANGE_HISTORY).set(row).execute();
        }
    }

    private long addEvent(String title) {
        addressSequence++;
        String address = "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6f" + String.format("%02d", addressSequence);
        return events.add(Event.create(twelveMonths, new EventPublicAddress(address), information(title), PUBLISHED_AT)).id()
                .getAsLong();
    }

    private static EventBasicInformation information(String title) {
        return new EventBasicInformation(EventTitle.of(title), EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "콘서트", null), "주연: 김배우",
                new Venue("○○라이브홀", "서울특별시 마포구 와우산로 1"), null, null, null);
    }

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    /** 칸 · 값을 번갈아 받아 덮어쓸 칸들을 만든다 — 값에 null을 넣을 수 있다(Map.of는 null을 받지 않는다). */
    private static Map<Field<?>, Object> values(Object... fieldsAndValues) {
        Map<Field<?>, Object> values = new HashMap<>();
        for (int index = 0; index < fieldsAndValues.length; index += 2) {
            values.put((Field<?>) fieldsAndValues[index], fieldsAndValues[index + 1]);
        }
        return values;
    }

    private static void assertRejectedBy(String constraint, Runnable insert) {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(insert::run)
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo(CHECK_VIOLATION);
                    assertThat(rejected.getMessage()).contains("\"" + constraint + "\"");
                });
    }

    private static void assertUniqueRejected(String index, Runnable insert) {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(insert::run)
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo("23505");
                    assertThat(rejected.getMessage()).contains("\"" + index + "\"");
                });
    }

    private static void assertForeignKeyRejected(String constraint, Runnable insert) {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(insert::run)
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo("23503");
                    assertThat(rejected.getMessage()).contains("\"" + constraint + "\"");
                });
    }
}
