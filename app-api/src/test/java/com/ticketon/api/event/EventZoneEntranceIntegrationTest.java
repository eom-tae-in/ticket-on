package com.ticketon.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.event.ZoneAdded;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneCapacityChanged;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneRemoved;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 위저드 3단계의 구역과 6단계의 입구를 저장 · 조회하는 흐름을 끝까지
 * 돌린다(#42 완료 조건). 수량형 "스탠딩A 300" 생성(docs/02 "구역"), 기본가 저장("R석, 5만원" — docs/02 "두 개의 축"), 입구 3개("정문", "2층
 * 전용", "VIP 입구" — docs/02 "입구 (게이트)"). 구역이 생기고 정원이 바뀌고 지워진 것을 재고가 들을 수 있게 애플리케이션 이벤트로 알리는지는 이
 * 테스트의 듣는 쪽({@link ZoneNotices})이 본다(#54). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원, 늦여름의 소유자 최여름. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인과 행사
 * 만들기는 실제 API로 한다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({LoginTestController.class, EventZoneEntranceIntegrationTest.ZoneNotices.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("구역 · 입구 저장과 조회 (진짜 PostgreSQL · 실제 서버)")
class EventZoneEntranceIntegrationTest {

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
    private ZoneNotices zoneNotices;

    @LocalServerPort
    private int port;

    private String junghoonEmail;
    private String plannerEmail;
    private String yeoreumEmail;
    private long planner;
    private long twelveMonths;
    private long lateSummer;

    /** 구역이 생기고 정원이 바뀌고 지워졌다는 알림을 듣는 쪽 — 재고(#54) 자리에서 받은 알림을 차례대로 모은다. */
    @TestComponent
    static class ZoneNotices {

        private final List<Object> received = new CopyOnWriteArrayList<>();

        @EventListener
        void zoneAdded(ZoneAdded notice) {
            received.add(notice);
        }

        @EventListener
        void zoneCapacityChanged(ZoneCapacityChanged notice) {
            received.add(notice);
        }

        @EventListener
        void zoneRemoved(ZoneRemoved notice) {
            received.add(notice);
        }

        /** 그 행사에 대해 받은 알림 — 받은 차례대로. */
        List<Object> about(long eventId) {
            return received.stream().filter(notice -> eventIdOf(notice) == eventId).toList();
        }

        private static long eventIdOf(Object notice) {
            return switch (notice) {
                case ZoneAdded added -> added.eventId();
                case ZoneCapacityChanged changed -> changed.eventId();
                case ZoneRemoved removed -> removed.eventId();
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
    @DisplayName("운영자가 수량형 '스탠딩A 300'(3만원)과 지정형 'R석'(5만원)을 더하고 스탠딩A 정원을 500으로 고치면, 소유자가 다시 열었을 때 그대로이고 진행 단계는 3단계다")
    void savesZonesWithBasePriceAndCapacity() throws Exception {
        long eventId;
        long standingA;
        long rSeat;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);

            HttpResponse<String> standingCreated = operator.post(zonesPath(twelveMonths, eventId),
                    zoneBody("QUANTITY", " 스탠딩A ", "#30a46c", 30_000, 300));
            assertThat(standingCreated.statusCode()).isEqualTo(201);
            JsonNode standing = jsonMapper.readTree(standingCreated.body());
            standingA = standing.get("zoneId").asLong();
            assertThat(standing.get("kind").asString()).isEqualTo("QUANTITY");
            assertThat(standing.get("name").asString()).isEqualTo("스탠딩A");
            assertThat(standing.get("color").asString()).isEqualTo("#30A46C");
            assertThat(standing.get("basePrice").asLong()).isEqualTo(30_000);
            assertThat(standing.get("capacity").asInt()).isEqualTo(300);

            HttpResponse<String> rSeatCreated = operator.post(zonesPath(twelveMonths, eventId),
                    zoneBody("DESIGNATED", "R석", "#E5484D", 50_000, null));
            assertThat(rSeatCreated.statusCode()).isEqualTo(201);
            rSeat = jsonMapper.readTree(rSeatCreated.body()).get("zoneId").asLong();

            HttpResponse<String> enlarged = operator.put(zonesPath(twelveMonths, eventId) + "/" + standingA,
                    changeZoneBody("스탠딩A", "#30A46C", 30_000, 500));
            assertThat(enlarged.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(enlarged.body()).get("capacity").asInt()).isEqualTo(500);
        }
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            HttpResponse<String> listed = owner.get(zonesPath(twelveMonths, eventId));

            assertThat(listed.statusCode()).isEqualTo(200);
            JsonNode zones = jsonMapper.readTree(listed.body()).get("zones");
            assertThat(zones).hasSize(2);
            assertThat(zones.get(0).get("zoneId").asLong()).isEqualTo(standingA);
            assertThat(zones.get(0).get("capacity").asInt()).isEqualTo(500);
            assertThat(zones.get(1).get("zoneId").asLong()).isEqualTo(rSeat);
            assertThat(zones.get(1).get("basePrice").asLong()).isEqualTo(50_000);
            assertThat(zones.get(1).get("capacity").isNull()).isTrue();
            JsonNode wizard = jsonMapper.readTree(owner.get(eventPath(twelveMonths, eventId)).body());
            assertThat(wizard.get("wizardProgress").get("step").asString()).isEqualTo("SEATING");
        }
        assertThat(jdbcTemplate.queryForMap("SELECT kind, name, base_price, capacity, deleted_at FROM event_zone WHERE id = ?",
                standingA)).containsEntry("kind", "QUANTITY").containsEntry("name", "스탠딩A").containsEntry("base_price", 30_000L)
                .containsEntry("capacity", 500).containsEntry("deleted_at", null);
        assertThat(zoneNotices.about(eventId)).containsExactly(
                new ZoneAdded(eventId, standingA, ZoneKind.QUANTITY, ZoneCapacity.of(300)),
                new ZoneAdded(eventId, rSeat, ZoneKind.DESIGNATED, null),
                new ZoneCapacityChanged(eventId, standingA, ZoneCapacity.of(500)));
        assertThat(eventOperations(twelveMonths)).containsExactly("CREATE_EVENT 행사 만들기 " + planner,
                "EDIT_EVENT 구역 추가 · 스탠딩A (3단계) " + planner, "EDIT_EVENT 구역 추가 · R석 (3단계) " + planner,
                "EDIT_EVENT 구역 수정 · 스탠딩A (3단계) " + planner);
    }

    @Test
    @DisplayName("구역을 지우면 204 — 표에는 지운 시각과 함께 남고 목록에서 빠지며, 같은 이름으로 다시 만들 수 있다")
    void removesZoneKeepingRecord() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay(twelveMonths);
            long standingA = jsonMapper.readTree(operator.post(zonesPath(twelveMonths, eventId),
                    zoneBody("QUANTITY", "스탠딩A", "#30A46C", 30_000, 300)).body()).get("zoneId").asLong();

            HttpResponse<String> removed = operator.delete(zonesPath(twelveMonths, eventId) + "/" + standingA);

            assertThat(removed.statusCode()).isEqualTo(204);
            assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM event_zone WHERE id = ?", Boolean.class,
                    standingA)).isTrue();
            assertThat(jsonMapper.readTree(operator.get(zonesPath(twelveMonths, eventId)).body()).get("zones")).isEmpty();
            assertThat(operator.delete(zonesPath(twelveMonths, eventId) + "/" + standingA).statusCode()).isEqualTo(404);

            HttpResponse<String> recreated = operator.post(zonesPath(twelveMonths, eventId),
                    zoneBody("QUANTITY", "스탠딩A", "#30A46C", 30_000, 300));

            assertThat(recreated.statusCode()).isEqualTo(201);
            assertThat(jsonMapper.readTree(recreated.body()).get("zoneId").asLong()).isNotEqualTo(standingA);
            assertThat(zoneNotices.about(eventId)).contains(new ZoneRemoved(eventId, standingA, ZoneKind.QUANTITY));
        }
    }

    @Test
    @DisplayName("입구 3개(정문 · 2층 전용 · VIP 입구)를 더하고 이름을 바꾸고 하나를 지우면 다시 열었을 때 그대로이고, 진행 단계는 6단계다")
    void savesThreeEntrances() throws Exception {
        long eventId;
        long vipGate;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);
            List<Long> entranceIds = new ArrayList<>();
            for (String name : List.of("정문", "2층 전용", "VIP 입구")) {
                HttpResponse<String> created = operator.post(entrancesPath(twelveMonths, eventId), Map.of("name", name));
                assertThat(created.statusCode()).isEqualTo(201);
                entranceIds.add(jsonMapper.readTree(created.body()).get("entranceId").asLong());
            }
            vipGate = entranceIds.get(2);

            HttpResponse<String> renamed = operator.put(entrancesPath(twelveMonths, eventId) + "/" + entranceIds.get(0),
                    Map.of("name", "정문(매표소 쪽)"));
            assertThat(renamed.statusCode()).isEqualTo(200);
            assertThat(operator.delete(entrancesPath(twelveMonths, eventId) + "/" + vipGate).statusCode()).isEqualTo(204);
            assertThat(operator.post(entrancesPath(twelveMonths, eventId), Map.of("name", "2층 전용")).statusCode())
                    .isEqualTo(409);
        }
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            JsonNode entrances = jsonMapper.readTree(owner.get(entrancesPath(twelveMonths, eventId)).body()).get("entrances");

            assertThat(entrances).extracting(entrance -> entrance.get("name").asString())
                    .containsExactly("정문(매표소 쪽)", "2층 전용");
            JsonNode wizard = jsonMapper.readTree(owner.get(eventPath(twelveMonths, eventId)).body());
            assertThat(wizard.get("wizardProgress").get("stepNumber").asInt()).isEqualTo(6);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_entrance WHERE event_id = ?", Long.class, eventId))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM event_entrance WHERE id = ?", Boolean.class,
                vipGate)).isTrue();
        assertThat(eventOperations(twelveMonths)).contains("EDIT_EVENT 입구 이름 변경 · 정문 → 정문(매표소 쪽) (6단계) " + planner,
                "EDIT_EVENT 입구 삭제 · VIP 입구 (6단계) " + planner);
    }

    @Test
    @DisplayName("수량형에 정원이 없거나 같은 이름이면 409, 빈 이름은 400, 다른 주최자의 운영진은 403, 자기 주최자 경로로 남의 행사에 닿으면 404 — 아무것도 남지 않는다")
    void rejections() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);
            operator.post(zonesPath(twelveMonths, eventId), zoneBody("QUANTITY", "스탠딩A", "#30A46C", 30_000, 300));

            assertError(operator.post(zonesPath(twelveMonths, eventId), zoneBody("QUANTITY", "스탠딩B", "#30A46C", 25_000, null)),
                    409, "ZONE_CAPACITY_REQUIRED");
            assertError(operator.post(zonesPath(twelveMonths, eventId), zoneBody("DESIGNATED", "스탠딩A", "#E5484D", 50_000, null)),
                    409, "ZONE_NAME_ALREADY_TAKEN");
            assertError(operator.post(zonesPath(twelveMonths, eventId), zoneBody("DESIGNATED", " ", "#E5484D", 50_000, null)),
                    400, "INVALID_INPUT");
        }
        try (Browser outsider = new Browser()) {
            outsider.logInStaff(yeoreumEmail);

            assertError(outsider.post(zonesPath(twelveMonths, eventId), zoneBody("QUANTITY", "스탠딩C", "#30A46C", 30_000, 100)),
                    403, "NOT_ORGANIZER_STAFF");
            assertError(outsider.post(zonesPath(lateSummer, eventId), zoneBody("QUANTITY", "스탠딩C", "#30A46C", 30_000, 100)),
                    404, "EVENT_NOT_FOUND");
            assertError(outsider.get(entrancesPath(lateSummer, eventId)), 404, "EVENT_NOT_FOUND");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_zone WHERE event_id = ?", Long.class, eventId))
                .isEqualTo(1);
        assertThat(zoneNotices.about(eventId)).hasSize(1);
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private static String eventPath(long organizerId, long eventId) {
        return "/api/organizer/organizers/" + organizerId + "/events/" + eventId;
    }

    private static String zonesPath(long organizerId, long eventId) {
        return eventPath(organizerId, eventId) + "/zones";
    }

    private static String entrancesPath(long organizerId, long eventId) {
        return eventPath(organizerId, eventId) + "/entrances";
    }

    private static Map<String, Object> zoneBody(String kind, String name, String color, long basePrice, Integer capacity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.putAll(changeZoneBody(name, color, basePrice, capacity));
        return body;
    }

    private static Map<String, Object> changeZoneBody(String name, String color, long basePrice, Integer capacity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("color", color);
        body.put("basePrice", basePrice);
        body.put("capacity", capacity);
        return body;
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
