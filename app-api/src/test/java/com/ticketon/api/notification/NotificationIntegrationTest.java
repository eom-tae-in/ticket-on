package com.ticketon.api.notification;

import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.notification.port.out.SmsSendPort;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.notification.SendResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄워, 알림 설정 API로 바꾼 설정이 알림 발송에 반영되고 발송 이력이 남는지,
 * 관객에게 보낸 알림이 알림함에 쌓여 알림함 API에서 처음 조회 때만 새로 온 것으로 보이는지 끝까지 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 관객은 가입·이메일 인증 API로 만들고(인증 링크 값은 DB에서 읽는다), 알림 설정 API는 그 계정으로 로그인한 요청으로 부른다. 알림은 다른
 * 도메인이 부르듯 발송 유스케이스를 바로 부른다. 이메일·문자는 '기록만' 어댑터가 받는다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른
 * 번호·이메일을 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("알림 발송·수신 설정·알림함 (진짜 PostgreSQL)")
class NotificationIntegrationTest {

    private static final String SETTINGS_PATH = "/api/audience/notification-settings";

    private static final String INBOX_PATH = "/api/audience/notification-inbox";

    private static final NotificationContent CONTENT = new NotificationContent("알림 제목", "알림 본문");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SendNotificationUseCase sendNotificationUseCase;

    @MockitoSpyBean
    private SmsSendPort smsSendPort;

    @Test
    @DisplayName("리마인더를 끄면 저장되고, 리마인더는 안 가며 회차 취소는 이메일·문자로 간다 — 이력에 그대로 남는다")
    void reminderOffSkipsReminderButSessionCancellationGoes() throws Exception {
        long accountId = signUpAndVerify("off@example.com", "010-3333-0001");

        mockMvc.perform(put(SETTINGS_PATH + "/DAY_BEFORE_REMINDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receives\": false}")
                        .with(csrf())
                        .with(loggedInAs(AccountKind.AUDIENCE, accountId)))
                .andExpect(status().isOk());
        mockMvc.perform(get(SETTINGS_PATH).with(loggedInAs(AccountKind.AUDIENCE, accountId)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"preferences": [
                          {"preference": "BOOKING_CONFIRMATION", "receives": true},
                          {"preference": "DAY_BEFORE_REMINDER", "receives": false},
                          {"preference": "BOOKING_OPEN", "receives": true}
                        ],
                        "marketing": {"agreed": false, "agreedAt": null}}""", JsonCompareMode.STRICT));

        send(accountId, NotificationKind.DAY_BEFORE_REMINDER);
        send(accountId, NotificationKind.SESSION_CANCELLED);

        assertThat(sendRecordsTo("off@example.com", "01033330001")).containsExactly(
                "SIGNUP_COMPLETED:EMAIL:off@example.com:true:",
                "SESSION_CANCELLED:EMAIL:off@example.com:true:",
                "SESSION_CANCELLED:SMS:01033330001:true:");
    }

    @Test
    @DisplayName("문자가 실패해도 이메일은 가고, 문자 줄에 실패 사유가 남는다")
    void smsFailureKeepsEmail() throws Exception {
        long accountId = signUpAndVerify("smsfail@example.com", "010-3333-0002");
        willReturn(SendResult.failure("없는 번호")).given(smsSendPort).send(any());

        send(accountId, NotificationKind.BOOKING_COMPLETED);

        assertThat(sendRecordsTo("smsfail@example.com", "01033330002")).containsExactly(
                "SIGNUP_COMPLETED:EMAIL:smsfail@example.com:true:",
                "BOOKING_COMPLETED:EMAIL:smsfail@example.com:true:",
                "BOOKING_COMPLETED:SMS:01033330002:false:없는 번호");
    }

    @Test
    @DisplayName("바꾼 적 없는 계정의 설정은 모두 받음이고, 표에는 줄이 없다")
    void defaultsWithoutRows() throws Exception {
        long accountId = signUpAndVerify("default@example.com", "010-3333-0003");

        mockMvc.perform(get(SETTINGS_PATH).with(loggedInAs(AccountKind.AUDIENCE, accountId)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"preferences": [
                          {"preference": "BOOKING_CONFIRMATION", "receives": true},
                          {"preference": "DAY_BEFORE_REMINDER", "receives": true},
                          {"preference": "BOOKING_OPEN", "receives": true}
                        ],
                        "marketing": {"agreed": false, "agreedAt": null}}""", JsonCompareMode.STRICT));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audience_notification_preference WHERE audience_account_id = ?", Long.class,
                accountId)).isZero();
    }

    @Test
    @DisplayName("관객에게 보낸 알림이 알림함에 최신순으로 쌓이고, 처음 조회 때만 새로 온 것이다 — 문자가 실패한 알림도 남는다")
    void inboxShowsNewOnlyOnFirstOpen() throws Exception {
        long accountId = signUpAndVerify("inbox@example.com", "010-3333-0004");
        send(accountId, NotificationKind.BOOKING_COMPLETED, new NotificationContent("예매가 완료되었습니다", "티켓 링크"));

        openInbox(accountId)
                .andExpect(jsonPath("$.notifications[*].title", contains("예매가 완료되었습니다", "회원가입이 완료되었습니다")))
                .andExpect(jsonPath("$.notifications[*].isNew", contains(true, true)))
                .andExpect(jsonPath("$.notifications[0].body").value("티켓 링크"));
        openInbox(accountId)
                .andExpect(jsonPath("$.notifications[*].isNew", contains(false, false)));

        willReturn(SendResult.failure("없는 번호")).given(smsSendPort).send(any());
        send(accountId, NotificationKind.SESSION_CANCELLED, new NotificationContent("회차가 취소되었습니다", "다른 회차 예매하기"));

        openInbox(accountId)
                .andExpect(jsonPath("$.notifications[*].title",
                        contains("회차가 취소되었습니다", "예매가 완료되었습니다", "회원가입이 완료되었습니다")))
                .andExpect(jsonPath("$.notifications[*].isNew", contains(true, false, false)));
        assertThat(sendRecordsTo("inbox@example.com", "01033330004")).contains(
                "SESSION_CANCELLED:SMS:01033330004:false:없는 번호");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audience_notification_inbox
                WHERE audience_account_id = ? AND first_seen_at IS NULL""", Long.class, accountId)).isZero();
    }

    private ResultActions openInbox(long accountId) throws Exception {
        return mockMvc.perform(get(INBOX_PATH).with(loggedInAs(AccountKind.AUDIENCE, accountId)))
                .andExpect(status().isOk());
    }

    private void send(long accountId, NotificationKind kind) {
        send(accountId, kind, CONTENT);
    }

    private void send(long accountId, NotificationKind kind, NotificationContent content) {
        sendNotificationUseCase.send(new SendNotificationCommand(NotificationRecipient.audience(accountId), kind, content));
    }

    /** 가입 API로 가입하고, DB에서 읽은 인증 링크 값으로 인증 API를 불러 활동 계정을 만든다. */
    private long signUpAndVerify(String email, String phoneNumber) throws Exception {
        mockMvc.perform(post("/api/auth/audience/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signUpBody(email, phoneNumber)))
                        .with(csrf()))
                .andExpect(status().isCreated());
        String token = jdbcTemplate.queryForObject("""
                SELECT t.token FROM audience_email_verification_token t
                JOIN audience_account a ON a.id = t.audience_account_id WHERE a.email = ?""", String.class, email);
        mockMvc.perform(post("/api/auth/audience/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("token", token)))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        return jdbcTemplate.queryForObject("SELECT id FROM audience_account WHERE email = ?", Long.class, email);
    }

    /** 그 이메일·번호로 남은 발송 이력 — "종류:수단:받는 곳:성공 여부:실패 사유", 남긴 순서대로. */
    private List<String> sendRecordsTo(String email, String phoneDigits) {
        return jdbcTemplate.queryForList("""
                SELECT kind || ':' || channel || ':' || recipient_address || ':' || succeeded || ':'
                       || coalesce(failure_reason, '')
                FROM notification_send_record WHERE recipient_address IN (?, ?) ORDER BY id""",
                String.class, email, phoneDigits);
    }

    private static Map<String, Object> signUpBody(String email, String phoneNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "한서윤");
        body.put("birthDate", "1998-04-02");
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
