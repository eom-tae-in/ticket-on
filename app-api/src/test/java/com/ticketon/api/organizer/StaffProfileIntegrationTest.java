package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.account.MyStaffProfileResponse;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 내 프로필 편집 → 로그인 없이 운영진 프로필 보기를 끝까지 돌린다
 * (#28 완료 조건 — 편집과 공개 조회가 동작하고 개인정보 칸이 응답에 없다). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 박정훈(docs/15)은 극단 열두달 소유자 · 2019~(지금), 늦여름 운영자 · 2023~2024(나옴), 극단 봄날 소유자 · 2021~2022(닫힘), 정지된 극단
 * 운영자(영구 정지)였다. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인은 실제 로그인 API로 한다. 테스트끼리 같은 DB를 쓰므로 테스트마다
 * 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영진 프로필 — 내 프로필 편집과 공개 조회 (진짜 PostgreSQL·실제 서버)")
class StaffProfileIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String MY_PROFILE_PATH = "/api/organizer/account/profile";

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final String INTRODUCTION = "극단에서 연출하고 있습니다";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashPort passwordHashPort;

    private String suffix;
    private String junghoonName;
    private String junghoonEmail;
    private long junghoon;
    private long twelveMonths;
    private long lateSummer;
    private long springDay;

    @BeforeEach
    void storeJunghoonAndOrganizers() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonName = "박정훈" + suffix;
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        junghoon = storeStaff(junghoonName, "고래" + suffix, junghoonEmail);
        long yeoreum = storeStaff("최여름" + suffix, "여름" + suffix, "yeoreum-" + suffix + "@example.com");

        twelveMonths = storeOrganizer("극단 열두달 " + suffix, "ACTIVE");
        storeMembership(twelveMonths, junghoon, "OWNER", "2019-03-04 10:00:00+09", null);

        lateSummer = storeOrganizer("늦여름 " + suffix, "ACTIVE");
        storeMembership(lateSummer, yeoreum, "OWNER", "2019-03-04 10:00:00+09", null);
        storeMembership(lateSummer, junghoon, "OPERATOR", "2023-05-01 10:00:00+09", "2024-11-30 18:00:00+09");

        springDay = storeOrganizer("극단 봄날 " + suffix, "CLOSED");
        storeMembership(springDay, junghoon, "OWNER", "2021-03-01 10:00:00+09", "2022-12-31 18:00:00+09");

        long suspended = storeOrganizer("정지된 극단 " + suffix, "PERMANENTLY_SUSPENDED");
        storeMembership(suspended, junghoon, "OPERATOR", "2024-01-10 10:00:00+09", "2024-06-30 18:00:00+09");
    }

    @Test
    @DisplayName("내 프로필에서 닉네임 · 소개를 고치면, 로그인 없이 보는 운영진 프로필에 그대로 나오고 참여한 주최자가 기간 · 활동 종료와 함께 나온다")
    void editedProfileIsShownPublicly() throws Exception {
        String nickname = "조용한고래" + suffix.substring(0, 4);
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);
            HttpResponse<String> edited = owner.put(MY_PROFILE_PATH, profileBody(nickname, " " + INTRODUCTION + " "));
            assertThat(edited.statusCode()).isEqualTo(200);
            MyStaffProfileResponse changed = jsonMapper.readValue(edited.body(), MyStaffProfileResponse.class);
            assertThat(changed.staffAccountId()).isEqualTo(junghoon);
            assertThat(changed.nickname()).isEqualTo(nickname);
            assertThat(changed.introduction()).isEqualTo(INTRODUCTION);
            assertThat(changed.avatar().initial()).isEqualTo("조");
            assertThat(changed.avatar().paletteIndex()).isEqualTo((int) (junghoon % 8));

            HttpResponse<String> mine = owner.get(MY_PROFILE_PATH);
            assertThat(mine.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(mine.body(), MyStaffProfileResponse.class)).isEqualTo(changed);
        }

        try (Browser visitor = new Browser()) {
            HttpResponse<String> profile = visitor.get(publicProfilePath(junghoon));

            assertThat(profile.statusCode()).isEqualTo(200);
            JsonNode body = jsonMapper.readTree(profile.body());
            assertThat(body.get("nickname").asString()).isEqualTo(nickname);
            assertThat(body.get("introduction").asString()).isEqualTo(INTRODUCTION);
            assertThat(body.get("avatar").get("initial").asString()).isEqualTo("조");
            JsonNode participations = body.get("participatedOrganizers");
            assertThat(participations).hasSize(3);
            assertParticipation(participations.get(0), twelveMonths, "OWNER", "2019-03-04T10:00:00+09:00", null, false);
            assertParticipation(participations.get(1), springDay, "OWNER", "2021-03-01T10:00:00+09:00",
                    "2022-12-31T18:00:00+09:00", true);
            assertParticipation(participations.get(2), lateSummer, "OPERATOR", "2023-05-01T10:00:00+09:00",
                    "2024-11-30T18:00:00+09:00", true);
        }
    }

    @Test
    @DisplayName("운영진 프로필 응답에는 실명 · 이메일 칸이 없고, 그 값도 담기지 않는다 (docs/01 \"운영진 프로필 — 보이지 않는 것\")")
    void publicProfileHasNoPersonalInformation() throws Exception {
        try (Browser visitor = new Browser()) {
            HttpResponse<String> profile = visitor.get(publicProfilePath(junghoon));

            assertThat(profile.statusCode()).isEqualTo(200);
            JsonNode body = jsonMapper.readTree(profile.body());
            assertThat(body.has("name")).isFalse();
            assertThat(body.has("email")).isFalse();
            assertThat(profile.body()).doesNotContain(junghoonName).doesNotContain(junghoonEmail);
        }
    }

    @Test
    @DisplayName("주최자에 소속된 적이 없는 운영진 로그인과 탈퇴한 운영진 로그인은 404다 — 탈퇴하면 소개도 파기된다")
    void noProfileForNeverAffiliatedOrWithdrawn() throws Exception {
        long newcomer = storeStaff("정새내" + suffix, "새내기" + suffix, "newcomer-" + suffix + "@example.com");
        String operatorEmail = "operator-" + suffix + "@example.com";
        long operator = storeStaff("김기획" + suffix, "바람" + suffix, operatorEmail);
        storeMembership(twelveMonths, operator, "OPERATOR", "2024-03-01 10:00:00+09", null);

        try (Browser visitor = new Browser()) {
            expectNotFound(visitor.get(publicProfilePath(newcomer)));
            assertThat(visitor.get(publicProfilePath(operator)).statusCode()).isEqualTo(200);
        }
        try (Browser leaving = new Browser()) {
            leaving.logInStaff(operatorEmail);
            assertThat(leaving.put(MY_PROFILE_PATH, profileBody("바람" + suffix, "기획을 맡고 있습니다")).statusCode())
                    .isEqualTo(200);
            assertThat(leaving.post("/api/organizer/account/withdrawal", null).statusCode()).isEqualTo(204);
        }
        try (Browser visitor = new Browser()) {
            expectNotFound(visitor.get(publicProfilePath(operator)));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT introduction FROM staff_account WHERE id = ?", String.class,
                operator)).isNull();
    }

    @Test
    @DisplayName("다른 운영진이 쓰는 닉네임으로 고치면 409이고, 함께 보낸 소개도 바뀌지 않는다")
    void takenNicknameRejectsWholeEdit() throws Exception {
        String takenNickname = "겹침" + suffix;
        storeStaff("이도현" + suffix, takenNickname, "dohyun-" + suffix + "@example.com");

        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);
            HttpResponse<String> rejected = owner.put(MY_PROFILE_PATH, profileBody(takenNickname, INTRODUCTION));

            assertThat(rejected.statusCode()).isEqualTo(409);
            assertThat(jsonMapper.readValue(rejected.body(), ErrorResponse.class).code())
                    .isEqualTo("STAFF_NICKNAME_ALREADY_TAKEN");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT introduction FROM staff_account WHERE id = ?", String.class,
                junghoon)).isNull();
    }

    private void assertParticipation(JsonNode participation, long organizerId, String role, String startedAt,
            String endedAt, boolean activityEnded) {
        assertThat(participation.get("organizerId").asLong()).isEqualTo(organizerId);
        assertThat(participation.get("role").asString()).isEqualTo(role);
        assertThat(participation.get("startedAt").asString()).isEqualTo(startedAt);
        if (endedAt == null) {
            assertThat(participation.get("endedAt").isNull()).isTrue();
        } else {
            assertThat(participation.get("endedAt").asString()).isEqualTo(endedAt);
        }
        assertThat(participation.get("activityEnded").asBoolean()).isEqualTo(activityEnded);
    }

    private void expectNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo("STAFF_PROFILE_NOT_FOUND");
    }

    private static String publicProfilePath(long staffAccountId) {
        return "/api/public/staff-profiles/" + staffAccountId;
    }

    private static Map<String, Object> profileBody(String nickname, String introduction) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nickname", nickname);
        body.put("introduction", introduction);
        return body;
    }

    /** 이메일 인증까지 마친 운영진 로그인 한 줄 — 가입은 #15가 하지만 여기서는 표에 바로 넣는다. */
    private long storeStaff(String name, String nickname, String email) {
        String passwordHash = passwordHashPort.hash(new NewPassword(STAFF_PASSWORD)).value();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', ?, ?, ?, lower(?), ?, now() - interval '30 days', now() - interval '30 days')
                RETURNING id""", Long.class, name, nickname, email, email, passwordHash);
        assertThat(id).isNotNull();
        return id;
    }

    /** 그 상태의 주최자 한 줄 — 승인 · 닫기 · 정지를 거치지 않고 표에 바로 넣는다. */
    private long storeOrganizer(String name, String status) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, ?) RETURNING id",
                Long.class, name, status);
        assertThat(id).isNotNull();
        return id;
    }

    /** 소속 한 줄 — 끝난 일시가 없으면 지금 소속. */
    private void storeMembership(long organizerId, long staffAccountId, String role, String startedAt, String endedAt) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at, ended_at)
                VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz)""", organizerId, staffAccountId, role, startedAt, endedAt);
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

        /** 이 세션의 CSRF 토큰을 받아 싣고 보낸다. 본문이 없으면 null. */
        HttpResponse<String> post(String path, Object body) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrfToken());
            if (body == null) {
                request.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body),
                                StandardCharsets.UTF_8));
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** 이 세션의 CSRF 토큰을 받아 싣고 JSON 본문으로 PUT한다. */
        HttpResponse<String> put(String path, Object body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
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
