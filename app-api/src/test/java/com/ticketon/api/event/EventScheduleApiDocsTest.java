package com.ticketon.api.event;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.AddAllDayDateUseCase;
import com.ticketon.application.event.port.in.AddTimedSessionUseCase;
import com.ticketon.application.event.port.in.ChangeDateOperatingHoursUseCase;
import com.ticketon.application.event.port.in.ChangeSessionDateTimeUseCase;
import com.ticketon.application.event.port.in.ChangeSessionPerformersUseCase;
import com.ticketon.application.event.port.in.ChangeSessionPriceAdjustmentUseCase;
import com.ticketon.application.event.port.in.ChooseSessionModeUseCase;
import com.ticketon.application.event.port.in.GetEventScheduleUseCase;
import com.ticketon.application.event.port.in.RemoveScheduleDateUseCase;
import com.ticketon.application.event.port.in.RemoveSessionUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 일정 API의 요청 · 응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = EventScheduleController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("일정 API 명세")
class EventScheduleApiDocsTest {

    private static final String SCHEDULE = "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/schedule";
    private static final String SCHEMAS = "$.components.schemas";
    private static final String SCHEDULE_RESPONSE = "#/components/schemas/EventScheduleResponse";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetEventScheduleUseCase getEventScheduleUseCase;

    @MockitoBean
    private ChooseSessionModeUseCase chooseSessionModeUseCase;

    @MockitoBean
    private AddTimedSessionUseCase addTimedSessionUseCase;

    @MockitoBean
    private AddAllDayDateUseCase addAllDayDateUseCase;

    @MockitoBean
    private ChangeSessionDateTimeUseCase changeSessionDateTimeUseCase;

    @MockitoBean
    private ChangeSessionPerformersUseCase changeSessionPerformersUseCase;

    @MockitoBean
    private ChangeSessionPriceAdjustmentUseCase changeSessionPriceAdjustmentUseCase;

    @MockitoBean
    private ChangeDateOperatingHoursUseCase changeDateOperatingHoursUseCase;

    @MockitoBean
    private RemoveSessionUseCase removeSessionUseCase;

    @MockitoBean
    private RemoveScheduleDateUseCase removeScheduleDateUseCase;

    private static String path(String suffix) {
        return SCHEDULE + suffix + "']";
    }

    @Test
    @DisplayName("조회 200 · 회차 방식 200 · 회차 · 날짜 추가 201 · 일시 · 출연 · 가격 조정 · 운영 시간 200은 일정 전체를, 삭제는 204를 돌려준다")
    void operations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path("") + ".get.parameters[*].name", containsInAnyOrder("organizerId", "eventId")))
                .andExpect(jsonPath(path("") + ".get.responses['200'].content['application/json'].schema['$ref']").value(SCHEDULE_RESPONSE))
                .andExpect(jsonPath(path("/session-mode") + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ChooseSessionModeRequest"))
                .andExpect(jsonPath(path("/session-mode") + ".put.responses['200'].content['application/json'].schema['$ref']")
                        .value(SCHEDULE_RESPONSE))
                .andExpect(jsonPath(path("/sessions") + ".post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AddTimedSessionRequest"))
                .andExpect(jsonPath(path("/sessions") + ".post.responses['201'].content['application/json'].schema['$ref']")
                        .value(SCHEDULE_RESPONSE))
                .andExpect(jsonPath(path("/dates") + ".post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AddAllDayDateRequest"))
                .andExpect(jsonPath(path("/dates") + ".post.responses['201'].content['application/json'].schema['$ref']")
                        .value(SCHEDULE_RESPONSE))
                .andExpect(jsonPath(path("/sessions/{sessionId}/date-time") + ".put.parameters[*].name",
                        containsInAnyOrder("organizerId", "eventId", "sessionId")))
                .andExpect(jsonPath(path("/sessions/{sessionId}/date-time") + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ChangeSessionDateTimeRequest"))
                .andExpect(jsonPath(path("/sessions/{sessionId}/performers") + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/SessionPerformersRequest"))
                .andExpect(jsonPath(path("/sessions/{sessionId}/price-adjustment")
                        + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/SessionPriceAdjustmentRequest"))
                .andExpect(jsonPath(path("/sessions/{sessionId}/price-adjustment")
                        + ".put.responses['200'].content['application/json'].schema['$ref']").value(SCHEDULE_RESPONSE))
                .andExpect(jsonPath(path("/sessions/{sessionId}") + ".delete.responses['204']").exists())
                .andExpect(jsonPath(path("/dates/{date}/operating-hours") + ".put.parameters[*].name",
                        containsInAnyOrder("organizerId", "eventId", "date")))
                .andExpect(jsonPath(path("/dates/{date}/operating-hours") + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/OperatingHoursRequest"))
                .andExpect(jsonPath(path("/dates/{date}/operating-hours") + ".delete.responses['204']").exists())
                .andExpect(jsonPath(path("/dates/{date}") + ".delete.responses['204']").exists());
    }

    @Test
    @DisplayName("요청의 필수 칸과 고르는 값")
    void requestFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".ChooseSessionModeRequest.required", containsInAnyOrder("sessionMode")))
                .andExpect(jsonPath(SCHEMAS + ".ChooseSessionModeRequest.properties.sessionMode.enum", containsInAnyOrder("TIMED", "ALL_DAY")))
                .andExpect(jsonPath(SCHEMAS + ".OperatingHoursRequest.required", containsInAnyOrder("openTime", "closeTime")))
                .andExpect(jsonPath(SCHEMAS + ".AddTimedSessionRequest.required", containsInAnyOrder("date", "startTime")))
                .andExpect(jsonPath(SCHEMAS + ".AddAllDayDateRequest.required", containsInAnyOrder("date")))
                .andExpect(jsonPath(SCHEMAS + ".ChangeSessionDateTimeRequest.required", containsInAnyOrder("date")))
                .andExpect(jsonPath(SCHEMAS + ".SessionPriceAdjustmentRequest.required", containsInAnyOrder("kind")))
                .andExpect(jsonPath(SCHEMAS + ".SessionPriceAdjustmentRequest.properties.kind.enum",
                        containsInAnyOrder("NONE", "AMOUNT", "PERCENT")))
                .andExpect(jsonPath(SCHEMAS + ".AddTimedSessionRequest.properties.startTime.type").value("string"));
    }

    @Test
    @DisplayName("응답의 칸 — 회차 방식 · 운영 시간 · 회차형 시각 · 출연 · 조정 값은 null일 수 있다")
    void responseFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".EventScheduleResponse.required", containsInAnyOrder("eventId", "dates")))
                .andExpect(jsonPath(SCHEMAS + ".ScheduleDateResponse.required", containsInAnyOrder("date", "sessions")))
                .andExpect(jsonPath(SCHEMAS + ".ScheduleSessionResponse.required",
                        containsInAnyOrder("sessionId", "priceAdjustment", "cancelled")))
                .andExpect(jsonPath(SCHEMAS + ".ScheduleSessionResponse.properties.startTime.type", containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(SCHEMAS + ".ScheduleSessionResponse.properties.performers.type", containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(SCHEMAS + ".OperatingHoursResponse.required", containsInAnyOrder("openTime", "closeTime")))
                .andExpect(jsonPath(SCHEMAS + ".SessionPriceAdjustmentResponse.required", containsInAnyOrder("kind")))
                .andExpect(jsonPath(SCHEMAS + ".SessionPriceAdjustmentResponse.properties.amount.type",
                        containsInAnyOrder("integer", "null")));
    }
}
