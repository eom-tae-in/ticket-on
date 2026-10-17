package com.ticketon.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.NewPassword;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 내 행사 목록을 본다(#45 완료 조건 — "내 행사 목록이 세 구역으로
 * 나온다"). 행사는 실제 API로 만들고 일정을 넣는다. 아직 API가 없는 판매 시작 뒤의 상태 · 가격 단계 진행 · 삭제는 표를 바로 고친다. 날짜는 오늘(한국
 * 날짜)에서 센다 — 지난 날짜의 회차도 넣을 수 있다(#41). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원, 늦여름의 소유자 최여름. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 ·
 * 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("내 행사 목록 — 세 구역 (진짜 PostgreSQL · 실제 서버)")
class OrganizerEventListIntegrationTest {

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

    @LocalServerPort
    private int port;

    private String junghoonEmail;
    private String plannerEmail;
    private String yeoreumEmail;
    private long twelveMonths;
    private long lateSummer;
    private LocalDate today;

    @BeforeEach
    void storeOrganizers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        plannerEmail = "planner-" + suffix + "@example.com";
        yeoreumEmail = "yeoreum-" + suffix + "@example.com";
        long junghoon = storeStaff("고래" + suffix, junghoonEmail);
        long planner = storeStaff("바람" + suffix, plannerEmail);
        long yeoreum = storeStaff("여름" + suffix, yeoreumEmail);
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
        today = LocalDate.now(KoreanTime.ZONE);
    }

    @Test
    @DisplayName("운영자가 만든 행사들이 작성 중 · 진행 중 · 지난 행사로 나뉘고, 날짜가 지난 작성 중 행사는 접힌다 — 지운 행사와 다른 주최자의 행사는 없다")
    void listsThreeSections() throws Exception {
        long settled;
        long workshop;
        long band;
        long autumnReading;
        long newDraft;
        long deleted;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            settled = operator.createEvent(twelveMonths, "2026 봄 정기공연 〈열두 번째 달〉");
            operator.addSession(twelveMonths, settled, today.minusDays(40));
            operator.addSession(twelveMonths, settled, today.minusDays(39));
            workshop = operator.createEvent(twelveMonths, "여름 워크숍 공연");
            operator.addSession(twelveMonths, workshop, today.minusDays(1));
            band = operator.createEvent(twelveMonths, "○○밴드 단독공연");
            operator.addSession(twelveMonths, band, today.plusDays(70));
            autumnReading = operator.createEvent(twelveMonths, "가을 낭독회");
            operator.addSession(twelveMonths, autumnReading, today.plusDays(20));
            operator.addSession(twelveMonths, autumnReading, today.plusDays(21));
            newDraft = operator.createEvent(twelveMonths, "새 행사");
            deleted = operator.createEvent(twelveMonths, "지운 행사");
        }
        try (Browser yeoreum = new Browser()) {
            yeoreum.logInStaff(yeoreumEmail);
            yeoreum.createEvent(lateSummer, "늦여름 페스티벌 2026");
        }
        startSales(settled, "SETTLED");
        startSales(autumnReading, "ON_SALE");
        jdbcTemplate.update("UPDATE event SET wizard_progress_step = 'PRICING' WHERE id = ?", band);
        jdbcTemplate.update("UPDATE event SET deleted_at = now() WHERE id = ?", deleted);

        JsonNode list;
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);
            HttpResponse<String> response = owner.get(eventsPath(twelveMonths));
            assertThat(response.statusCode()).isEqualTo(200);
            list = jsonMapper.readTree(response.body());
        }

        JsonNode drafts = list.get("drafts");
        assertThat(drafts).extracting(draft -> draft.get("eventId").asLong()).containsExactly(newDraft, band, workshop);
        assertThat(drafts).extracting(draft -> draft.get("folded").asBoolean()).containsExactly(false, false, true);
        JsonNode bandCard = drafts.get(1);
        assertThat(bandCard.get("title").asString()).isEqualTo("○○밴드 단독공연");
        assertThat(bandCard.get("wizardProgress").get("step").asString()).isEqualTo("PRICING");
        assertThat(bandCard.get("wizardProgress").get("stepNumber").asInt()).isEqualTo(4);
        assertThat(bandCard.get("wizardProgress").get("stepCount").asInt()).isEqualTo(7);
        assertThat(bandCard.get("resumeStep").asString()).isEqualTo("BOOKING_SETTINGS");
        assertThat(bandCard.get("firstDate").asString()).isEqualTo(today.plusDays(70).toString());
        assertThat(bandCard.get("posterUrl").isNull()).isTrue();
        assertThat(OffsetDateTime.parse(bandCard.get("lastModifiedAt").asString()).toInstant())
                .isEqualTo(lastModifiedAt(band).toInstant());
        assertThat(bandCard.get("lastModifiedAt").asString()).endsWith("+09:00");
        JsonNode newCard = drafts.get(0);
        assertThat(newCard.get("wizardProgress").get("step").asString()).isEqualTo("BASIC_INFORMATION");
        assertThat(newCard.get("resumeStep").asString()).isEqualTo("SCHEDULE");
        assertThat(newCard.get("firstDate").isNull()).isTrue();
        assertThat(drafts.get(2).get("lastDate").asString()).isEqualTo(today.minusDays(1).toString());

        assertThat(list.get("inProgress")).singleElement().satisfies(event -> {
            assertThat(event.get("eventId").asLong()).isEqualTo(autumnReading);
            assertThat(event.get("status").asString()).isEqualTo("ON_SALE");
            assertThat(event.get("firstDate").asString()).isEqualTo(today.plusDays(20).toString());
            assertThat(event.get("lastDate").asString()).isEqualTo(today.plusDays(21).toString());
        });
        assertThat(list.get("past")).singleElement().satisfies(event -> {
            assertThat(event.get("eventId").asLong()).isEqualTo(settled);
            assertThat(event.get("status").asString()).isEqualTo("SETTLED");
            assertThat(event.get("firstDate").asString()).isEqualTo(today.minusDays(40).toString());
        });
    }

    @Test
    @DisplayName("다른 주최자의 운영진이 열두달 경로로 보면 403이고, 자기 주최자 경로로는 자기 행사만 보인다")
    void otherOrganizersEventsAreHidden() throws Exception {
        long springPlay;
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);
            springPlay = owner.createEvent(twelveMonths, "2026 봄 정기공연 〈열두 번째 달〉");
        }
        try (Browser yeoreum = new Browser()) {
            yeoreum.logInStaff(yeoreumEmail);
            long festival = yeoreum.createEvent(lateSummer, "늦여름 페스티벌 2026");

            HttpResponse<String> denied = yeoreum.get(eventsPath(twelveMonths));
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(jsonMapper.readValue(denied.body(), ErrorResponse.class).code()).isEqualTo("NOT_ORGANIZER_STAFF");

            JsonNode own = jsonMapper.readTree(yeoreum.get(eventsPath(lateSummer)).body());
            assertThat(own.get("drafts")).extracting(draft -> draft.get("eventId").asLong()).containsExactly(festival)
                    .doesNotContain(springPlay);
            assertThat(own.get("inProgress")).isEmpty();
            assertThat(own.get("past")).isEmpty();
        }
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void requiresLogin() throws Exception {
        try (Browser anonymous = new Browser()) {
            assertThat(anonymous.get(eventsPath(twelveMonths)).statusCode()).isEqualTo(401);
        }
    }

    /** 판매를 시작한 뒤의 상태로 바로 고친다 — 아직 판매 시작 API가 없다. 예매가 열린 시각도 함께 적는다(event_booking_opened_at_ck). */
    private void startSales(long eventId, String status) {
        jdbcTemplate.update("UPDATE event SET status = ?, booking_opened_at = now() WHERE id = ?", status, eventId);
    }

    private OffsetDateTime lastModifiedAt(long eventId) {
        return jdbcTemplate.queryForObject("SELECT last_modified_at FROM event WHERE id = ?", OffsetDateTime.class, eventId);
    }

    private static String eventsPath(long organizerId) {
        return "/api/organizer/organizers/" + organizerId + "/events";
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
            return send(path, "POST", body);
        }

        HttpResponse<String> put(String path, Object body) throws Exception {
            return send(path, "PUT", body);
        }

        /** 이 세션의 CSRF 토큰을 받아 싣고 보낸다. */
        private HttpResponse<String> send(String path, String method, Object body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
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

        /** 1단계를 넘겨 행사를 만들고 행사 id를 돌려준다 — 작성 중, 진행 단계 1단계. */
        long createEvent(long organizerId, String title) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", title);
            body.put("type", "PERFORMANCE");
            body.put("categoryName", "연극");
            body.put("introduction", "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.");
            body.put("venueName", "○○아트홀");
            body.put("venueAddress", "서울특별시 종로구 대학로 12");
            HttpResponse<String> created = post(eventsPath(organizerId), body);
            assertThat(created.statusCode()).isEqualTo(201);
            return jsonMapper.readTree(created.body()).get("eventId").asLong();
        }

        /** 회차형으로 두고 그 날짜 19:00 회차를 더한다 — 위저드 2단계. */
        void addSession(long organizerId, long eventId, LocalDate date) throws Exception {
            String schedulePath = eventsPath(organizerId) + "/" + eventId + "/schedule";
            assertThat(put(schedulePath + "/session-mode", Map.of("sessionMode", "TIMED")).statusCode()).isEqualTo(200);
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("date", date.toString());
            session.put("startTime", "19:00");
            session.put("endTime", "21:00");
            assertThat(post(schedulePath + "/sessions", session).statusCode()).isEqualTo(201);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
