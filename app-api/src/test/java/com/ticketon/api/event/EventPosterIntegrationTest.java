package com.ticketon.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.common.TestImages;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.policy.ProposedPolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)과 로컬 파일 저장소(임시 디렉터리)로 띄우고, 실제 HTTP 요청으로 운영진이 행사를 만든 뒤 1단계
 * 포스터를 올리고 바꾸는 흐름을 끝까지 돌린다(#40 완료 조건 — 3:4 포스터만 저장되고 용량 상한을 지킨다). 3:4 통과, 1:1 거부, 상한 초과 거부, 교체
 * (바꾸기 전 파일 정리), 로그인 없이 공개 주소로 열기, 거절된 파일은 아무것도 남기지 않음, 판매가 끝난 행사는 고정. Docker가 없는 환경에서는
 * 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원, 늦여름의 소유자 최여름. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인은 실제
 * 로그인 API로 한다. 테스트끼리 같은 DB · 저장소를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓰고, 파일은 테스트 앞뒤로 견줘 본다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 포스터 올리기 (진짜 PostgreSQL · 로컬 저장소 · 실제 서버)")
class EventPosterIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final String PUBLIC_BASE_URL = "http://files.ticket-on.test/api/public/files";

    private static final String POSTER_BUNDLE = "event-posters";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @TempDir
    private static Path storageDirectory;

    @DynamicPropertySource
    static void localStorage(DynamicPropertyRegistry registry) {
        registry.add("ticketon.storage.local.directory", () -> storageDirectory.toString());
        registry.add("ticketon.storage.local.public-base-url", () -> PUBLIC_BASE_URL);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashPort passwordHashPort;

    private String suffix;
    private String plannerEmail;
    private String yeoreumEmail;
    private long planner;
    private long twelveMonths;
    private long lateSummer;

    @BeforeEach
    void storeOrganizers() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        long junghoon = storeStaff("고래" + suffix, "junghoon-" + suffix + "@example.com");
        plannerEmail = "planner-" + suffix + "@example.com";
        planner = storeStaff("바람" + suffix, plannerEmail);
        yeoreumEmail = "yeoreum-" + suffix + "@example.com";
        long yeoreum = storeStaff("여름" + suffix, yeoreumEmail);
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
    }

    @Test
    @DisplayName("운영자가 3:4 포스터를 올리면 추측할 수 없는 열쇠로 로컬 저장소에 저장되고, 위저드 값의 포스터 주소로 누구나 로그인 없이 연다")
    void operatorUploadsPosterAndItIsPublished() throws Exception {
        byte[] poster = TestImages.jpeg(900, 1200);
        long eventId;
        String posterUrl;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay();

            HttpResponse<String> uploaded = operator.uploadPoster(eventId, "../../봄 포스터.jpg", "image/jpeg", poster);

            assertThat(uploaded.statusCode()).isEqualTo(200);
            JsonNode body = jsonMapper.readTree(uploaded.body());
            posterUrl = body.get("posterUrl").asString();
            assertThat(posterUrl).matches(PUBLIC_BASE_URL.replace(".", "\\.") + "/event-posters/[A-Za-z0-9_-]{32}\\.jpg");
            assertThat(body.get("eventId").asLong()).isEqualTo(eventId);
            assertThat(body.get("wizardProgress").get("step").asString()).isEqualTo("BASIC_INFORMATION");

            HttpResponse<String> resumed = operator.get(eventPath(eventId));
            assertThat(jsonMapper.readTree(resumed.body()).get("posterUrl").asString()).isEqualTo(posterUrl);
        }
        String fileKey = fileKeyOf(posterUrl);
        assertThat(storageDirectory.resolve(POSTER_BUNDLE).resolve(fileKey)).hasBinaryContent(poster);
        try (Browser visitor = new Browser()) {
            HttpResponse<byte[]> file = visitor.getBytes(URI.create(posterUrl).getPath());

            assertThat(file.statusCode()).isEqualTo(200);
            assertThat(file.headers().firstValue(HttpHeaders.CONTENT_TYPE)).hasValue(MediaType.IMAGE_JPEG_VALUE);
            assertThat(file.body()).isEqualTo(poster);
        }
        assertThat(posterFileKey(eventId)).isEqualTo(fileKey);
        assertThat(operationSummaries()).containsExactly(
                "CREATE_EVENT 행사 만들기 " + planner, "EDIT_EVENT 행사 포스터 올리기 (1단계) " + planner);
    }

    @Test
    @DisplayName("다시 올리면 새 열쇠로 바뀌고 바꾸기 전 포스터의 파일은 저장소에서 지워져 공개 주소로 열리지 않는다")
    void replacingPosterDeletesPreviousFile() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay();
            String firstUrl = posterUrlOf(operator.uploadPoster(eventId, "poster.jpg", "image/jpeg", TestImages.jpeg(300, 400)));

            String secondUrl = posterUrlOf(operator.uploadPoster(eventId, "poster.png", "image/png", TestImages.png(900, 1200)));

            assertThat(secondUrl).isNotEqualTo(firstUrl).endsWith(".png");
            assertThat(storageDirectory.resolve(POSTER_BUNDLE).resolve(fileKeyOf(firstUrl))).doesNotExist();
            expectPosterNotFound(operator.getBytes(URI.create(firstUrl).getPath()));
            assertThat(operator.getBytes(URI.create(secondUrl).getPath()).statusCode()).isEqualTo(200);
            assertThat(posterFileKey(eventId)).isEqualTo(fileKeyOf(secondUrl));
        }
    }

    @Test
    @DisplayName("1:1 그림 · 용량 상한(제안 5MB) 초과 · 그림이 아닌 파일 · 요청 울타리(제안 10MB) 초과는 400이고 파일 · 포스터 · 기록이 남지 않는다")
    void rejectedPostersLeaveNothing() throws Exception {
        Set<String> filesBefore = posterFiles();
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay();

            expectPosterRejected(operator.uploadPoster(eventId, "poster.png", "image/png", TestImages.png(1200, 1200)),
                    "포스터는 3:4 세로 비율로 잘라 올려 주세요.");
            byte[] overPosterLimit = Arrays.copyOf(TestImages.png(30, 40),
                    (int) ProposedPolicy.EVENT_POSTER_MAXIMUM_SIZE.bytes() + 1);
            expectPosterRejected(operator.uploadPoster(eventId, "poster.png", "image/png", overPosterLimit),
                    "포스터는 5MB 이하로 올려 주세요.");
            expectPosterRejected(operator.uploadPoster(eventId, "poster.jpg", "image/jpeg",
                    "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)),
                    "포스터는 PNG 또는 JPEG 그림만 올릴 수 있습니다.");

            byte[] overRequestLimit = new byte[(int) ProposedPolicy.UPLOAD_REQUEST_MAXIMUM_SIZE.bytes() + 1];
            HttpResponse<String> tooLarge = operator.uploadPoster(eventId, "poster.png", "image/png", overRequestLimit);
            assertThat(tooLarge.statusCode()).isEqualTo(400);
            assertThat(jsonMapper.readValue(tooLarge.body(), ErrorResponse.class).code()).isEqualTo("INVALID_INPUT");
        }
        assertThat(posterFiles()).isEqualTo(filesBefore);
        assertThat(posterFileKey(eventId)).isNull();
        assertThat(operationSummaries()).containsExactly("CREATE_EVENT 행사 만들기 " + planner);
    }

    @Test
    @DisplayName("판매가 끝난 행사는 포스터가 고정이라 409이고, 지금 포스터와 그 파일은 그대로다")
    void posterIsLockedAfterSalesClosed() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = operator.createSpringPlay();
            String posterUrl = posterUrlOf(operator.uploadPoster(eventId, "poster.jpg", "image/jpeg", TestImages.jpeg(300, 400)));
            jdbcTemplate.update("UPDATE event SET status = 'SALES_CLOSED', booking_opened_at = now() WHERE id = ?", eventId);
            Set<String> filesBefore = posterFiles();

            expectError(operator.uploadPoster(eventId, "poster.jpg", "image/jpeg", TestImages.jpeg(300, 400)), 409,
                    "EDIT_LOCKED_AFTER_SALES_CLOSED");

            assertThat(posterFiles()).isEqualTo(filesBefore);
            assertThat(posterFileKey(eventId)).isEqualTo(fileKeyOf(posterUrl));
            assertThat(operator.getBytes(URI.create(posterUrl).getPath()).statusCode()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 열두달 경로로 403, 자기 주최자 경로로 열두달 행사에 닿으면 404다. 포스터 열쇠의 꼴이 아닌 공개 주소는 404다")
    void outsidersAndMalformedKeys() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = operator.createSpringPlay();
        }
        Set<String> filesBefore = posterFiles();
        try (Browser stranger = new Browser()) {
            stranger.logInStaff(yeoreumEmail);

            expectError(stranger.uploadPoster(twelveMonths, eventId, "poster.jpg", "image/jpeg", TestImages.jpeg(300, 400)), 403,
                    "NOT_ORGANIZER_STAFF");
            expectError(stranger.uploadPoster(lateSummer, eventId, "poster.jpg", "image/jpeg", TestImages.jpeg(300, 400)), 404,
                    "EVENT_NOT_FOUND");
        }
        try (Browser visitor = new Browser()) {
            expectPosterNotFound(visitor.getBytes("/api/public/files/event-posters/not-a-poster-key.jpg"));
            assertThat(visitor.getBytes("/api/public/files/event-posters/..%2F..%2Fetc%2Fpasswd").statusCode()).isIn(400, 404);
        }
        assertThat(posterFiles()).isEqualTo(filesBefore);
        assertThat(posterFileKey(eventId)).isNull();
    }

    private void expectPosterRejected(HttpResponse<String> response, String message) {
        assertThat(response.statusCode()).isEqualTo(400);
        ErrorResponse error = jsonMapper.readValue(response.body(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("INVALID_INPUT");
        assertThat(error.fieldErrors()).singleElement().satisfies(fieldError -> {
            assertThat(fieldError.field()).isEqualTo("poster");
            assertThat(fieldError.message()).isEqualTo(message);
        });
    }

    private void expectPosterNotFound(HttpResponse<byte[]> response) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo("EVENT_POSTER_NOT_FOUND");
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private String posterUrlOf(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readTree(response.body()).get("posterUrl").asString();
    }

    private static String fileKeyOf(String posterUrl) {
        return posterUrl.substring(posterUrl.lastIndexOf('/') + 1);
    }

    private String posterFileKey(long eventId) {
        return jdbcTemplate.queryForObject("SELECT poster_file_key FROM event WHERE id = ?", String.class, eventId);
    }

    /** 저장소의 포스터 묶음에 있는 파일 이름들 — 묶음이 아직 없으면 비어 있다. */
    private static Set<String> posterFiles() {
        Path bundle = storageDirectory.resolve(POSTER_BUNDLE);
        if (!Files.exists(bundle)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(bundle)) {
            return files.map(file -> file.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** 열두달의 행사 조작 기록 — "한 일 요약 운영진 id"를 넣은 차례대로. */
    private List<String> operationSummaries() {
        return jdbcTemplate.queryForList("""
                SELECT staff_action || ' ' || summary || ' ' || staff_account_id FROM staff_operation_record
                WHERE organizer_id = ? AND target_kind = 'EVENT' ORDER BY id""", String.class, twelveMonths);
    }

    private String eventPath(long eventId) {
        return "/api/organizer/organizers/" + twelveMonths + "/events/" + eventId;
    }

    /** 극단 열두달의 봄 정기공연 1단계 — 공연 · 연극(docs/03 "기본 정보"). */
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

    /** 이메일 인증까지 마친 운영진 로그인 한 줄 — 가입은 #15가 하지만 여기서는 표에 바로 넣는다. */
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

        HttpResponse<byte[]> getBytes(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        /** 이 세션의 CSRF 토큰을 받아 싣고 JSON 본문으로 보낸다. */
        HttpResponse<String> post(String path, Object body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** 열두달에 봄 정기공연을 만들고 그 행사 id를 돌려준다. */
        long createSpringPlay() throws Exception {
            HttpResponse<String> created = post("/api/organizer/organizers/" + twelveMonths + "/events", springPlayBody());
            assertThat(created.statusCode()).isEqualTo(201);
            return jsonMapper.readTree(created.body()).get("eventId").asLong();
        }

        /** 열두달의 그 행사에 포스터 파일 하나를 multipart/form-data의 poster 칸으로 올린다. */
        HttpResponse<String> uploadPoster(long eventId, String fileName, String contentType, byte[] content) throws Exception {
            return uploadPoster(twelveMonths, eventId, fileName, contentType, content);
        }

        /** 그 주최자 경로의 그 행사에 포스터 파일 하나를 multipart/form-data의 poster 칸으로 올린다. */
        HttpResponse<String> uploadPoster(long organizerId, long eventId, String fileName, String contentType, byte[] content)
                throws Exception {
            String boundary = "ticketon-" + UUID.randomUUID();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"poster\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(content);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(
                            uri("/api/organizer/organizers/" + organizerId + "/events/" + eventId + "/poster"))
                    .header(CSRF_HEADER, csrfToken())
                    .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
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

        @Override
        public void close() {
            client.close();
        }
    }
}
