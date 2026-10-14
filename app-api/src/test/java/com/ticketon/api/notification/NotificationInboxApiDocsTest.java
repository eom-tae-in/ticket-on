package com.ticketon.api.notification;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.notification.port.in.OpenNotificationInboxUseCase;
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

/**
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 알림함 API의 응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = NotificationInboxController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("알림함 API 명세")
class NotificationInboxApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenNotificationInboxUseCase openNotificationInboxUseCase;

    @Test
    @DisplayName("알림함 열기는 200에 NotificationInboxResponse를 돌려준다")
    void operationReturnsInboxResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/audience/notification-inbox'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/NotificationInboxResponse"));
    }

    @Test
    @DisplayName("응답 칸은 모두 필수이고, 보낸 시각은 날짜·시각 형식이며, 안 읽은 개수 칸은 없다")
    void responseShape() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.NotificationInboxResponse.required", contains("notifications")))
                .andExpect(jsonPath("$.components.schemas.NotificationInboxResponse.properties.length()").value(1))
                .andExpect(jsonPath("$.components.schemas.NotificationInboxItem.required",
                        containsInAnyOrder("id", "title", "body", "sentAt", "isNew")))
                .andExpect(jsonPath("$.components.schemas.NotificationInboxItem.properties.sentAt.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.NotificationInboxItem.properties.isNew.type").value("boolean"));
    }
}
