package com.ticketon.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.event.SeatBlockAdded;
import com.ticketon.domain.event.SeatBlockRemoved;
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
import java.util.stream.StreamSupport;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 위저드 3단계 배치도 에디터의 블록 · 좌석 · 무대를 저장 · 조회하는
 * 흐름을 끝까지 돌린다(#43 완료 조건) — 10행 × 20열 블록 → 좌석 200개, 맨 앞줄 일괄 구역 배정, 좌석 비활성화, 블록 이름 중복 거부, 블록 편집에도
 * 좌석 id 그대로, 블록 삭제 기록, 구역 삭제 시 좌석 배정 풀림. 블록이 얹히고 지워진 것을 재고가 들을 수 있게 애플리케이션 이벤트로 알리는지는 이
 * 테스트의 듣는 쪽({@link BlockNotices})이 본다(#54). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원, 늦여름의 소유자 최여름. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인 · 행사
 * 만들기 · 구역 추가는 실제 API로 한다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({LoginTestController.class, FloorPlanEditorIntegrationTest.BlockNotices.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("배치도 편집 — 블록 · 좌석 · 무대 저장과 조회 (진짜 PostgreSQL · 실제 서버)")
class FloorPlanEditorIntegrationTest {

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
    private BlockNotices blockNotices;

    @LocalServerPort
    private int port;

    private String junghoonEmail;
    private String plannerEmail;
    private String yeoreumEmail;
    private long planner;
    private long twelveMonths;
    private long lateSummer;

    /** 블록이 얹히고 지워졌다는 알림을 듣는 쪽 — 재고(#54) 자리에서 받은 알림을 차례대로 모은다. */
    @TestComponent
    static class BlockNotices {

        private final List<Object> received = new CopyOnWriteArrayList<>();

        @EventListener
        void blockAdded(SeatBlockAdded notice) {
            received.add(notice);
        }

        @EventListener
        void blockRemoved(SeatBlockRemoved notice) {
            received.add(notice);
        }

        /** 그 행사에 대해 받은 알림 — 받은 차례대로. */
        List<Object> about(long eventId) {
            return received.stream().filter(notice -> eventIdOf(notice) == eventId).toList();
        }

        private static long eventIdOf(Object notice) {
            return switch (notice) {
                case SeatBlockAdded added -> added.eventId();
                case SeatBlockRemoved removed -> removed.eventId();
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
    @DisplayName("운영자가 10행 × 20열 '1층'을 얹어 좌석 200개를 만들고, 맨 앞줄 20석을 R석에 칠하고, 통로 3석을 끄고, 휠체어 2석을 정하면 소유자가 다시 열었을 때 그대로다")
    void buildsTwoHundredSeatTheater() throws Exception {
        long eventId;
        long rSeat;
        JsonNode firstFloor;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);
            rSeat = operator.addRSeat(twelveMonths, eventId);

            HttpResponse<String> added = operator.post(blocksPath(twelveMonths, eventId),
                    blockBody(" 1층 ", 10, 20, Map.of("x", 120, "y", 80), -15));

            assertThat(added.statusCode()).isEqualTo(201);
            firstFloor = jsonMapper.readTree(added.body());
            assertThat(firstFloor.get("name").asString()).isEqualTo("1층");
            assertThat(firstFloor.get("angleDegrees").asInt()).isEqualTo(345);
            assertThat(firstFloor.get("seats")).hasSize(200);
            List<Long> frontRow = seatIds(firstFloor, 1, 20);

            HttpResponse<String> painted = operator.put(seatsPath(twelveMonths, eventId) + "/zone",
                    Map.of("seatIds", frontRow, "zoneId", rSeat));
            assertThat(painted.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(painted.body()).get("seats")).hasSize(20)
                    .allSatisfy(seat -> assertThat(seat.get("zoneId").asLong()).isEqualTo(rSeat));
            assertThat(operator.put(seatsPath(twelveMonths, eventId) + "/activation",
                    Map.of("seatIds", seatIds(firstFloor, 5, 3), "active", false)).statusCode()).isEqualTo(200);
            assertThat(operator.put(seatsPath(twelveMonths, eventId) + "/attribute",
                    Map.of("seatIds", seatIds(firstFloor, 10, 2), "attribute", "WHEELCHAIR")).statusCode()).isEqualTo(200);
        }
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            HttpResponse<String> opened = owner.get(eventPath(twelveMonths, eventId) + "/floor-plan");

            assertThat(opened.statusCode()).isEqualTo(200);
            JsonNode floorPlan = jsonMapper.readTree(opened.body());
            assertThat(floorPlan.get("zones")).extracting(zone -> zone.get("name").asString()).containsExactly("R석");
            assertThat(floorPlan.get("stagePosition").isNull()).isTrue();
            JsonNode block = floorPlan.get("blocks").get(0);
            assertThat(block.get("blockId").asLong()).isEqualTo(firstFloor.get("blockId").asLong());
            assertThat(block.get("rows").asInt()).isEqualTo(10);
            assertThat(block.get("columns").asInt()).isEqualTo(20);
            assertThat(block.get("position").get("x").asInt()).isEqualTo(120);
            List<JsonNode> seats = list(block.get("seats"));
            assertThat(seats).hasSize(200);
            assertThat(seats.stream().filter(seat -> !seat.get("zoneId").isNull())).hasSize(20);
            assertThat(seats.stream().filter(seat -> !seat.get("active").asBoolean())).hasSize(3);
            assertThat(seats.stream().filter(seat -> seat.get("attribute").asString().equals("WHEELCHAIR"))).hasSize(2);
            JsonNode wizard = jsonMapper.readTree(owner.get(eventPath(twelveMonths, eventId)).body());
            assertThat(wizard.get("wizardProgress").get("step").asString()).isEqualTo("SEATING");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_seat WHERE event_id = ? AND deleted_at IS NULL", Long.class,
                eventId)).isEqualTo(200);
        assertThat(blockNotices.about(eventId)).containsExactly(new SeatBlockAdded(eventId, firstFloor.get("blockId").asLong(),
                list(firstFloor.get("seats")).stream().map(seat -> seat.get("seatId").asLong()).toList()));
        assertThat(eventOperations(twelveMonths)).containsExactly("CREATE_EVENT 행사 만들기 " + planner,
                "EDIT_EVENT 구역 추가 · R석 (3단계) " + planner, "EDIT_EVENT 블록 추가 · 1층 (3단계) " + planner,
                "EDIT_EVENT 좌석 구역 배정 · R석 20석 (3단계) " + planner, "EDIT_EVENT 좌석 비활성화 · 3석 (3단계) " + planner,
                "EDIT_EVENT 좌석 속성 지정 · 휠체어 2석 (3단계) " + planner);
    }

    @Test
    @DisplayName("같은 이름의 블록, 구역 이름과 겹치는 블록, 블록 이름과 겹치는 구역은 409 — 아무것도 남지 않는다")
    void rejectsOverlappingNames() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay(twelveMonths);
            operator.addRSeat(twelveMonths, eventId);
            assertThat(operator.post(blocksPath(twelveMonths, eventId), blockBody("1층", 10, 20, null, null)).statusCode())
                    .isEqualTo(201);

            assertError(operator.post(blocksPath(twelveMonths, eventId), blockBody("1층", 5, 10, null, null)), 409,
                    "BLOCK_NAME_ALREADY_TAKEN");
            assertError(operator.post(blocksPath(twelveMonths, eventId), blockBody("R석", 5, 10, null, null)), 409,
                    "BLOCK_NAME_TAKEN_BY_ZONE");
            assertError(operator.post(zonesPath(twelveMonths, eventId), zoneBody("1층")), 409, "ZONE_NAME_TAKEN_BY_BLOCK");

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_block WHERE event_id = ?", Long.class, eventId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_seat WHERE event_id = ?", Long.class, eventId))
                    .isEqualTo(200);
            assertThat(blockNotices.about(eventId)).hasSize(1);
        }
    }

    @Test
    @DisplayName("'1증'을 '1층'으로 고치고 블록을 옮기고 돌려도 좌석 id는 그대로다 — 무대를 놓으면 도면에 실린다")
    void blockEditsKeepSeatIds() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay(twelveMonths);
            JsonNode typo = jsonMapper.readTree(operator.post(blocksPath(twelveMonths, eventId),
                    blockBody("1증", 10, 20, null, null)).body());
            String blockPath = blocksPath(twelveMonths, eventId) + "/" + typo.get("blockId").asLong();

            HttpResponse<String> renamed = operator.put(blockPath + "/name", Map.of("name", "1층"));
            HttpResponse<String> moved = operator.put(blockPath + "/position", Map.of("x", -40, "y", 260));
            HttpResponse<String> rotated = operator.put(blockPath + "/angle", Map.of("degrees", 15));
            HttpResponse<String> stage = operator.put(eventPath(twelveMonths, eventId) + "/stage", Map.of("x", 400, "y", -30));

            assertThat(List.of(renamed, moved, rotated, stage)).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
            JsonNode floorPlan = jsonMapper.readTree(operator.get(eventPath(twelveMonths, eventId) + "/floor-plan").body());
            JsonNode block = floorPlan.get("blocks").get(0);
            assertThat(block.get("name").asString()).isEqualTo("1층");
            assertThat(block.get("position").get("y").asInt()).isEqualTo(260);
            assertThat(block.get("angleDegrees").asInt()).isEqualTo(15);
            assertThat(list(block.get("seats"))).extracting(seat -> seat.get("seatId").asLong())
                    .containsExactlyElementsOf(list(typo.get("seats")).stream().map(seat -> seat.get("seatId").asLong()).toList());
            assertThat(floorPlan.get("stagePosition").get("x").asInt()).isEqualTo(400);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_seat WHERE event_id = ?", Long.class, eventId))
                    .isEqualTo(200);
            assertThat(eventOperations(twelveMonths)).contains("EDIT_EVENT 블록 이름 변경 · 1증 → 1층 (3단계) " + planner,
                    "EDIT_EVENT 블록 이동 · 1층 (3단계) " + planner, "EDIT_EVENT 블록 회전 · 1층 (3단계) " + planner,
                    "EDIT_EVENT 무대 위치 지정 (3단계) " + planner);
        }
    }

    @Test
    @DisplayName("블록을 지우면 204 — 블록과 좌석 200줄은 지운 시각과 함께 남고 도면에서 빠지며, 재고에 알리고, 같은 이름으로 다시 얹는다")
    void removesBlockKeepingRecord() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay(twelveMonths);
            JsonNode block = jsonMapper.readTree(operator.post(blocksPath(twelveMonths, eventId),
                    blockBody("1층", 10, 20, null, null)).body());
            long blockId = block.get("blockId").asLong();

            HttpResponse<String> removed = operator.delete(blocksPath(twelveMonths, eventId) + "/" + blockId);

            assertThat(removed.statusCode()).isEqualTo(204);
            assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM event_block WHERE id = ?", Boolean.class, blockId))
                    .isTrue();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_seat WHERE block_id = ? AND deleted_at IS NOT NULL",
                    Long.class, blockId)).isEqualTo(200);
            assertThat(jsonMapper.readTree(operator.get(eventPath(twelveMonths, eventId) + "/floor-plan").body()).get("blocks")).isEmpty();
            assertThat(blockNotices.about(eventId)).last().isEqualTo(new SeatBlockRemoved(eventId, blockId,
                    list(block.get("seats")).stream().map(seat -> seat.get("seatId").asLong()).toList()));
            assertError(operator.delete(blocksPath(twelveMonths, eventId) + "/" + blockId), 404, "BLOCK_NOT_FOUND");
            assertThat(operator.post(blocksPath(twelveMonths, eventId), blockBody("1층", 5, 10, null, null)).statusCode())
                    .isEqualTo(201);
        }
    }

    @Test
    @DisplayName("좌석을 칠한 R석을 지우면 표에서 그 좌석들의 구역이 같은 요청에서 비워진다 (#42)")
    void removingZoneUnassignsSeats() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay(twelveMonths);
            long rSeat = operator.addRSeat(twelveMonths, eventId);
            JsonNode block = jsonMapper.readTree(operator.post(blocksPath(twelveMonths, eventId),
                    blockBody("1층", 10, 20, null, null)).body());
            operator.put(seatsPath(twelveMonths, eventId) + "/zone", Map.of("seatIds", seatIds(block, 1, 20), "zoneId", rSeat));

            assertThat(operator.delete(zonesPath(twelveMonths, eventId) + "/" + rSeat).statusCode()).isEqualTo(204);

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_seat WHERE zone_id = ?", Long.class, rSeat)).isZero();
            assertThat(list(jsonMapper.readTree(operator.get(eventPath(twelveMonths, eventId) + "/floor-plan").body())
                    .get("blocks").get(0).get("seats"))).allSatisfy(seat -> assertThat(seat.get("zoneId").isNull()).isTrue());
        }
    }

    @Test
    @DisplayName("없는 좌석이 섞이면 404이고 아무것도 바뀌지 않는다. 51열은 400, 다른 주최자의 운영진은 403, 자기 주최자 경로로 남의 행사에 닿으면 404")
    void rejections() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay(twelveMonths);
            JsonNode block = jsonMapper.readTree(operator.post(blocksPath(twelveMonths, eventId),
                    blockBody("1층", 10, 20, null, null)).body());
            List<Long> withUnknown = new ArrayList<>(seatIds(block, 1, 2));
            withUnknown.add(999_999_999L);

            assertError(operator.put(seatsPath(twelveMonths, eventId) + "/activation", Map.of("seatIds", withUnknown, "active", false)),
                    404, "SEAT_NOT_FOUND");
            assertError(operator.post(blocksPath(twelveMonths, eventId), blockBody("2층", 10, 51, null, null)), 400, "INVALID_INPUT");
        }
        try (Browser outsider = new Browser()) {
            outsider.logInStaff(yeoreumEmail);

            assertError(outsider.post(blocksPath(twelveMonths, eventId), blockBody("2층", 5, 10, null, null)), 403,
                    "NOT_ORGANIZER_STAFF");
            assertError(outsider.get(eventPath(lateSummer, eventId) + "/floor-plan"), 404, "EVENT_NOT_FOUND");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_seat WHERE event_id = ? AND active = FALSE", Long.class,
                eventId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event_block WHERE event_id = ?", Long.class, eventId)).isEqualTo(1);
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    /** 그 블록 응답에서 그 열의 앞쪽 좌석 id들. */
    private static List<Long> seatIds(JsonNode block, int row, int count) {
        return list(block.get("seats")).stream()
                .filter(seat -> seat.get("row").asInt() == row && seat.get("number").asInt() <= count)
                .map(seat -> seat.get("seatId").asLong())
                .toList();
    }

    private static String eventPath(long organizerId, long eventId) {
        return "/api/organizer/organizers/" + organizerId + "/events/" + eventId;
    }

    private static String zonesPath(long organizerId, long eventId) {
        return eventPath(organizerId, eventId) + "/zones";
    }

    private static String blocksPath(long organizerId, long eventId) {
        return eventPath(organizerId, eventId) + "/blocks";
    }

    private static String seatsPath(long organizerId, long eventId) {
        return eventPath(organizerId, eventId) + "/seats";
    }

    private static Map<String, Object> blockBody(String name, int rows, int columns, Map<String, Object> position, Integer angle) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("rows", rows);
        body.put("columns", columns);
        body.put("position", position);
        body.put("angleDegrees", angle);
        return body;
    }

    /** 지정형 구역(5만원). */
    private static Map<String, Object> zoneBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", "DESIGNATED");
        body.put("name", name);
        body.put("color", "#E5484D");
        body.put("basePrice", 50_000);
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

        /** 지정형 구역 "R석"(5만원)을 더하고 구역 id를 돌려준다. */
        long addRSeat(long organizerId, long eventId) throws Exception {
            HttpResponse<String> created = post(zonesPath(organizerId, eventId), zoneBody("R석"));
            assertThat(created.statusCode()).isEqualTo(201);
            return jsonMapper.readTree(created.body()).get("zoneId").asLong();
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
