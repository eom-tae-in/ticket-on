package com.ticketon.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.event.SessionAdded;
import com.ticketon.domain.event.SessionRemoved;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 위저드 2단계의 회차 방식 · 날짜 · 회차 · 운영 시간 · 출연 · 회차 가격
 * 조정을 저장 · 조회하는 흐름을 끝까지 돌린다(#41 완료 조건). 회차형 공연 "3/14 15:00 · 19:00"(docs/02 "날짜와 회차"), 종일형 페스티벌 "5/3 · 5/4 ·
 * 5/5"와 기본 운영 시간 "10:00~18:00"(docs/02), 주말만 늦게까지 여는 날의 덮어쓰기(docs/02 "날짜마다 운영 시간이 다를 수 있습니다"), 출연 "김배우 ·
 * 박배우"(docs/02), "+10,000원 · -20%"(docs/05). 회차가 생기고 지워진 것을 재고가 들을 수 있게 애플리케이션 이벤트로 알리는지는 이 테스트의 듣는 쪽
 * ({@link SessionNotices})이 본다(#54). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원, 늦여름의 소유자 최여름. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인과 행사
 * 만들기는 실제 API로 한다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({LoginTestController.class, EventScheduleIntegrationTest.SessionNotices.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("일정 저장과 조회 — 회차 방식 · 날짜 · 회차 (진짜 PostgreSQL · 실제 서버)")
class EventScheduleIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "junghoon1234";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashPort passwordHashPort;

    @Autowired
    private SessionNotices sessionNotices;

    @LocalServerPort
    private int port;

    private String junghoonEmail;
    private String plannerEmail;
    private String yeoreumEmail;
    private long planner;
    private long twelveMonths;
    private long lateSummer;

    /** 회차가 생기고 지워졌다는 알림을 듣는 쪽 — 재고(#54) 자리에서 받은 알림을 차례대로 모은다. */
    @TestComponent
    static class SessionNotices {

        private final List<Object> received = new CopyOnWriteArrayList<>();

        @EventListener
        void sessionAdded(SessionAdded notice) {
            received.add(notice);
        }

        @EventListener
        void sessionRemoved(SessionRemoved notice) {
            received.add(notice);
        }

        /** 그 행사에 대해 받은 알림 — 받은 차례대로. */
        List<Object> about(long eventId) {
            return received.stream().filter(notice -> eventIdOf(notice) == eventId).toList();
        }

        private static long eventIdOf(Object notice) {
            return switch (notice) {
                case SessionAdded added -> added.eventId();
                case SessionRemoved removed -> removed.eventId();
                default -> throw new IllegalStateException("모르는 알림: " + notice);
            };
        }
    }

    @BeforeEach
    void storeOrganizers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        plannerEmail = "planner-" + suffix + "@example.com";
        yeoreumEmail = "yeoreum-" + suffix + "@example.com";
        long junghoon = storeStaff("고래" + suffix, junghoonEmail);
        planner = storeStaff("바람" + suffix, plannerEmail);
        long yeoreum = storeStaff("여름" + suffix, yeoreumEmail);
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
    }

    @Test
    @DisplayName("운영자가 회차형을 고르고 3/14 15:00~17:00 · 19:00~21:00 두 회차를 만들면, 소유자가 다시 열었을 때 그대로이고 진행 단계는 2단계다")
    void createsTwoTimedSessions() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);

            HttpResponse<String> chosen = operator.put(schedulePath(twelveMonths, eventId) + "/session-mode", Map.of("sessionMode", "TIMED"));
            assertThat(chosen.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(chosen.body()).get("sessionMode").asString()).isEqualTo("TIMED");

            HttpResponse<String> evening = operator.post(schedulePath(twelveMonths, eventId) + "/sessions",
                    sessionBody("2026-03-14", "19:00", "21:00"));
            assertThat(evening.statusCode()).isEqualTo(201);
            HttpResponse<String> matinee = operator.post(schedulePath(twelveMonths, eventId) + "/sessions",
                    sessionBody("2026-03-14", "15:00", "17:00"));
            assertThat(matinee.statusCode()).isEqualTo(201);
        }
        JsonNode schedule;
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            HttpResponse<String> read = owner.get(schedulePath(twelveMonths, eventId));

            assertThat(read.statusCode()).isEqualTo(200);
            schedule = jsonMapper.readTree(read.body());
            JsonNode wizard = jsonMapper.readTree(owner.get(eventPath(twelveMonths, eventId)).body());
            assertThat(wizard.get("wizardProgress").get("step").asString()).isEqualTo("SCHEDULE");
        }
        assertThat(schedule.get("sessionMode").asString()).isEqualTo("TIMED");
        assertThat(schedule.get("defaultOperatingHours").isNull()).isTrue();
        JsonNode dates = schedule.get("dates");
        assertThat(dates).hasSize(1);
        assertThat(dates.get(0).get("date").asString()).isEqualTo("2026-03-14");
        assertThat(dates.get(0).get("operatingHours").isNull()).isTrue();
        JsonNode sessions = dates.get(0).get("sessions");
        assertThat(sessions).extracting(session -> session.get("startTime").asString() + "~" + session.get("endTime").asString())
                .containsExactly("15:00~17:00", "19:00~21:00");
        assertThat(sessions).allSatisfy(session -> {
            assertThat(session.get("performers").isNull()).isTrue();
            assertThat(session.get("priceAdjustment").get("kind").asString()).isEqualTo("NONE");
            assertThat(session.get("cancelled").asBoolean()).isFalse();
        });
        long matineeId = sessions.get(0).get("sessionId").asLong();
        long eveningId = sessions.get(1).get("sessionId").asLong();
        assertThat(jdbcTemplate.queryForMap("SELECT start_time::text AS start_time, end_time::text AS end_time, deleted_at FROM "
                + "event_session WHERE id = ?", matineeId))
                .containsEntry("start_time", "15:00:00").containsEntry("end_time", "17:00:00").containsEntry("deleted_at", null);
        assertThat(sessionNotices.about(eventId))
                .containsExactly(new SessionAdded(eventId, eveningId), new SessionAdded(eventId, matineeId));
        assertThat(eventOperations(twelveMonths)).containsExactly("CREATE_EVENT 행사 만들기 " + planner,
                "EDIT_EVENT 회차 방식 · 회차형 (2단계) " + planner, "EDIT_EVENT 회차 추가 · 2026-03-14 19:00 (2단계) " + planner,
                "EDIT_EVENT 회차 추가 · 2026-03-14 15:00 (2단계) " + planner);
    }

    @Test
    @DisplayName("종일형 5/3 · 5/4 · 5/5를 기본 10:00~18:00으로 만들고 5/4만 10:00~20:00으로 덮어쓰면, 조회에서 날짜마다 실제 운영 시간이 보인다")
    void createsThreeAllDayDatesAndOverridesOne() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);
            assertThat(operator.put(schedulePath(twelveMonths, eventId) + "/session-mode", allDayBody("10:00", "18:00")).statusCode())
                    .isEqualTo(200);
            for (String date : List.of("2026-05-03", "2026-05-04", "2026-05-05")) {
                assertThat(operator.post(schedulePath(twelveMonths, eventId) + "/dates", Map.of("date", date)).statusCode())
                        .isEqualTo(201);
            }

            HttpResponse<String> overridden = operator.put(schedulePath(twelveMonths, eventId) + "/dates/2026-05-04/operating-hours",
                    Map.of("openTime", "10:00", "closeTime", "20:00", "lastEntryTime", "19:00"));

            assertThat(overridden.statusCode()).isEqualTo(200);
        }
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            JsonNode schedule = jsonMapper.readTree(owner.get(schedulePath(twelveMonths, eventId)).body());

            assertThat(schedule.get("sessionMode").asString()).isEqualTo("ALL_DAY");
            assertThat(schedule.get("defaultOperatingHours").get("openTime").asString()).isEqualTo("10:00");
            assertThat(schedule.get("defaultOperatingHours").get("closeTime").asString()).isEqualTo("18:00");
            JsonNode dates = schedule.get("dates");
            assertThat(dates).extracting(date -> date.get("date").asString()).containsExactly("2026-05-03", "2026-05-04", "2026-05-05");
            assertThat(dates).allSatisfy(date -> assertThat(date.get("sessions")).singleElement()
                    .satisfies(session -> assertThat(session.get("startTime").isNull()).isTrue()));
            assertThat(dates.get(0).get("operatingHours").get("closeTime").asString()).isEqualTo("18:00");
            assertThat(dates.get(0).get("operatingHoursOverride").isNull()).isTrue();
            assertThat(dates.get(1).get("operatingHours").get("closeTime").asString()).isEqualTo("20:00");
            assertThat(dates.get(1).get("operatingHours").get("lastEntryTime").asString()).isEqualTo("19:00");
            assertThat(dates.get(1).get("operatingHoursOverride").get("closeTime").asString()).isEqualTo("20:00");
            assertThat(dates.get(2).get("operatingHours").get("closeTime").asString()).isEqualTo("18:00");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_session WHERE event_id = ? AND deleted_at IS NULL",
                Long.class, eventId)).isEqualTo(3);
        assertThat(sessionNotices.about(eventId)).hasSize(3).allSatisfy(notice -> assertThat(notice).isInstanceOf(SessionAdded.class));
        assertThat(eventOperations(twelveMonths)).last()
                .isEqualTo("EDIT_EVENT 운영 시간 덮어쓰기 · 2026-05-04 10:00~20:00 (마지막 입장 19:00) (2단계) " + planner);
    }

    @Test
    @DisplayName("종일형 날짜에 시간을 정한 회차를 더하면 409이고 아무것도 남지 않는다 — 시간을 쪼개려면 회차형")
    void rejectsTimedSessionOnAllDayDate() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);
            operator.put(schedulePath(twelveMonths, eventId) + "/session-mode", allDayBody("10:00", "18:00"));
            operator.post(schedulePath(twelveMonths, eventId) + "/dates", Map.of("date", "2026-05-03"));

            assertError(operator.post(schedulePath(twelveMonths, eventId) + "/sessions", sessionBody("2026-05-03", "10:00", "12:00")),
                    409, "ALL_DAY_SINGLE_SESSION_PER_DATE");
            assertError(operator.post(schedulePath(twelveMonths, eventId) + "/sessions", sessionBody("2026-05-04", "14:00", null)),
                    409, "ALL_DAY_SINGLE_SESSION_PER_DATE");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_session WHERE event_id = ?", Long.class, eventId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_date WHERE event_id = ?", Long.class, eventId))
                .isEqualTo(1);
        assertThat(sessionNotices.about(eventId)).hasSize(1);
    }

    @Test
    @DisplayName("출연 · 가격 조정 · 회차 일시를 저장하면 다시 읽을 때 그대로이고, 같은 값을 다시 보내면 200에 같은 일정을 돌려주며 조작 기록도 마지막 수정 시각도 그대로다")
    void savesSessionDetailsAndIgnoresSameValues() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay(twelveMonths);
            String schedule = schedulePath(twelveMonths, eventId);
            operator.put(schedule + "/session-mode", Map.of("sessionMode", "TIMED"));
            operator.post(schedule + "/sessions", sessionBody("2026-03-14", "15:00", "17:00"));
            JsonNode added = jsonMapper.readTree(operator.post(schedule + "/sessions", sessionBody("2026-03-14", "19:00", null)).body());
            long matinee = added.get("dates").get(0).get("sessions").get(0).get("sessionId").asLong();
            long evening = added.get("dates").get(0).get("sessions").get(1).get("sessionId").asLong();

            assertThat(operator.put(schedule + "/sessions/" + evening + "/performers", Map.of("performers", " 김배우 · 박배우 "))
                    .statusCode()).isEqualTo(200);
            assertThat(operator.put(schedule + "/sessions/" + evening + "/price-adjustment", Map.of("kind", "AMOUNT", "amount", 10_000))
                    .statusCode()).isEqualTo(200);
            assertThat(operator.put(schedule + "/sessions/" + matinee + "/price-adjustment", Map.of("kind", "PERCENT", "percent", -20))
                    .statusCode()).isEqualTo(200);
            HttpResponse<String> moved = operator.put(schedule + "/sessions/" + evening + "/date-time",
                    sessionBody("2026-03-15", "19:00", "21:00"));
            assertThat(moved.statusCode()).isEqualTo(200);

            JsonNode read = jsonMapper.readTree(operator.get(schedule).body());
            assertThat(read.get("dates")).extracting(date -> date.get("date").asString()).containsExactly("2026-03-14", "2026-03-15");
            JsonNode eveningRead = read.get("dates").get(1).get("sessions").get(0);
            assertThat(eveningRead.get("sessionId").asLong()).isEqualTo(evening);
            assertThat(eveningRead.get("endTime").asString()).isEqualTo("21:00");
            assertThat(eveningRead.get("performers").asString()).isEqualTo("김배우 · 박배우");
            assertThat(eveningRead.get("priceAdjustment").get("amount").asLong()).isEqualTo(10_000);
            assertThat(read.get("dates").get(0).get("sessions").get(0).get("priceAdjustment").get("percent").asInt()).isEqualTo(-20);
            List<String> operationsBefore = eventOperations(twelveMonths);
            String lastModifiedBefore = lastModifiedAt(eventId);

            List<HttpResponse<String>> resent = List.of(
                    operator.put(schedule + "/session-mode", Map.of("sessionMode", "TIMED")),
                    operator.put(schedule + "/sessions/" + evening + "/performers", Map.of("performers", "김배우 · 박배우")),
                    operator.put(schedule + "/sessions/" + evening + "/price-adjustment", Map.of("kind", "AMOUNT", "amount", 10_000)),
                    operator.put(schedule + "/sessions/" + evening + "/date-time", sessionBody("2026-03-15", "19:00", "21:00")));

            for (HttpResponse<String> response : resent) {
                assertThat(response.statusCode()).as(response.request().uri().getPath()).isEqualTo(200);
                assertThat(jsonMapper.readTree(response.body())).as(response.request().uri().getPath()).isEqualTo(read);
            }
            assertThat(jsonMapper.readTree(operator.get(schedule).body())).isEqualTo(read);
            assertThat(eventOperations(twelveMonths)).isEqualTo(operationsBefore);
            assertThat(lastModifiedAt(eventId)).isEqualTo(lastModifiedBefore);
            assertThat(sessionNotices.about(eventId)).hasSize(2);
            assertThat(operationsBefore).contains("EDIT_EVENT 출연 변경 · 2026-03-14 19:00 (2단계) " + planner,
                    "EDIT_EVENT 가격 조정 변경 · 2026-03-14 19:00 · +10,000원 (2단계) " + planner,
                    "EDIT_EVENT 회차 일시 변경 · 2026-03-14 19:00 → 2026-03-15 19:00 (2단계) " + planner);
        }
    }

    @Test
    @DisplayName("회차 · 날짜를 지우면 204 — 표에는 지운 시각과 함께 남고 조회에서 빠지며, 재고에 알린다. 지운 시각의 회차는 다시 만들 수 있다")
    void removesSessionsAndDatesKeepingRecords() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay(twelveMonths);
            String schedule = schedulePath(twelveMonths, eventId);
            operator.put(schedule + "/session-mode", Map.of("sessionMode", "TIMED"));
            operator.post(schedule + "/sessions", sessionBody("2026-03-14", "15:00", "17:00"));
            operator.post(schedule + "/sessions", sessionBody("2026-03-14", "19:00", "21:00"));
            JsonNode added = jsonMapper.readTree(operator.post(schedule + "/sessions", sessionBody("2026-03-15", "19:00", "21:00")).body());
            long march15 = added.get("dates").get(1).get("sessions").get(0).get("sessionId").asLong();

            assertThat(operator.delete(schedule + "/sessions/" + march15).statusCode()).isEqualTo(204);
            assertThat(operator.delete(schedule + "/dates/2026-03-14").statusCode()).isEqualTo(204);

            assertThat(jsonMapper.readTree(operator.get(schedule).body()).get("dates")).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM event_session WHERE id = ?", Boolean.class,
                    march15)).isTrue();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_session WHERE event_id = ? AND deleted_at IS NOT NULL",
                    Long.class, eventId)).isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_date WHERE event_id = ? AND deleted_at IS NOT NULL",
                    Long.class, eventId)).isEqualTo(2);
            assertError(operator.delete(schedule + "/sessions/" + march15), 404, "SESSION_NOT_FOUND");
            assertThat(sessionNotices.about(eventId)).filteredOn(SessionRemoved.class::isInstance).hasSize(3)
                    .contains(new SessionRemoved(eventId, march15));

            HttpResponse<String> recreated = operator.post(schedule + "/sessions", sessionBody("2026-03-15", "19:00", "21:00"));

            assertThat(recreated.statusCode()).isEqualTo(201);
            assertThat(jsonMapper.readTree(recreated.body()).get("dates").get(0).get("sessions").get(0).get("sessionId").asLong())
                    .isNotEqualTo(march15);
            assertThat(eventOperations(twelveMonths)).contains("EDIT_EVENT 회차 삭제 · 2026-03-15 19:00 (2단계) " + planner,
                    "EDIT_EVENT 날짜 삭제 · 2026-03-14 (2단계) " + planner);
        }
    }

    @Test
    @DisplayName("회차 방식을 고르기 전 회차는 409, 초가 붙은 시각은 400, 다른 주최자의 운영진은 403, 자기 주최자 경로로 남의 행사에 닿으면 404 — 아무것도 남지 않는다")
    void rejections() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);

            assertError(operator.post(schedulePath(twelveMonths, eventId) + "/sessions", sessionBody("2026-03-14", "19:00", null)),
                    409, "SESSION_MODE_NOT_CHOSEN");
            operator.put(schedulePath(twelveMonths, eventId) + "/session-mode", Map.of("sessionMode", "TIMED"));
            HttpResponse<String> secondsRejected = operator.post(schedulePath(twelveMonths, eventId) + "/sessions",
                    sessionBody("2026-03-14", "19:00:30", null));
            assertError(secondsRejected, 400, "INVALID_INPUT");
            assertThat(jsonMapper.readValue(secondsRejected.body(), ErrorResponse.class).fieldErrors())
                    .extracting(ErrorResponse.FieldError::field).containsExactly("startTime");
            assertError(operator.post(schedulePath(twelveMonths, eventId) + "/dates", Map.of("date", "2026-03-14")),
                    409, "TIMED_DATE_NEEDS_SESSION");
        }
        try (Browser outsider = new Browser()) {
            outsider.logInStaff(yeoreumEmail);

            assertError(outsider.post(schedulePath(twelveMonths, eventId) + "/sessions", sessionBody("2026-03-14", "19:00", null)),
                    403, "NOT_ORGANIZER_STAFF");
            assertError(outsider.post(schedulePath(lateSummer, eventId) + "/sessions", sessionBody("2026-03-14", "19:00", null)),
                    404, "EVENT_NOT_FOUND");
            assertError(outsider.get(schedulePath(lateSummer, eventId)), 404, "EVENT_NOT_FOUND");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_session WHERE event_id = ?", Long.class, eventId)).isZero();
        assertThat(sessionNotices.about(eventId)).isEmpty();
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private static String eventPath(long organizerId, long eventId) {
        return "/api/organizer/organizers/" + organizerId + "/events/" + eventId;
    }

    private static String schedulePath(long organizerId, long eventId) {
        return eventPath(organizerId, eventId) + "/schedule";
    }

    private static Map<String, Object> sessionBody(String date, String startTime, String endTime) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", date);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        return body;
    }

    private static Map<String, Object> allDayBody(String openTime, String closeTime) {
        return Map.of("sessionMode", "ALL_DAY", "defaultOperatingHours", Map.of("openTime", openTime, "closeTime", closeTime));
    }

    /** 극단 열두달의 봄 정기공연 1단계. */
    private static Map<String, Object> springPlayBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "2026 봄 정기공연 〈열두 번째 달〉");
        body.put("type", "PERFORMANCE");
        body.put("categoryName", "연극");
        body.put("introduction", "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.");
        body.put("venueName", "○○아트홀");
        body.put("venueAddress", "서울특별시 종로구 대학로 12");
        return body;
    }

    /** 이 주최자의 행사 조작 기록 — "한 일 요약 운영진 id"를 넣은 차례대로. */
    private List<String> eventOperations(long organizerId) {
        return jdbcTemplate.queryForList("""
                SELECT staff_action || ' ' || summary || ' ' || staff_account_id FROM staff_operation_record
                WHERE organizer_id = ? AND target_kind = 'EVENT' ORDER BY id""", String.class, organizerId);
    }

    private String lastModifiedAt(long eventId) {
        return jdbcTemplate.queryForObject("SELECT last_modified_at::text FROM event WHERE id = ?", String.class, eventId);
    }

    /** 이메일 인증까지 마친 운영진 로그인 한 줄 — 표에 바로 넣는다. */
    private long storeStaff(String nickname, String email) {
        String passwordHash = passwordHashPort.hash(new NewPassword(STAFF_PASSWORD)).value();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', '운영진', ?, ?, lower(?), ?, now() - interval '30 days', now() - interval '30 days')
                RETURNING id""", Long.class, nickname, email, email, passwordHash);
        assertThat(id).isNotNull();
        return id;
    }

    /** 활동 중인 주최자 한 줄 — 신청 · 승인을 거치지 않고 표에 바로 넣는다. */
    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    /** 지금 소속 한 줄. */
    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '30 days')""", organizerId, staffAccountId, role);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** 브라우저 하나 — 서버가 내려 준 쿠키를 기억했다가 다음 요청에 싣는 HTTP 클라이언트. */
    private final class Browser implements AutoCloseable {

        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpResponse<String> post(String path, Object body) throws Exception {
            return send(path, "POST", HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body),
                    StandardCharsets.UTF_8));
        }

        HttpResponse<String> put(String path, Object body) throws Exception {
            return send(path, "PUT", HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body),
                    StandardCharsets.UTF_8));
        }

        HttpResponse<String> delete(String path) throws Exception {
            return send(path, "DELETE", HttpRequest.BodyPublishers.noBody());
        }

        /** 이 세션의 CSRF 토큰을 받아 싣고 보낸다. */
        private HttpResponse<String> send(String path, String method, HttpRequest.BodyPublisher body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .method(method, body)
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** 지금 세션의 CSRF 토큰. 세션이 없으면 서버가 세션을 만들어 쿠키를 내려 준다. */
        String csrfToken() throws Exception {
            HttpResponse<String> response = get(CSRF_TOKEN_PATH);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }

        void logInStaff(String email) throws Exception {
            assertThat(post("/api/auth/staff/login", Map.of("email", email, "password", STAFF_PASSWORD)).statusCode())
                    .isEqualTo(200);
        }

        /** 1단계를 넘겨 봄 정기공연을 만들고 행사 id를 돌려준다. */
        long createSpringPlay(long organizerId) throws Exception {
            HttpResponse<String> created = post("/api/organizer/organizers/" + organizerId + "/events", springPlayBody());
            assertThat(created.statusCode()).isEqualTo(201);
            return jsonMapper.readTree(created.body()).get("eventId").asLong();
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
