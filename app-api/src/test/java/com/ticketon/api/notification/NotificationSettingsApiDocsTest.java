package com.ticketon.api.notification;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceUseCase;
import com.ticketon.application.notification.port.in.GetNotificationSettingsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 알림 설정 API의 요청·응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = NotificationSettingsController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("알림 설정 API 명세")
class NotificationSettingsApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetNotificationSettingsUseCase getNotificationSettingsUseCase;

    @MockitoBean
    private ChangeNotificationPreferenceUseCase changeNotificationPreferenceUseCase;

    @Test
    @DisplayName("조회와 한 줄 바꾸기 모두 200에 NotificationSettingsResponse를 돌려준다")
    void operationsReturnSettingsResponse() throws Exception {
        ResultActions apiDocs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        apiDocs.andExpect(jsonPath("$.paths['/api/audience/notification-settings'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/NotificationSettingsResponse"))
                .andExpect(jsonPath("$.paths['/api/audience/notification-settings/{preference}'].put.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/NotificationSettingsResponse"));
    }

    @Test
    @DisplayName("응답 칸은 모두 필수이고, 묶음은 세 이름만 가진 enum이다")
    void responseShape() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.NotificationSettingsResponse.required",
                        containsInAnyOrder("preferences", "marketing")))
                .andExpect(jsonPath("$.components.schemas.NotificationPreferenceItem.required",
                        containsInAnyOrder("preference", "receives")))
                .andExpect(jsonPath("$.components.schemas.NotificationPreferenceItem.properties.preference.enum",
                        contains("BOOKING_CONFIRMATION", "DAY_BEFORE_REMINDER", "BOOKING_OPEN")));
    }

    @Test
    @DisplayName("마케팅 수신은 동의 여부가 필수이고, 동의 일시는 동의한 상태에만 있다")
    void marketingShape() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.NotificationSettingsResponse.properties.marketing['$ref']")
                        .value("#/components/schemas/MarketingConsentResponse"))
                .andExpect(jsonPath("$.components.schemas.MarketingConsentResponse.required", contains("agreed")))
                .andExpect(jsonPath("$.components.schemas.MarketingConsentResponse.properties.agreedAt.format")
                        .value("date-time"));
    }

    @Test
    @DisplayName("바꾸기 요청은 receives 하나가 필수이고, 주소의 묶음도 같은 세 이름만 받는다")
    void changeRequestShape() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ChangeNotificationPreferenceRequest.required",
                        contains("receives")))
                .andExpect(jsonPath("$.paths['/api/audience/notification-settings/{preference}'].put.parameters[0].schema.enum",
                        contains("BOOKING_CONFIRMATION", "DAY_BEFORE_REMINDER", "BOOKING_OPEN")));
    }
}
