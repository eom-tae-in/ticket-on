package com.ticketon.api.notification;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.notification.port.in.NotificationInboxResult;
import com.ticketon.application.notification.port.in.OpenNotificationInboxCommand;
import com.ticketon.application.notification.port.in.OpenNotificationInboxUseCase;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 알림함 API — 보안 필터를 건 채로 관객 영역인지, 로그인한 관객이 Command로 넘어가는지, 결과가 응답 모양으로 바뀌는지 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = NotificationInboxController.class)
@Import(SecurityConfiguration.class)
@DisplayName("알림함 API")
class NotificationInboxControllerTest {

    private static final String INBOX_PATH = "/api/audience/notification-inbox";

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, 7);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenNotificationInboxUseCase openNotificationInboxUseCase;

    @Test
    @DisplayName("로그인한 관객의 알림함을 열고, 최신순 목록을 한국 시각의 보낸 시각과 새로 온 것 표시로 돌려준다")
    void opensInboxOfLoggedInAudience() throws Exception {
        given(openNotificationInboxUseCase.open(any())).willReturn(new NotificationInboxResult(List.of(
                new NotificationInboxResult.Item(2, "회차가 취소되었습니다", "다른 회차 예매하기",
                        KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 9, 0)), true),
                new NotificationInboxResult.Item(1, "예매가 완료되었습니다", "티켓 링크",
                        KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 12, 0)), false))));

        mockMvc.perform(get(INBOX_PATH).with(loggedInAs(SEOYUN)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"notifications": [
                          {"id": 2, "title": "회차가 취소되었습니다", "body": "다른 회차 예매하기",
                           "sentAt": "2026-10-16T09:00:00+09:00", "isNew": true},
                          {"id": 1, "title": "예매가 완료되었습니다", "body": "티켓 링크",
                           "sentAt": "2026-10-15T12:00:00+09:00", "isNew": false}
                        ]}""", JsonCompareMode.STRICT));

        ArgumentCaptor<OpenNotificationInboxCommand> command = ArgumentCaptor.forClass(OpenNotificationInboxCommand.class);
        verify(openNotificationInboxUseCase).open(command.capture());
        assertThat(command.getValue().account()).isEqualTo(SEOYUN);
    }

    @Test
    @DisplayName("쌓인 알림이 없으면 빈 목록이다")
    void emptyInbox() throws Exception {
        given(openNotificationInboxUseCase.open(any())).willReturn(new NotificationInboxResult(List.of()));

        mockMvc.perform(get(INBOX_PATH).with(loggedInAs(SEOYUN)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"notifications\": []}", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get(INBOX_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"STAFF", "ADMIN", "CHECKIN"})
    @DisplayName("관객이 아닌 계정으로는 403이다 — 알림함은 관객 계정에만 있다")
    void otherAccountKindIsDenied(AccountKind kind) throws Exception {
        mockMvc.perform(get(INBOX_PATH).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verify(openNotificationInboxUseCase, never()).open(any());
    }
}
