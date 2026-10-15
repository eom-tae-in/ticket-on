package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.common.TestImages;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.policy.ProposedPolicy;
import java.io.ByteArrayOutputStream;
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
import java.util.UUID;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)과 로컬 파일 저장소(임시 디렉터리)로 띄우고, 실제 HTTP 요청으로 운영진이 소개 · SNS 링크 · 로고를
 * 편집하고 로고가 로컬 저장소에 저장 · 공개되는 흐름을 끝까지 돌린다(#29 완료 조건). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인은 실제 로그인 API로 한다.
 * 테스트끼리 같은 DB · 저장소를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 공개 정보 편집과 로고 업로드 (진짜 PostgreSQL · 로컬 저장소 · 실제 서버)")
class OrganizerPublicProfileIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final String PUBLIC_BASE_URL = "http://files.ticket-on.test/api/public/files";

    private static final String INTRODUCTION = "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.";

    private static final String INSTAGRAM = "https://www.instagram.com/twelve_months_theater";

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
    private long planner;
    private long twelveMonths;

    @BeforeEach
    void storeTwelveMonths() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        long junghoon = storeStaff("고래" + suffix, "junghoon-" + suffix + "@example.com");
        plannerEmail = "planner-" + suffix + "@example.com";
        planner = storeStaff("바람" + suffix, plannerEmail);
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
    }

    @Test
    @DisplayName("운영자가 소개 · SNS 링크를 고치고 로고를 올리면, 로고는 로컬 저장소에 열쇠 이름으로 저장되고 로그인 없이 공개 주소로 열린다")
    void operatorEditsProfileAndLogoIsPublished() throws Exception {
        byte[] logo = TestImages.png(256, 256);
        String logoUrl;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);

            HttpResponse<String> changed = operator.put(profilePath(),
                    profileBody(" " + INTRODUCTION + " ", List.of(INSTAGRAM, "https://blog.example.com/twelve-months")));
            assertThat(changed.statusCode()).isEqualTo(200);
            JsonNode changedBody = jsonMapper.readTree(changed.body());
            assertThat(changedBody.get("introduction").asString()).isEqualTo(INTRODUCTION);
            assertThat(changedBody.get("snsLinks")).hasSize(2);
            assertThat(changedBody.get("logoUrl").isNull()).isTrue();

            HttpResponse<String> uploaded = operator.uploadLogo("../../내 로고.png", "image/png", logo);
            assertThat(uploaded.statusCode()).isEqualTo(200);
            JsonNode uploadedBody = jsonMapper.readTree(uploaded.body());
            logoUrl = uploadedBody.get("logoUrl").asString();
            assertThat(logoUrl).matches(PUBLIC_BASE_URL.replace(".", "\\.") + "/organizer-logos/[A-Za-z0-9_-]{32}\\.png");
            assertThat(uploadedBody.get("introduction").asString()).isEqualTo(INTRODUCTION);

            HttpResponse<String> shown = operator.get(profilePath());
            assertThat(shown.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(shown.body()).get("logoUrl").asString()).isEqualTo(logoUrl);
        }
        String fileKey = fileKeyOf(logoUrl);
        assertThat(storageDirectory.resolve("organizer-logos").resolve(fileKey)).hasBinaryContent(logo);
        try (Browser visitor = new Browser()) {
            HttpResponse<byte[]> file = visitor.getBytes(URI.create(logoUrl).getPath());

            assertThat(file.statusCode()).isEqualTo(200);
            assertThat(file.headers().firstValue(HttpHeaders.CONTENT_TYPE)).hasValue(MediaType.IMAGE_PNG_VALUE);
            assertThat(file.body()).isEqualTo(logo);
        }

        assertThat(jdbcTemplate.queryForObject("SELECT logo_file_key FROM organizer WHERE id = ?", String.class,
                twelveMonths)).isEqualTo(fileKey);
        assertThat(jdbcTemplate.queryForList(
                "SELECT url FROM organizer_sns_link WHERE organizer_id = ? ORDER BY display_order", String.class, twelveMonths))
                .containsExactly(INSTAGRAM, "https://blog.example.com/twelve-months");
        assertThat(jdbcTemplate.queryForList("""
                SELECT summary FROM staff_operation_record
                WHERE organizer_id = ? AND staff_account_id = ? AND staff_action = 'EDIT_PUBLIC_PROFILE'
                  AND target_kind = 'ORGANIZER' AND target_id = ?
                ORDER BY id""", String.class, twelveMonths, planner, twelveMonths))
                .containsExactly("공개 정보 수정 (소개 · SNS 링크)", "로고 올리기");
    }

    @Test
    @DisplayName("로고를 다시 올리면 이전 파일이 지워져 이전 주소는 404이고, 로고를 지우면 첫 글자 + 정해진 색 표시로 돌아간다")
    void replacingAndRemovingLogo() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            String firstUrl = logoUrlOf(operator.uploadLogo("logo.png", "image/png", TestImages.png(128, 128)));
            String secondUrl = logoUrlOf(operator.uploadLogo("logo.jpg", "image/jpeg", TestImages.jpeg(300, 300)));

            assertThat(secondUrl).isNotEqualTo(firstUrl).endsWith(".jpg");
            assertThat(storageDirectory.resolve("organizer-logos").resolve(fileKeyOf(firstUrl))).doesNotExist();
            expectLogoNotFound(operator.getBytes(URI.create(firstUrl).getPath()));
            assertThat(operator.getBytes(URI.create(secondUrl).getPath()).statusCode()).isEqualTo(200);

            HttpResponse<String> removed = operator.delete(profilePath() + "/logo");

            assertThat(removed.statusCode()).isEqualTo(200);
            JsonNode body = jsonMapper.readTree(removed.body());
            assertThat(body.get("logoUrl").isNull()).isTrue();
            assertThat(body.get("avatar").get("initial").asString()).isEqualTo("극");
            assertThat(body.get("avatar").get("paletteIndex").asInt()).isEqualTo((int) (twelveMonths % 8));
            assertThat(storageDirectory.resolve("organizer-logos").resolve(fileKeyOf(secondUrl))).doesNotExist();
            expectLogoNotFound(operator.getBytes(URI.create(secondUrl).getPath()));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT logo_file_key FROM organizer WHERE id = ?", String.class,
                twelveMonths)).isNull();
        assertThat(jdbcTemplate.queryForList("""
                SELECT summary FROM staff_operation_record WHERE organizer_id = ? AND staff_action = 'EDIT_PUBLIC_PROFILE'
                ORDER BY id""", String.class, twelveMonths))
                .containsExactly("로고 올리기", "로고 올리기", "로고 삭제");
    }

    @Test
    @DisplayName("정사각형이 아닌 그림 · 용량 상한 초과 · 받지 않는 형식 · 요청 울타리 초과는 400이고 아무것도 저장 · 기록되지 않는다")
    void rejectedLogosLeaveNothing() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);

            expectLogoRejected(operator.uploadLogo("logo.png", "image/png", TestImages.png(400, 300)),
                    "로고는 정사각형으로 잘라 올려 주세요.");
            byte[] overLogoLimit = Arrays.copyOf(TestImages.png(64, 64),
                    (int) ProposedPolicy.ORGANIZER_LOGO_MAXIMUM_SIZE.bytes() + 1);
            expectLogoRejected(operator.uploadLogo("logo.png", "image/png", overLogoLimit), "로고는 2MB 이하로 올려 주세요.");
            expectLogoRejected(operator.uploadLogo("logo.png", "image/png",
                    "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)),
                    "로고는 PNG 또는 JPEG 그림만 올릴 수 있습니다.");

            byte[] overRequestLimit = Arrays.copyOf(TestImages.png(64, 64),
                    (int) ProposedPolicy.UPLOAD_REQUEST_MAXIMUM_SIZE.bytes() + 1);
            HttpResponse<String> tooLarge = operator.uploadLogo("logo.png", "image/png", overRequestLimit);
            assertThat(tooLarge.statusCode()).isEqualTo(400);
            assertThat(jsonMapper.readValue(tooLarge.body(), ErrorResponse.class).code()).isEqualTo("INVALID_INPUT");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT logo_file_key FROM organizer WHERE id = ?", String.class,
                twelveMonths)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM staff_operation_record WHERE organizer_id = ?",
                Integer.class, twelveMonths)).isZero();
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 403이고, 공개 경로는 로고 열쇠의 꼴이 아닌 값에 파일을 내주지 않는다")
    void outsidersAndMalformedKeys() throws Exception {
        String outsiderEmail = "outsider-" + suffix + "@example.com";
        long outsider = storeStaff("밤기타" + suffix, outsiderEmail);
        long lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, outsider, "OWNER");

        try (Browser stranger = new Browser()) {
            stranger.logInStaff(outsiderEmail);
            HttpResponse<String> rejected = stranger.uploadLogo("logo.png", "image/png", TestImages.png(64, 64));

            assertThat(rejected.statusCode()).isEqualTo(403);
            assertThat(jsonMapper.readValue(rejected.body(), ErrorResponse.class).code()).isEqualTo("NOT_ORGANIZER_STAFF");
        }
        try (Browser visitor = new Browser()) {
            expectLogoNotFound(visitor.getBytes("/api/public/files/organizer-logos/not-a-logo-key.png"));
            assertThat(visitor.getBytes("/api/public/files/organizer-logos/..%2F..%2Fetc%2Fpasswd").statusCode())
                    .isIn(400, 404);
        }
    }

    private void expectLogoRejected(HttpResponse<String> response, String message) {
        assertThat(response.statusCode()).isEqualTo(400);
        ErrorResponse error = jsonMapper.readValue(response.body(), ErrorResponse.class);
        assertThat(error.code()).isEqualTo("INVALID_INPUT");
        assertThat(error.fieldErrors()).singleElement().satisfies(fieldError -> {
            assertThat(fieldError.field()).isEqualTo("logo");
            assertThat(fieldError.message()).isEqualTo(message);
        });
    }

    private void expectLogoNotFound(HttpResponse<byte[]> response) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo("ORGANIZER_LOGO_NOT_FOUND");
    }

    private String logoUrlOf(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readTree(response.body()).get("logoUrl").asString();
    }

    private static String fileKeyOf(String logoUrl) {
        return logoUrl.substring(logoUrl.lastIndexOf('/') + 1);
    }

    private String profilePath() {
        return "/api/organizer/organizers/" + twelveMonths + "/public-profile";
    }

    private static Map<String, Object> profileBody(String introduction, List<String> snsLinks) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("introduction", introduction);
        body.put("snsLinks", snsLinks);
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

        HttpResponse<String> put(String path, Object body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpResponse<String> delete(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrfToken()).DELETE().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** 로고 파일 하나를 multipart/form-data의 logo 칸으로 올린다. */
        HttpResponse<String> uploadLogo(String fileName, String contentType, byte[] content) throws Exception {
            String boundary = "ticketon-" + UUID.randomUUID();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"logo\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(content);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri(profilePath() + "/logo"))
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
