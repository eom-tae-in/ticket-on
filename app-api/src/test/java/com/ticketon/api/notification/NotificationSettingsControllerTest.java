package com.ticketon.api.notification;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.ChangeMarketingConsentCommand;
import com.ticketon.application.account.port.in.ChangeMarketingConsentUseCase;
import com.ticketon.application.account.port.in.MarketingConsentResult;
import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceCommand;
import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceUseCase;
import com.ticketon.application.notification.port.in.GetNotificationSettingsQuery;
import com.ticketon.application.notification.port.in.GetNotificationSettingsUseCase;
import com.ticketon.application.notification.port.in.NotificationSettingsResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.notification.NotificationPreference;
import com.ticketon.domain.notification.NotificationSettings;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 알림 설정 조회·변경 API — 보안 필터를 건 채로 관객 영역인지, 요청이 Query/Command로 바뀌는지, 입력 검증(400)을 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = NotificationSettingsController.class)
@Import(SecurityConfiguration.class)
@DisplayName("알림 설정 조회·변경 API")
class NotificationSettingsControllerTest {

    private static final String SETTINGS_PATH = "/api/audience/notification-settings";

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, 7);

    private static final MarketingConsentResult NOT_AGREED = new MarketingConsentResult(false, null);

    private static final NotificationSettingsResult DEFAULTS =
            NotificationSettingsResult.from(NotificationSettings.defaultsFor(SEOYUN.id()), NOT_AGREED);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetNotificationSettingsUseCase getNotificationSettingsUseCase;

    @MockitoBean
    private ChangeNotificationPreferenceUseCase changeNotificationPreferenceUseCase;

    @MockitoBean
    private ChangeMarketingConsentUseCase changeMarketingConsentUseCase;

    @Nested
    @DisplayName("조회")
    class GetSettings {

        @Test
        @DisplayName("로그인한 관객의 설정을 세 묶음, 화면 순서대로 돌려주고 마케팅 수신은 따로 담는다")
        void returnsSettingsOfLoggedInAudience() throws Exception {
            given(getNotificationSettingsUseCase.getSettings(any())).willReturn(DEFAULTS);

            mockMvc.perform(get(SETTINGS_PATH).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"preferences": [
                              {"preference": "BOOKING_CONFIRMATION", "receives": true},
                              {"preference": "DAY_BEFORE_REMINDER", "receives": true},
                              {"preference": "BOOKING_OPEN", "receives": true}
                            ],
                            "marketing": {"agreed": false, "agreedAt": null}}""", JsonCompareMode.STRICT));

            ArgumentCaptor<GetNotificationSettingsQuery> query = ArgumentCaptor.forClass(GetNotificationSettingsQuery.class);
            verify(getNotificationSettingsUseCase).getSettings(query.capture());
            assertThat(query.getValue().account()).isEqualTo(SEOYUN);
        }

        @Test
        @DisplayName("마케팅 수신에 동의한 상태면 동의 일시를 한국 시각으로 돌려준다 — \"2026-03-01 동의\" (docs/12 예시)")
        void returnsMarketingAgreedAtInKoreanTime() throws Exception {
            Instant agreedAt = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 1, 9, 30));
            given(getNotificationSettingsUseCase.getSettings(any())).willReturn(NotificationSettingsResult.from(
                    NotificationSettings.defaultsFor(SEOYUN.id()), new MarketingConsentResult(true, agreedAt)));

            mockMvc.perform(get(SETTINGS_PATH).with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.marketing.agreed").value(true))
                    .andExpect(jsonPath("$.marketing.agreedAt").value("2026-03-01T09:30:00+09:00"));
        }

        @Test
        @DisplayName("로그인하지 않았으면 401이다")
        void requiresLogin() throws Exception {
            mockMvc.perform(get(SETTINGS_PATH))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"STAFF", "ADMIN", "CHECKIN"})
        @DisplayName("관객이 아닌 계정으로는 403이다 — 알림 설정은 관객 계정 단위다")
        void otherAccountKindIsDenied(AccountKind kind) throws Exception {
            mockMvc.perform(get(SETTINGS_PATH).with(loggedInAs(kind, 7)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

            verify(getNotificationSettingsUseCase, never()).getSettings(any());
        }
    }

    @Nested
    @DisplayName("한 줄 바꾸기")
    class ChangePreference {

        @Test
        @DisplayName("주소의 묶음과 본문의 받을지를 Command로 넘기고, 바뀐 뒤의 설정 전체를 돌려준다")
        void convertsRequestToCommand() throws Exception {
            given(changeNotificationPreferenceUseCase.changePreference(any())).willReturn(NotificationSettingsResult.from(
                    NotificationSettings.defaultsFor(SEOYUN.id()).change(NotificationPreference.DAY_BEFORE_REMINDER, false),
                    NOT_AGREED));

            mockMvc.perform(change("DAY_BEFORE_REMINDER", "{\"receives\": false}").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preferences", hasSize(3)))
                    .andExpect(jsonPath("$.preferences[1].preference").value("DAY_BEFORE_REMINDER"))
                    .andExpect(jsonPath("$.preferences[1].receives").value(false))
                    .andExpect(jsonPath("$.marketing.agreed").value(false));

            ArgumentCaptor<ChangeNotificationPreferenceCommand> command =
                    ArgumentCaptor.forClass(ChangeNotificationPreferenceCommand.class);
            verify(changeNotificationPreferenceUseCase).changePreference(command.capture());
            assertThat(command.getValue()).isEqualTo(
                    new ChangeNotificationPreferenceCommand(SEOYUN, NotificationPreference.DAY_BEFORE_REMINDER, false));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(NotificationPreferenceCode.class)
        @DisplayName("세 묶음 모두 같은 이름의 도메인 묶음으로 바뀐다")
        void everyCodeMapsToSameNamedPreference(NotificationPreferenceCode code) throws Exception {
            given(changeNotificationPreferenceUseCase.changePreference(any())).willReturn(DEFAULTS);

            mockMvc.perform(change(code.name(), "{\"receives\": true}").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk());

            ArgumentCaptor<ChangeNotificationPreferenceCommand> command =
                    ArgumentCaptor.forClass(ChangeNotificationPreferenceCommand.class);
            verify(changeNotificationPreferenceUseCase).changePreference(command.capture());
            assertThat(command.getValue().preference().name()).isEqualTo(code.name());
        }

        @Test
        @DisplayName("모르는 묶음 이름(끌 수 없는 회차 취소 등)이면 400이고 유스케이스를 부르지 않는다")
        void unknownPreferenceIsBadRequest() throws Exception {
            mockMvc.perform(change("SESSION_CANCELLED", "{\"receives\": false}").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("preference"));

            verify(changeNotificationPreferenceUseCase, never()).changePreference(any());
        }

        @Test
        @DisplayName("받을지가 빠지면 400 — '안 받음'으로 읽지 않는다")
        void missingReceivesIsBadRequest() throws Exception {
            mockMvc.perform(change("BOOKING_OPEN", "{}").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("receives"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("받을지 골라 주세요."));

            verify(changeNotificationPreferenceUseCase, never()).changePreference(any());
        }

        @Test
        @DisplayName("CSRF 토큰이 없으면 403이다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(put(SETTINGS_PATH + "/BOOKING_OPEN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"receives\": false}")
                            .with(loggedInAs(SEOYUN)))
                    .andExpect(status().isForbidden());

            verify(changeNotificationPreferenceUseCase, never()).changePreference(any());
        }

        @Test
        @DisplayName("관객이 아닌 계정으로는 403이다")
        void staffIsDenied() throws Exception {
            mockMvc.perform(change("BOOKING_OPEN", "{\"receives\": false}").with(loggedInAs(AccountKind.STAFF, 7)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
        }

        private MockHttpServletRequestBuilder change(String preference, String body) {
            return put(SETTINGS_PATH + "/" + preference)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf());
        }
    }

    @Nested
    @DisplayName("마케팅 수신 동의·철회")
    class ChangeMarketingConsent {

        private static final String MARKETING_PATH = SETTINGS_PATH + "/marketing";

        @Test
        @DisplayName("본문의 동의 여부를 Command로 넘기고, 바뀐 뒤의 상태를 돌려준다 — 철회하면 동의 일시가 없다")
        void convertsRequestToCommand() throws Exception {
            given(changeMarketingConsentUseCase.changeConsent(any())).willReturn(NOT_AGREED);

            mockMvc.perform(changeMarketing("{\"agreed\": false}").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"agreed\": false, \"agreedAt\": null}", JsonCompareMode.STRICT));

            ArgumentCaptor<ChangeMarketingConsentCommand> command = ArgumentCaptor.forClass(ChangeMarketingConsentCommand.class);
            verify(changeMarketingConsentUseCase).changeConsent(command.capture());
            assertThat(command.getValue()).isEqualTo(new ChangeMarketingConsentCommand(SEOYUN, false));
        }

        @Test
        @DisplayName("동의하면 동의 일시를 한국 시각으로 돌려준다")
        void agreementReturnsAgreedAt() throws Exception {
            Instant agreedAt = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 1, 9, 30));
            given(changeMarketingConsentUseCase.changeConsent(any())).willReturn(new MarketingConsentResult(true, agreedAt));

            mockMvc.perform(changeMarketing("{\"agreed\": true}").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"agreed\": true, \"agreedAt\": \"2026-03-01T09:30:00+09:00\"}",
                            JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("동의 여부가 빠지면 400 — 어느 쪽으로도 읽지 않는다")
        void missingAgreedIsBadRequest() throws Exception {
            mockMvc.perform(changeMarketing("{}").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("agreed"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("마케팅 수신 동의 여부를 골라 주세요."));

            verify(changeMarketingConsentUseCase, never()).changeConsent(any());
        }

        @Test
        @DisplayName("'marketing'은 끌 수 있는 묶음 이름이 아니라 이 주소로 간다 — 알림 묶음 바꾸기를 부르지 않는다")
        void doesNotGoToPreferenceChange() throws Exception {
            given(changeMarketingConsentUseCase.changeConsent(any())).willReturn(NOT_AGREED);

            mockMvc.perform(changeMarketing("{\"agreed\": false}").with(loggedInAs(SEOYUN)))
                    .andExpect(status().isOk());

            verify(changeNotificationPreferenceUseCase, never()).changePreference(any());
        }

        @Test
        @DisplayName("CSRF 토큰이 없으면 403이다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(put(MARKETING_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agreed\": false}")
                            .with(loggedInAs(SEOYUN)))
                    .andExpect(status().isForbidden());

            verify(changeMarketingConsentUseCase, never()).changeConsent(any());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"STAFF", "ADMIN", "CHECKIN"})
        @DisplayName("관객이 아닌 계정으로는 403이다 — 마케팅 수신 동의는 관객 가입 때 받는다")
        void otherAccountKindIsDenied(AccountKind kind) throws Exception {
            mockMvc.perform(changeMarketing("{\"agreed\": false}").with(loggedInAs(kind, 7)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

            verify(changeMarketingConsentUseCase, never()).changeConsent(any());
        }

        private MockHttpServletRequestBuilder changeMarketing(String body) {
            return put(MARKETING_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf());
        }
    }
}
