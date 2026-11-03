package com.ticketon.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.NewPassword;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 끝난 행사를 복제하는 흐름을 끝까지 돌린다(#47 완료 조건, docs/04
 * "행사 복제"). 작년 봄 정기공연은 도면(구역 R석 · 스탠딩A, 블록 1층 12×10 · 2층 5×10, 휠체어석 · 꺼 둔 기둥 자리, 무대) · 입구 셋 · 회차 하나를
 * 갖추고, 링크 전용 · 예매 설정 · "3일 전까지 전액" 취소 규정으로 판매를 마쳤다. 복제본에서 "1층"을 "지하 1층"으로 바꾸고 12열을 없애도 작년
 * 도면에는 여전히 "1층 12열 7번"이 있어야 한다(docs/02 "도면은 행사마다 따로 갖습니다"). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원, 늦여름의 소유자 최여름. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인과 행사
 * 만들기 · 도면 · 입구 · 회차 · 예매 설정 · 복제는 실제 API로 한다. 원본을 끝난 행사로 두는 일만 표를 바로 고친다. 테스트끼리 같은 DB를 쓰므로
 * 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 복제 (진짜 PostgreSQL · 실제 서버)")
class EventDuplicationIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final String SPRING_PLAY_TITLE = "2026 봄 정기공연 〈열두 번째 달〉";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashPort passwordHashPort;

    @LocalServerPort
    private int port;

    private String plannerEmail;
    private String yeoreumEmail;
    private long planner;
    private long twelveMonths;
    private long lateSummer;

    @BeforeEach
    void storeOrganizers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        plannerEmail = "planner-" + suffix + "@example.com";
        yeoreumEmail = "yeoreum-" + suffix + "@example.com";
        long junghoon = storeStaff("고래" + suffix, "junghoon-" + suffix + "@example.com");
        planner = storeStaff("바람" + suffix, plannerEmail);
        long yeoreum = storeStaff("여름" + suffix, yeoreumEmail);
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
    }

    @Test
    @DisplayName("운영자가 끝난 봄 정기공연을 복제하면 작성 중인 '(복사본)'이 공개로 생기고, 예매 설정은 가져오되 오픈 시각 · 회차는 없다")
    void duplicatesEndedEventAsPublicDraftWithoutSessions() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long original = operator.lastSpringPlay();

            HttpResponse<String> duplicated = operator.post(eventPath(twelveMonths, original) + "/duplication", Map.of());

            assertThat(duplicated.statusCode()).isEqualTo(201);
            JsonNode copy = jsonMapper.readTree(duplicated.body());
            long copyId = copy.get("eventId").asLong();
            assertThat(copyId).isNotEqualTo(original);
            assertThat(copy.get("status").asString()).isEqualTo("DRAFT");
            assertThat(copy.get("wizardProgress").get("step").asString()).isEqualTo("BASIC_INFORMATION");
            assertThat(copy.get("posterUrl").isNull()).isTrue();
            assertThat(copy.get("publicAddress").asString()).isNotEqualTo(publicAddressOf(original));
            JsonNode basicInformation = copy.get("basicInformation");
            assertThat(basicInformation.get("title").asString()).isEqualTo(SPRING_PLAY_TITLE + " (복사본)");
            assertThat(basicInformation.get("categoryName").asString()).isEqualTo("연극");
            assertThat(basicInformation.get("venueDetails").asString()).isEqualTo("지하 1층, 주차 불가");
            JsonNode bookingSettings = copy.get("bookingSettings");
            assertThat(bookingSettings.get("visibility").asString()).isEqualTo("PUBLIC");
            assertThat(bookingSettings.get("passwordSet").asBoolean()).isFalse();
            assertThat(bookingSettings.get("bookingOpensAt").isNull()).isTrue();
            assertThat(bookingSettings.get("bookingCloseLeadMinutes").asInt()).isEqualTo(120);
            assertThat(bookingSettings.get("checkinOpenLeadMinutes").asInt()).isEqualTo(30);
            assertThat(bookingSettings.get("maxSeatsPerSelection").asInt()).isEqualTo(2);
            assertThat(bookingSettings.get("ticketsPerPersonLimit").get("maxTickets").asInt()).isEqualTo(6);
            assertThat(bookingSettings.get("sessionChoice").asString()).isEqualTo("ONE_SESSION_PER_DATE");
            assertThat(bookingSettings.get("reentryAllowed").asBoolean()).isTrue();
            assertThat(bookingSettings.get("minimumAge").asInt()).isEqualTo(19);
            assertThat(bookingSettings.get("cancellationFeeTiers")).isEqualTo(jsonMapper.readTree("""
                    [{"fromDaysBefore": 3, "deductionRatePercent": 0}, {"fromDaysBefore": 1, "deductionRatePercent": 30},
                     {"fromDaysBefore": 0, "deductionRatePercent": 90}]"""));

            JsonNode schedule = operator.getJson(eventPath(twelveMonths, copyId) + "/schedule");
            assertThat(schedule.get("sessionMode").isNull()).isTrue();
            assertThat(schedule.get("dates")).isEmpty();
            assertThat(operator.getJson(eventPath(twelveMonths, original) + "/schedule").get("dates")).hasSize(1);
            assertThat(jdbcTemplate.queryForObject("SELECT visibility FROM event WHERE id = ?", String.class, original))
                    .isEqualTo("LINK_ONLY");
            assertThat(eventOperations(copyId)).containsExactly("CREATE_EVENT 행사 복제 · " + SPRING_PLAY_TITLE + " " + planner);
        }
    }

    @Test
    @DisplayName("도면 · 입구는 새 id로 통째로 복사되고, 복제본에서 '1층'을 '지하 1층'으로 바꾸고 12열을 없애고 가격 · 입구를 고쳐도 원본은 그대로다")
    void copiesFloorPlanWithNewIdsAndEditingCopyLeavesOriginal() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long original = operator.lastSpringPlay();
            JsonNode originalFloorPlan = operator.getJson(eventPath(twelveMonths, original) + "/floor-plan");
            JsonNode originalEntrances = operator.getJson(eventPath(twelveMonths, original) + "/entrances");

            long copyId = jsonMapper.readTree(operator.post(eventPath(twelveMonths, original) + "/duplication", Map.of()).body())
                    .get("eventId").asLong();

            JsonNode copiedFloorPlan = operator.getJson(eventPath(twelveMonths, copyId) + "/floor-plan");
            assertThat(withoutIds(copiedFloorPlan)).isEqualTo(withoutIds(originalFloorPlan));
            assertThat(idsOf(copiedFloorPlan, "zones", "zoneId"))
                    .doesNotContainAnyElementsOf(idsOf(originalFloorPlan, "zones", "zoneId"));
            assertThat(idsOf(copiedFloorPlan, "blocks", "blockId"))
                    .doesNotContainAnyElementsOf(idsOf(originalFloorPlan, "blocks", "blockId"));
            assertThat(seatIdsOf(copiedFloorPlan)).hasSize(170).doesNotContainAnyElementsOf(seatIdsOf(originalFloorPlan));
            long copiedRSeat = copiedFloorPlan.get("zones").get(0).get("zoneId").asLong();
            assertThat(copiedFloorPlan.get("blocks").get(0).get("seats").get(0).get("zoneId").asLong()).isEqualTo(copiedRSeat);
            JsonNode copiedEntrances = operator.getJson(eventPath(twelveMonths, copyId) + "/entrances");
            assertThat(namesOf(copiedEntrances.get("entrances"))).containsExactly("정문", "2층 전용", "VIP 입구");
            assertThat(idsOf(copiedEntrances, "entrances", "entranceId"))
                    .doesNotContainAnyElementsOf(idsOf(originalEntrances, "entrances", "entranceId"));

            JsonNode copiedFirstFloor = copiedFloorPlan.get("blocks").get(0);
            assertThat(operator.put(eventPath(twelveMonths, copyId) + "/blocks/" + copiedFirstFloor.get("blockId").asLong() + "/name",
                    Map.of("name", "지하 1층")).statusCode()).isEqualTo(200);
            assertThat(operator.put(eventPath(twelveMonths, copyId) + "/seats/activation",
                    Map.of("seatIds", seatIdsInRow(copiedFirstFloor, 12), "active", false)).statusCode()).isEqualTo(200);
            assertThat(operator.put(eventPath(twelveMonths, copyId) + "/zones/" + copiedRSeat,
                    zoneBody("R석", "#E5484D", 60_000, null)).statusCode()).isEqualTo(200);
            assertThat(operator.delete(eventPath(twelveMonths, copyId) + "/blocks/"
                    + copiedFloorPlan.get("blocks").get(1).get("blockId").asLong()).statusCode()).isEqualTo(204);
            assertThat(operator.delete(eventPath(twelveMonths, copyId) + "/entrances/"
                    + copiedEntrances.get("entrances").get(2).get("entranceId").asLong()).statusCode()).isEqualTo(204);

            assertThat(operator.getJson(eventPath(twelveMonths, original) + "/floor-plan")).isEqualTo(originalFloorPlan);
            assertThat(operator.getJson(eventPath(twelveMonths, original) + "/entrances")).isEqualTo(originalEntrances);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT b.name || ' ' || s.seat_row || '열 ' || s.seat_number || '번 ' || s.active
                    FROM event_seat s JOIN event_block b ON b.id = s.block_id
                    WHERE s.event_id = ? AND b.name = '1층' AND s.seat_row = 12 AND s.seat_number = 7 AND s.deleted_at IS NULL""",
                    String.class, original)).isEqualTo("1층 12열 7번 true");
            assertThat(operator.getJson(eventPath(twelveMonths, copyId) + "/floor-plan").get("blocks").get(0).get("name").asString())
                    .isEqualTo("지하 1층");
        }
    }

    @Test
    @DisplayName("다른 주최자의 행사를 자기 주최자 경로로 복제하면 404 EVENT_NOT_FOUND이고 복제본이 생기지 않는다")
    void otherOrganizersEventIsNotFound() throws Exception {
        long original;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            original = operator.lastSpringPlay();
        }
        try (Browser outsider = new Browser()) {
            outsider.logInStaff(yeoreumEmail);

            assertError(outsider.post(eventPath(lateSummer, original) + "/duplication", Map.of()), 404, "EVENT_NOT_FOUND");
        }
        assertThat(eventCountOf(lateSummer)).isZero();
        assertThat(eventCountOf(twelveMonths)).isEqualTo(1);
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 403 NOT_ORGANIZER_STAFF이고 복제본이 생기지 않는다")
    void outsiderIsForbidden() throws Exception {
        long original;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            original = operator.lastSpringPlay();
        }
        try (Browser outsider = new Browser()) {
            outsider.logInStaff(yeoreumEmail);

            assertError(outsider.post(eventPath(twelveMonths, original) + "/duplication", Map.of()), 403, "NOT_ORGANIZER_STAFF");
        }
        assertThat(eventCountOf(twelveMonths)).isEqualTo(1);
        assertThat(eventCountOf(lateSummer)).isZero();
    }

    /** 도면에서 id(행사 · 구역 · 블록 · 좌석)를 걷어 내고 좌석이 가리키는 구역은 구역 이름으로 바꾼 모양 — 원본과 복제본을 견준다. */
    private JsonNode withoutIds(JsonNode floorPlan) throws Exception {
        Map<Long, String> zoneNames = new LinkedHashMap<>();
        floorPlan.get("zones").forEach(zone -> zoneNames.put(zone.get("zoneId").asLong(), zone.get("name").asString()));
        ObjectNode shape = (ObjectNode) jsonMapper.readTree(floorPlan.toString());
        shape.remove("eventId");
        shape.get("zones").forEach(zone -> ((ObjectNode) zone).remove("zoneId"));
        shape.get("blocks").forEach(block -> {
            ((ObjectNode) block).remove("blockId");
            block.get("seats").forEach(seat -> {
                ObjectNode seatShape = (ObjectNode) seat;
                seatShape.remove("seatId");
                if (!seat.get("zoneId").isNull()) {
                    seatShape.put("zoneId", zoneNames.get(seat.get("zoneId").asLong()));
                }
            });
        });
        return shape;
    }

    private static List<Long> idsOf(JsonNode response, String listName, String idName) {
        List<Long> ids = new ArrayList<>();
        response.get(listName).forEach(item -> ids.add(item.get(idName).asLong()));
        return ids;
    }

    private static List<Long> seatIdsOf(JsonNode floorPlan) {
        List<Long> ids = new ArrayList<>();
        floorPlan.get("blocks").forEach(block -> block.get("seats").forEach(seat -> ids.add(seat.get("seatId").asLong())));
        return ids;
    }

    private static List<Long> seatIdsInRow(JsonNode block, int row) {
        List<Long> ids = new ArrayList<>();
        block.get("seats").forEach(seat -> {
            if (seat.get("row").asInt() == row) {
                ids.add(seat.get("seatId").asLong());
            }
        });
        return ids;
    }

    private static List<String> namesOf(JsonNode items) {
        List<String> names = new ArrayList<>();
        items.forEach(item -> names.add(item.get("name").asString()));
        return names;
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private static String eventPath(long organizerId, long eventId) {
        return "/api/organizer/organizers/" + organizerId + "/events/" + eventId;
    }

    private static Map<String, Object> zoneBody(String name, String color, long basePrice, Integer capacity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("color", color);
        body.put("basePrice", basePrice);
        body.put("capacity", capacity);
        return body;
    }

    private static Map<String, Object> blockBody(String name, int rows, int columns, int x, int y, int angleDegrees) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("rows", rows);
        body.put("columns", columns);
        body.put("position", Map.of("x", x, "y", y));
        body.put("angleDegrees", angleDegrees);
        return body;
    }

    /** 극단 열두달의 봄 정기공연 1단계 — 선택 항목 가운데 상세 안내만 적었다. */
    private static Map<String, Object> springPlayBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", SPRING_PLAY_TITLE);
        body.put("type", "PERFORMANCE");
        body.put("categoryName", "연극");
        body.put("introduction", "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.");
        body.put("venueName", "○○아트홀");
        body.put("venueAddress", "서울특별시 종로구 대학로 12");
        body.put("venueDetails", "지하 1층, 주차 불가");
        return body;
    }

    /** 링크 전용, 오픈 2026-02-01 10:00(한국 시각), 예매 종료 2시간 전, 검표 30분 전, 1회 2석, 1인 6매(행사 전체), 하루 한 회차, 재입장 켬, 만 19세 이상, "3일 전까지 전액". */
    private static Map<String, Object> adjustedBookingBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("visibility", "LINK_ONLY");
        body.put("newPassword", null);
        body.put("bookingOpensAt", "2026-02-01T10:00:00+09:00");
        body.put("bookingCloseLeadMinutes", 120);
        body.put("checkinOpenLeadMinutes", 30);
        body.put("maxSeatsPerSelection", 2);
        body.put("ticketsPerPersonLimit", Map.of("maxTickets", 6, "basis", "EVENT_TOTAL"));
        body.put("sessionChoice", "ONE_SESSION_PER_DATE");
        body.put("reentryAllowed", true);
        body.put("minimumAge", 19);
        body.put("cancellationFeeTiers", List.of(Map.of("fromDaysBefore", 3, "deductionRatePercent", 0),
                Map.of("fromDaysBefore", 1, "deductionRatePercent", 30), Map.of("fromDaysBefore", 0, "deductionRatePercent", 90)));
        return body;
    }

    private String publicAddressOf(long eventId) {
        return jdbcTemplate.queryForObject("SELECT public_address FROM event WHERE id = ?", String.class, eventId);
    }

    private long eventCountOf(long organizerId) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM event WHERE organizer_id = ?", Long.class, organizerId);
        assertThat(count).isNotNull();
        return count;
    }

    /** 그 행사를 대상으로 남은 조작 기록 — "한 일 요약 운영진 id"를 넣은 차례대로. */
    private List<String> eventOperations(long eventId) {
        return jdbcTemplate.queryForList("""
                SELECT staff_action || ' ' || summary || ' ' || staff_account_id FROM staff_operation_record
                WHERE target_kind = 'EVENT' AND target_id = ? ORDER BY id""", String.class, eventId);
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

        /** 200으로 받은 응답 본문. */
        JsonNode getJson(String path) throws Exception {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).isEqualTo(200);
            return jsonMapper.readTree(response.body());
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

        /** 보낸 요청이 그 상태 코드로 끝났는지 보고 응답 본문을 돌려준다. */
        private JsonNode expect(HttpResponse<String> response, int status) throws Exception {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
            return jsonMapper.readTree(response.body());
        }

        /**
         * 극단 열두달의 작년 봄 정기공연을 API로 갖추고 끝난 행사로 둔 뒤 행사 id를 돌려준다 — 구역 R석(5만원) · 스탠딩A(3만원 300석), 블록
         * 1층(12×10, 모두 R석) · 2층(5×10, 15도), 1층 1열 1번 휠체어석, 2층 5열 10번은 꺼 둔 기둥 자리, 무대, 입구 셋, 회차 3/14 19:00~21:00,
         * 링크 전용과 조정한 예매 설정.
         */
        long lastSpringPlay() throws Exception {
            long eventId = expect(post("/api/organizer/organizers/" + twelveMonths + "/events", springPlayBody()), 201)
                    .get("eventId").asLong();
            String path = eventPath(twelveMonths, eventId);
            long rSeat = expect(post(path + "/zones", withKind("DESIGNATED", zoneBody("R석", "#E5484D", 50_000, null))), 201)
                    .get("zoneId").asLong();
            expect(post(path + "/zones", withKind("QUANTITY", zoneBody("스탠딩A", "#30A46C", 30_000, 300))), 201);
            JsonNode firstFloor = expect(post(path + "/blocks", blockBody("1층", 12, 10, 120, 200, 0)), 201);
            JsonNode secondFloor = expect(post(path + "/blocks", blockBody("2층", 5, 10, 120, 600, 15)), 201);
            List<Long> firstFloorSeats = new ArrayList<>();
            firstFloor.get("seats").forEach(seat -> firstFloorSeats.add(seat.get("seatId").asLong()));
            expect(put(path + "/seats/zone", Map.of("seatIds", firstFloorSeats, "zoneId", rSeat)), 200);
            expect(put(path + "/seats/attribute", Map.of("seatIds", List.of(firstFloorSeats.getFirst()), "attribute", "WHEELCHAIR")),
                    200);
            JsonNode pillar = secondFloor.get("seats").get(secondFloor.get("seats").size() - 1);
            expect(put(path + "/seats/activation", Map.of("seatIds", List.of(pillar.get("seatId").asLong()), "active", false)), 200);
            expect(put(path + "/stage", Map.of("x", 400, "y", 0)), 200);
            for (String entrance : List.of("정문", "2층 전용", "VIP 입구")) {
                expect(post(path + "/entrances", Map.of("name", entrance)), 201);
            }
            Map<String, Object> timed = new LinkedHashMap<>();
            timed.put("sessionMode", "TIMED");
            timed.put("defaultOperatingHours", null);
            expect(put(path + "/schedule/session-mode", timed), 200);
            expect(post(path + "/schedule/sessions", Map.of("date", "2026-03-14", "startTime", "19:00", "endTime", "21:00")), 201);
            expect(put(path + "/booking-settings", adjustedBookingBody()), 200);
            jdbcTemplate.update("UPDATE event SET status = 'ENDED', booking_opened_at = '2026-02-01T01:00:00Z' WHERE id = ?", eventId);
            return eventId;
        }

        private static Map<String, Object> withKind(String kind, Map<String, Object> zoneBody) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("kind", kind);
            body.putAll(zoneBody);
            return body;
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
