package com.ticketon.api.account;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.notification.SendResult;
import java.time.Clock;
import java.time.Duration;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄워 관객 직접 가입 → 인증 메일 → 인증 → 회원가입 완료 알림까지 끝까지 돌린다.
 * Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 인증 메일은 '기록만' 어댑터가 로그로 남긴다. 그 로그에서 링크를 꺼내 인증한다 — 관객이 메일을 열어 링크를 누르는 것과 같다.
 * 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 번호·이메일을 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("관객 직접 가입과 이메일 인증 (진짜 PostgreSQL)")
class AudienceSignUpIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 10, 0);

    private static final Pattern VERIFICATION_LINK =
            Pattern.compile("http://localhost:3000/verify-email\\?token=([A-Za-z0-9_-]{32})");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    /**
     * 서비스 기준 시계. 테스트가 옮긴다. 빈 자리는 아래 {@code clock} 칸이 이 시계로 바꾼다.
     * <p>
     * 칸의 타입을 MutableClock이 아니라 Clock으로 둔다. 이름으로 바꾸는 {@code @TestBean}은 칸 타입과 같은 타입의 빈만 바꿀 대상으로 보는데,
     * clock 빈은 Clock 타입으로 등록돼 있어 MutableClock 칸이면 같은 이름의 빈을 하나 더 등록하려다 실패한다
     * (Spring Boot는 빈 정의 덮어쓰기를 막는다).
     */
    private static final MutableClock MUTABLE_CLOCK = MutableClock.at(NOW);

    @TestBean(name = "clock")
    private Clock clock;

    @MockitoSpyBean
    private EmailSendPort emailSendPort;

    static Clock clock() {
        return MUTABLE_CLOCK;
    }

    @BeforeEach
    void resetClock() {
        MUTABLE_CLOCK.moveTo(NOW);
    }

    @Test
    @DisplayName("가입 → 인증 메일이 '기록만' 어댑터 로그에 남음 → 메일의 링크로 인증 → 활동 상태")
    void signUpThenVerifyWithLinkInMail(CapturedOutput output) throws Exception {
        mockMvc.perform(signUp(body("seoyun@example.com", "010-1111-0001", "1998-04-02")))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"email\":\"seoyun@example.com\"}", JsonCompareMode.STRICT));
        assertThat(statusOf("seoyun@example.com")).isEqualTo("PENDING_VERIFICATION");

        String token = tokenInMailTo("seoyun@example.com", output);
        MUTABLE_CLOCK.advance(Duration.ofHours(1));
        mockMvc.perform(verify(token)).andExpect(status().isNoContent());

        assertThat(statusOf("seoyun@example.com")).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM audience_account WHERE email = ?", Boolean.class,
                "seoyun@example.com")).isTrue();
    }

    @Test
    @DisplayName("인증을 마치면 '회원가입 완료' 메일이 '기록만' 어댑터 로그에 남고, 발송 이력에 이메일 한 줄이 남는다")
    void verificationSendsSignUpCompletedMail(CapturedOutput output) throws Exception {
        mockMvc.perform(signUp(body("welcome@example.com", "010-1111-0014", "1998-04-02"))).andExpect(status().isCreated());
        String token = tokenInMailTo("welcome@example.com", output);
        assertThat(sendRecordsTo("welcome@example.com")).isEmpty();

        mockMvc.perform(verify(token)).andExpect(status().isNoContent());

        assertThat(output.getOut())
                .contains("[기록만] 이메일 — 받는 곳: welcome@example.com, 제목: 회원가입이 완료되었습니다,");
        assertThat(sendRecordsTo("welcome@example.com")).containsExactly("SIGNUP_COMPLETED:EMAIL:true");
    }

    @Test
    @DisplayName("비밀번호는 해시로만, 동의 기록은 네 항목 모두 남는다")
    void storesPasswordHashAndConsents() throws Exception {
        mockMvc.perform(signUp(body("hash@example.com", "010-1111-0002", "1998-04-02"))).andExpect(status().isCreated());

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM audience_account WHERE email = ?", String.class, "hash@example.com");
        assertThat(passwordHash).startsWith("{pbkdf2@SpringSecurity_v5_8}").doesNotContain("seoyun1234");
        List<String> agreedItems = jdbcTemplate.queryForList("""
                SELECT c.item || ':' || c.agreed FROM audience_consent_record c
                JOIN audience_account a ON a.id = c.audience_account_id
                WHERE a.email = ? ORDER BY c.id""", String.class, "hash@example.com");
        assertThat(agreedItems).containsExactly("TERMS_OF_SERVICE:true", "PERSONAL_INFORMATION_COLLECTION:true",
                "MINIMUM_AGE_CONFIRMATION:true", "MARKETING:false");
    }

    @Test
    @DisplayName("같은 번호로 두 번째 가입하면 409 — '이미 가입된 번호입니다' (docs/01)")
    void secondSignupWithSameNumberFails() throws Exception {
        mockMvc.perform(signUp(body("first@example.com", "010-1111-0003", "1998-04-02"))).andExpect(status().isCreated());

        expectPhoneNumberRejected(mockMvc.perform(signUp(body("second@example.com", "01011110003", "1990-01-01"))));
        assertThat(accountCountWithEmail("second@example.com")).isZero();
    }

    @Test
    @DisplayName("인증 대기 계정의 번호로도 가입할 수 없다 — 가입한 계정이다")
    void pendingAccountHoldsNumber() throws Exception {
        mockMvc.perform(signUp(body("pending@example.com", "010-1111-0004", "1998-04-02"))).andExpect(status().isCreated());
        assertThat(statusOf("pending@example.com")).isEqualTo("PENDING_VERIFICATION");

        expectPhoneNumberRejected(mockMvc.perform(signUp(body("other@example.com", "010-1111-0004", "1998-04-02"))));
    }

    @Test
    @DisplayName("이용 정지 후 탈퇴로 차단된 번호는 같은 문구로 막는다 — 정지 이력이 드러나지 않게")
    void blockedNumberFails() throws Exception {
        jdbcTemplate.update("INSERT INTO audience_blocked_phone_number (phone_number, blocked_at) VALUES (?, now())",
                "01011110005");

        expectPhoneNumberRejected(mockMvc.perform(signUp(body("blocked@example.com", "010-1111-0005", "1998-04-02"))));
        assertThat(accountCountWithEmail("blocked@example.com")).isZero();
    }

    @Test
    @DisplayName("만 14세 미만은 409 — 2012-10-16생은 2026-10-15에 만 13세")
    void underFourteenFails() throws Exception {
        mockMvc.perform(signUp(body("young@example.com", "010-1111-0006", "2012-10-16")))
                .andExpect(status().isConflict())
                .andExpect(errorBody("AUDIENCE_UNDER_MINIMUM_AGE", "만 14세 미만은 가입할 수 없습니다."));
        assertThat(accountCountWithEmail("young@example.com")).isZero();
    }

    @Test
    @DisplayName("2012-10-15생은 만 14세 생일인 2026-10-15에 가입된다")
    void fourteenthBirthdaySucceeds() throws Exception {
        mockMvc.perform(signUp(body("birthday@example.com", "010-1111-0007", "2012-10-15")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("대소문자만 다른 같은 이메일로는 가입할 수 없다")
    void sameEmailIgnoringCaseFails() throws Exception {
        mockMvc.perform(signUp(body("case@example.com", "010-1111-0008", "1998-04-02"))).andExpect(status().isCreated());

        mockMvc.perform(signUp(body("CASE@Example.com", "010-1111-0009", "1998-04-02")))
                .andExpect(status().isConflict())
                .andExpect(errorBody("AUDIENCE_EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다."));
    }

    @Test
    @DisplayName("같은 번호로 동시에 가입하면 하나만 가입되고, 다른 하나는 409 번호 문구다")
    void concurrentSignupsWithSameNumber() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (String email : List.of("race1@example.com", "race2@example.com")) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(signUp(body(email, "010-1111-0010", "1998-04-02"))).andReturn();
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

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(bodies.get(statuses.indexOf(409))).contains("PHONE_NUMBER_ALREADY_REGISTERED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM audience_account WHERE phone_number = '01011110010'", Long.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("인증 메일을 보내지 못하면 가입을 되돌린다 — 계정·링크가 남지 않아 다시 가입할 수 있다")
    void mailFailureRollsBackSignup() throws Exception {
        willReturn(SendResult.failure("메일 서버 응답 없음")).given(emailSendPort).send(any());

        mockMvc.perform(signUp(body("mailfail@example.com", "010-1111-0011", "1998-04-02")))
                .andExpect(status().isInternalServerError())
                .andExpect(errorBody("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));

        assertThat(accountCountWithEmail("mailfail@example.com")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audience_account WHERE phone_number = '01011110011'",
                Long.class)).isZero();
    }

    @Test
    @DisplayName("같은 링크를 다시 열면 409 '이미 이메일 인증을 마친 계정입니다'")
    void reopeningLinkSaysAlreadyVerified(CapturedOutput output) throws Exception {
        mockMvc.perform(signUp(body("twice@example.com", "010-1111-0012", "1998-04-02"))).andExpect(status().isCreated());
        String token = tokenInMailTo("twice@example.com", output);
        mockMvc.perform(verify(token)).andExpect(status().isNoContent());

        mockMvc.perform(verify(token))
                .andExpect(status().isConflict())
                .andExpect(errorBody("EMAIL_ALREADY_VERIFIED", "이미 이메일 인증을 마친 계정입니다."));
    }

    @Test
    @DisplayName("가입하고 24시간이 지나 링크를 열면 409 '유효 시간이 지났습니다'이고 인증 대기 그대로다")
    void expiredLinkFails(CapturedOutput output) throws Exception {
        mockMvc.perform(signUp(body("late@example.com", "010-1111-0013", "1998-04-02"))).andExpect(status().isCreated());
        String token = tokenInMailTo("late@example.com", output);
        MUTABLE_CLOCK.advance(Duration.ofHours(24));

        mockMvc.perform(verify(token))
                .andExpect(status().isConflict())
                .andExpect(errorBody("EMAIL_VERIFICATION_LINK_EXPIRED", "인증 링크의 유효 시간이 지났습니다."));
        assertThat(statusOf("late@example.com")).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    @DisplayName("발급한 적 없는 링크면 404")
    void unknownLinkFails() throws Exception {
        mockMvc.perform(verify("unknownAAAAAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("EMAIL_VERIFICATION_LINK_NOT_FOUND", "인증 링크가 올바르지 않습니다."));
    }

    private void expectPhoneNumberRejected(ResultActions response) throws Exception {
        response.andExpect(status().isConflict())
                .andExpect(errorBody("PHONE_NUMBER_ALREADY_REGISTERED", "이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요."));
    }

    /** '기록만' 어댑터가 남긴 그 주소의 메일 로그에서 인증 링크 값을 꺼낸다. */
    private static String tokenInMailTo(String email, CapturedOutput output) {
        String mailLog = output.getOut().lines()
                .filter(line -> line.contains("[기록만] 이메일 — 받는 곳: " + email + ","))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError(email + "로 보낸 인증 메일 로그가 없습니다"));
        assertThat(mailLog).contains("제목: 이메일 인증을 마쳐 주세요,");
        int mailStart = output.getOut().lastIndexOf(mailLog);
        Matcher link = VERIFICATION_LINK.matcher(output.getOut().substring(mailStart));
        assertThat(link.find()).as("메일 본문의 인증 링크").isTrue();
        return link.group(1);
    }

    /** 그 받는 곳으로 남은 발송 이력 — "종류:수단:성공 여부", 남긴 순서대로. */
    private List<String> sendRecordsTo(String recipientAddress) {
        return jdbcTemplate.queryForList("""
                SELECT kind || ':' || channel || ':' || succeeded FROM notification_send_record
                WHERE recipient_address = ? ORDER BY id""", String.class, recipientAddress);
    }

    private String statusOf(String email) {
        return jdbcTemplate.queryForObject("SELECT status FROM audience_account WHERE email = ?", String.class, email);
    }

    private long accountCountWithEmail(String email) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM audience_account WHERE email_lowercase = lower(?)",
                Long.class, email);
        return count == null ? 0 : count;
    }

    private RequestBuilder signUp(Map<String, Object> body) {
        return post("/api/auth/audience/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body))
                .with(csrf());
    }

    private RequestBuilder verify(String token) {
        return post("/api/auth/audience/email-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("token", token)))
                .with(csrf());
    }

    private static Map<String, Object> body(String email, String phoneNumber, String birthDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "한서윤");
        body.put("birthDate", birthDate);
        body.put("email", email);
        body.put("phoneNumber", phoneNumber);
        body.put("password", "seoyun1234");
        body.put("termsOfServiceAgreed", true);
        body.put("personalInformationCollectionAgreed", true);
        body.put("minimumAgeConfirmed", true);
        body.put("marketingAgreed", false);
        return body;
    }
}
