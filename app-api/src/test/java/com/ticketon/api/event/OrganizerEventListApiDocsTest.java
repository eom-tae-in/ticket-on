package com.ticketon.api.event;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.GetOrganizerEventListUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 내 행사 목록의 응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = OrganizerEventListController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("내 행사 목록 API 명세")
class OrganizerEventListApiDocsTest {

    private static final String EVENTS_PATH = "$.paths['/api/organizer/organizers/{organizerId}/events']";
    private static final String SCHEMAS = "$.components.schemas";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOrganizerEventListUseCase getOrganizerEventListUseCase;

    @Test
    @DisplayName("주최자 경로의 GET이 OrganizerEventListResponse를 돌려준다")
    void operation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(EVENTS_PATH + ".get.parameters[*].name", containsInAnyOrder("organizerId")))
                .andExpect(jsonPath(EVENTS_PATH + ".get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/OrganizerEventListResponse"));
    }

    @Test
    @DisplayName("응답의 칸 — 세 구역은 늘 있고, 작성 중 행사는 진행 단계 · 이어서 할 단계 · 마지막 수정 시각 · 접어둠을 늘 담는다")
    void responseFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerEventListResponse.required", containsInAnyOrder("drafts", "inProgress", "past")))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerDraftEventResponse.required",
                        containsInAnyOrder("eventId", "title", "wizardProgress", "resumeStep", "lastModifiedAt", "folded")))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerListedEventResponse.required",
                        containsInAnyOrder("eventId", "title", "status")))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerDraftEventResponse.properties.wizardProgress['$ref']")
                        .value("#/components/schemas/EventWizardProgressResponse"))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerDraftEventResponse.properties.resumeStep.enum", containsInAnyOrder(
                        "BASIC_INFORMATION", "SCHEDULE", "SEATING", "PRICING", "BOOKING_SETTINGS", "ENTRANCES_AND_CHECKIN_ACCOUNTS",
                        "REVIEW")))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerListedEventResponse.properties.status.enum", containsInAnyOrder("DRAFT",
                        "AWAITING_OPEN", "ON_SALE", "SALES_PAUSED", "SALES_CLOSED", "ENDED", "SETTLED", "CANCELLED")));
    }

    @Test
    @DisplayName("포스터 주소와 일정 날짜는 비어 있을 수 있다 — null을 받는다")
    void nullableFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerDraftEventResponse.properties.posterUrl.type",
                        containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerDraftEventResponse.properties.firstDate.type",
                        containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerDraftEventResponse.properties.lastDate.type",
                        containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerListedEventResponse.properties.posterUrl.type",
                        containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(SCHEMAS + ".OrganizerListedEventResponse.properties.firstDate.type",
                        containsInAnyOrder("string", "null")));
    }
}
