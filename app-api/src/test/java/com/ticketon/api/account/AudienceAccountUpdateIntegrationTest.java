package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄워 마이페이지 계정 정보 변경(#18)을 끝까지 돌린다 — 전화번호 변경의 중복 확인과,
 * 마케팅 수신 동의 변경의 동의 기록·처리 결과 안내·알림 설정 표시. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 관객은 가입·이메일 인증 API로 만들고(인증 링크 값은 DB에서 읽는다), 변경 API는 그 계정으로 로그인한 요청으로 부른다. 처리 결과 안내 메일은
 * '기록만' 어댑터가 로그로 남긴다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 번호·이메일을 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("계정 정보 변경 — 전화번호·마케팅 수신 동의 (진짜 PostgreSQL)")
class AudienceAccountUpdateIntegrationTest {

    private static final String PHONE_NUMBER_PATH = "/api/audience/account/phone-number";

    private static final String SETTINGS_PATH = "/api/audience/notification-settings";

    private static final String MARKETING_PATH = SETTINGS_PATH + "/marketing";

    private static final String ALREADY_REGISTERED_MESSAGE = "이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요.";

    private static final LocalDateTime SIGNED_UP_AT = LocalDateTime.of(2026, 2, 20, 10, 0);

    /** docs/12 "알림 설정" 예시의 동의 일시 — "2026-03-01 동의 · 언제든 철회할 수 있습니다". */
    private static final LocalDateTime AGREED_AT = LocalDateTime.of(2026, 3, 1, 9, 30);

    private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 4, 10, 21, 0);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    /** 서비스 기준 시계. 칸 타입은 Clock이어야 같은 이름의 빈을 바꾼다(AudienceSignUpIntegrationTest 참고). */
    private static final MutableClock MUTABLE_CLOCK = MutableClock.at(SIGNED_UP_AT);

    @TestBean(name = "clock")
    private Clock clock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    static Clock clock() {
        return MUTABLE_CLOCK;
    }

    @BeforeEach
    void resetClock() {
        MUTABLE_CLOCK.moveTo(SIGNED_UP_AT);
    }

    @Test
    @DisplayName("다른 계정이 쓰는 번호로는 바꿀 수 없다 — 409 \"이미 가입된 번호입니다…\", 번호는 그대로다")
    void phoneNumberOfAnotherAccountIsRejected() throws Exception {
        long seoyun = signUpAndVerify("phone-taken@example.com", "010-4444-0001", false);
        signUpAndVerify("phone-owner@example.com", "010-4444-0002", false);

        mockMvc.perform(changePhoneNumber(seoyun, "010-4444-0002"))
                .andExpect(status().isConflict())
                .andExpect(errorBody("PHONE_NUMBER_ALREADY_REGISTERED", ALREADY_REGISTERED_MESSAGE));

        assertThat(phoneNumberOf(seoyun)).isEqualTo("01044440001");
    }

    @Test
    @DisplayName("아무도 쓰지 않는 번호로 바꾸면 계정 정보에 새 번호가 보이고, 옛 번호로는 다른 사람이 가입할 수 있다")
    void phoneNumberChangeReleasesOldNumber() throws Exception {
        long seoyun = signUpAndVerify("phone-change@example.com", "010-4444-0003", false);

        mockMvc.perform(changePhoneNumber(seoyun, "010 4444 0004"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"phoneNumber\": \"01044440004\"}", JsonCompareMode.STRICT));

        mockMvc.perform(get("/api/audience/account").with(loggedInAs(AccountKind.AUDIENCE, seoyun)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("01044440004"));
        mockMvc.perform(signUp(signUpBody("phone-newcomer@example.com", "010-4444-0003", false)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("이용 정지 후 탈퇴로 차단된 번호로는 바꿀 수 없다 — 가입된 번호와 같은 문구다")
    void blockedPhoneNumberIsRejectedWithSameMessage() throws Exception {
        long seoyun = signUpAndVerify("phone-blocked@example.com", "010-4444-0005", false);
        jdbcTemplate.update("INSERT INTO audience_blocked_phone_number (phone_number, blocked_at) VALUES (?, now())",
                "01044440006");

        mockMvc.perform(changePhoneNumber(seoyun, "010-4444-0006"))
                .andExpect(status().isConflict())
                .andExpect(errorBody("PHONE_NUMBER_ALREADY_REGISTERED", ALREADY_REGISTERED_MESSAGE));
    }

    @Test
    @DisplayName("두 계정이 동시에 같은 번호로 바꾸면 하나만 바뀌고, 다른 하나는 409 번호 문구다")
    void concurrentChangesToSameNumberLeaveOne() throws Exception {
        long first = signUpAndVerify("phone-race1@example.com", "010-4444-0007", false);
        long second = signUpAndVerify("phone-race2@example.com", "010-4444-0008", false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (long accountId : List.of(first, second)) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(changePhoneNumber(accountId, "010-4444-0009")).andReturn();
                }));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            List<String> bodies = new ArrayList<>();
            for (Future<MvcResult> result : results) {
                MvcResult mvcResult = result.get(60, TimeUnit.SECONDS);
                statuses.add(mvcResult.getResponse().getStatus());
                bodies.add(mvcResult.getResponse().getContentAsString());
            }

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(bodies.get(statuses.indexOf(409))).contains("PHONE_NUMBER_ALREADY_REGISTERED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM audience_account WHERE phone_number = '01044440009'", Long.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("철회하면 동의 기록이 쌓이고, 처리 결과 메일이 '기록만' 어댑터에 남으며, 알림 설정에 동의하지 않은 상태로 보인다")
    void withdrawalRecordsConsentAndSendsResultMail(CapturedOutput output) throws Exception {
        long seoyun = signUpAndVerify("marketing-off@example.com", "010-4444-0010", true);
        MUTABLE_CLOCK.moveTo(WITHDRAWN_AT);

        mockMvc.perform(changeMarketing(seoyun, false))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"agreed\": false, \"agreedAt\": null}", JsonCompareMode.STRICT));

        assertThat(marketingConsentRecordsOf(seoyun)).containsExactly(
                "true:2026-02-20T10:00+09:00", "false:2026-04-10T21:00+09:00");
        assertThat(output.getOut())
                .contains("[기록만] 이메일 — 받는 곳: marketing-off@example.com, 제목: 마케팅 수신 동의 철회가 처리되었습니다,")
                .contains("2026-04-10에 마케팅 수신 동의를 철회하셨습니다.");
        assertThat(sendRecordsTo("marketing-off@example.com")).containsExactly(
                "SIGNUP_COMPLETED:EMAIL:true", "MARKETING_CONSENT_CHANGED:EMAIL:true");
        mockMvc.perform(get(SETTINGS_PATH).with(loggedInAs(AccountKind.AUDIENCE, seoyun)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketing.agreed").value(false))
                .andExpect(jsonPath("$.marketing.agreedAt").value(nullValue()));
    }

    @Test
    @DisplayName("동의하면 알림 설정에 \"2026-03-01\" 동의 일시가 보이고, 같은 동의를 다시 보내도 기록·안내가 늘지 않는다")
    void agreementShowsDateAndRepeatChangesNothing() throws Exception {
        long seoyun = signUpAndVerify("marketing-on@example.com", "010-4444-0011", false);
        MUTABLE_CLOCK.moveTo(AGREED_AT);

        mockMvc.perform(changeMarketing(seoyun, true))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"agreed\": true, \"agreedAt\": \"2026-03-01T09:30:00+09:00\"}",
                        JsonCompareMode.STRICT));
        MUTABLE_CLOCK.moveTo(WITHDRAWN_AT);
        mockMvc.perform(changeMarketing(seoyun, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedAt").value("2026-03-01T09:30:00+09:00"));

        mockMvc.perform(get(SETTINGS_PATH).with(loggedInAs(AccountKind.AUDIENCE, seoyun)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketing.agreed").value(true))
                .andExpect(jsonPath("$.marketing.agreedAt").value("2026-03-01T09:30:00+09:00"));
        assertThat(marketingConsentRecordsOf(seoyun)).containsExactly(
                "false:2026-02-20T10:00+09:00", "true:2026-03-01T09:30+09:00");
        assertThat(sendRecordsTo("marketing-on@example.com")).containsExactly(
                "SIGNUP_COMPLETED:EMAIL:true", "MARKETING_CONSENT_CHANGED:EMAIL:true");
    }

    @Test
    @DisplayName("처리 결과 안내는 알림함에도 쌓인다 — 이메일을 지워도 다시 볼 수 있다")
    void resultNoticeIsKeptInInbox() throws Exception {
        long seoyun = signUpAndVerify("marketing-inbox@example.com", "010-4444-0012", true);
        MUTABLE_CLOCK.moveTo(WITHDRAWN_AT);

        mockMvc.perform(changeMarketing(seoyun, false)).andExpect(status().isOk());

        mockMvc.perform(get("/api/audience/notification-inbox").with(loggedInAs(AccountKind.AUDIENCE, seoyun)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].title").value("마케팅 수신 동의 철회가 처리되었습니다"));
    }

    private RequestBuilder changePhoneNumber(long accountId, String phoneNumber) {
        return put(PHONE_NUMBER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("phoneNumber", phoneNumber)))
                .with(csrf())
                .with(loggedInAs(AccountKind.AUDIENCE, accountId));
    }

    private RequestBuilder changeMarketing(long accountId, boolean agreed) {
        return put(MARKETING_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("agreed", agreed)))
                .with(csrf())
                .with(loggedInAs(AccountKind.AUDIENCE, accountId));
    }

    /** 가입 API로 가입하고, DB에서 읽은 인증 링크 값으로 인증 API를 불러 활동 계정을 만든다. */
    private long signUpAndVerify(String email, String phoneNumber, boolean marketingAgreed) throws Exception {
        mockMvc.perform(signUp(signUpBody(email, phoneNumber, marketingAgreed))).andExpect(status().isCreated());
        String token = jdbcTemplate.queryForObject("""
                SELECT t.token FROM audience_email_verification_token t
                JOIN audience_account a ON a.id = t.audience_account_id WHERE a.email = ?""", String.class, email);
        ResultActions verification = mockMvc.perform(post("/api/auth/audience/email-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("token", token)))
                .with(csrf()));
        verification.andExpect(status().isNoContent());
        return jdbcTemplate.queryForObject("SELECT id FROM audience_account WHERE email = ?", Long.class, email);
    }

    private RequestBuilder signUp(Map<String, Object> body) {
        return post("/api/auth/audience/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body))
                .with(csrf());
    }

    private String phoneNumberOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT phone_number FROM audience_account WHERE id = ?", String.class, accountId);
    }

    /** 그 계정의 마케팅 수신 동의 기록 — "동의 여부:한국 시각", 남긴 순서대로. */
    private List<String> marketingConsentRecordsOf(long accountId) {
        return jdbcTemplate.queryForList("""
                SELECT agreed::text || ':' || to_char(recorded_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD"T"HH24:MI') || '+09:00'
                FROM audience_consent_record
                WHERE audience_account_id = ? AND item = 'MARKETING' ORDER BY recorded_at, id""", String.class, accountId);
    }

    /** 그 받는 곳으로 남은 발송 이력 — "종류:수단:성공 여부", 남긴 순서대로. */
    private List<String> sendRecordsTo(String recipientAddress) {
        return jdbcTemplate.queryForList("""
                SELECT kind || ':' || channel || ':' || succeeded FROM notification_send_record
                WHERE recipient_address = ? ORDER BY id""", String.class, recipientAddress);
    }

    private static Map<String, Object> signUpBody(String email, String phoneNumber, boolean marketingAgreed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "한서윤");
        body.put("birthDate", "1998-04-02");
        body.put("email", email);
        body.put("phoneNumber", phoneNumber);
        body.put("password", "seoyun1234");
        body.put("termsOfServiceAgreed", true);
        body.put("personalInformationCollectionAgreed", true);
        body.put("minimumAgeConfirmed", true);
        body.put("marketingAgreed", marketingAgreed);
        return body;
    }
}
