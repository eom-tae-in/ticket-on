package com.ticketon.api.event;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.GetEventCategoriesUseCase;
import com.ticketon.application.event.port.in.GetEventPosterFileUseCase;
import com.ticketon.application.event.port.in.GetEventWizardUseCase;
import com.ticketon.application.event.port.in.SaveEventBasicInformationUseCase;
import com.ticketon.application.event.port.in.SaveEventBookingSettingsUseCase;
import com.ticketon.application.event.port.in.UploadEventPosterUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 행사 만들기 위저드의 요청 · 응답 모양과 포스터 올리기 · 포스터 파일이 명세에 그대로
 * 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {OrganizerEventController.class, EventCategoryController.class, EventPosterFileController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("행사 만들기 위저드 API 명세")
class OrganizerEventApiDocsTest {

    private static final String EVENTS_PATH = "$.paths['/api/organizer/organizers/{organizerId}/events']";
    private static final String EVENT_PATH = "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}']";
    private static final String BASIC_INFORMATION_PATH =
            "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/basic-information']";
    private static final String BOOKING_SETTINGS_PATH =
            "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/booking-settings']";
    private static final String POSTER_PATH = "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/poster']";
    private static final String POSTER_FILE_PATH = "$.paths['/api/public/files/event-posters/{fileKey}']";
    private static final String SCHEMAS = "$.components.schemas";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateEventUseCase createEventUseCase;

    @MockitoBean
    private GetEventWizardUseCase getEventWizardUseCase;

    @MockitoBean
    private SaveEventBasicInformationUseCase saveEventBasicInformationUseCase;

    @MockitoBean
    private SaveEventBookingSettingsUseCase saveEventBookingSettingsUseCase;

    @MockitoBean
    private UploadEventPosterUseCase uploadEventPosterUseCase;

    @MockitoBean
    private GetEventPosterFileUseCase getEventPosterFileUseCase;

    @MockitoBean
    private GetEventCategoriesUseCase getEventCategoriesUseCase;

    @Test
    @DisplayName("행사 만들기는 1단계 요청을 받아 201에, 조회 · 1단계 저장 · 5단계 저장은 200에 EventWizardResponse를 돌려준다")
    void operations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(EVENTS_PATH + ".post.parameters[*].name", containsInAnyOrder("organizerId")))
                .andExpect(jsonPath(EVENTS_PATH + ".post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventBasicInformationRequest"))
                .andExpect(jsonPath(EVENTS_PATH + ".post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventWizardResponse"))
                .andExpect(jsonPath(EVENT_PATH + ".get.parameters[*].name", containsInAnyOrder("organizerId", "eventId")))
                .andExpect(jsonPath(EVENT_PATH + ".get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventWizardResponse"))
                .andExpect(jsonPath(BASIC_INFORMATION_PATH + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventBasicInformationRequest"))
                .andExpect(jsonPath(BASIC_INFORMATION_PATH + ".put.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventWizardResponse"))
                .andExpect(jsonPath(BOOKING_SETTINGS_PATH + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventBookingSettingsRequest"))
                .andExpect(jsonPath(BOOKING_SETTINGS_PATH + ".put.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventWizardResponse"))
                .andExpect(jsonPath("$.paths['/api/organizer/event-categories'].get.responses['200'].content['application/json']"
                        + ".schema['$ref']").value("#/components/schemas/EventCategoriesResponse"));
    }

    @Test
    @DisplayName("요청의 필수 칸 — 1단계는 제목 · 유형 · 카테고리 · 소개 · 장소, 5단계는 비운 것이 처음 값인 칸(오픈 · 1인 총 매수 · 연령 · 암호) 밖의 칸")
    void requiredRequestFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".EventBasicInformationRequest.required",
                        containsInAnyOrder("title", "type", "categoryName", "introduction", "venueName", "venueAddress")))
                .andExpect(jsonPath(SCHEMAS + ".EventBookingSettingsRequest.required", containsInAnyOrder("visibility",
                        "bookingCloseLeadMinutes", "checkinOpenLeadMinutes", "maxSeatsPerSelection", "sessionChoice",
                        "reentryAllowed", "cancellationFeeTiers")))
                .andExpect(jsonPath(SCHEMAS + ".TicketsPerPersonLimitRequest.required", containsInAnyOrder("maxTickets", "basis")))
                .andExpect(jsonPath(SCHEMAS + ".CancellationFeeTierRequest.required",
                        containsInAnyOrder("fromDaysBefore", "deductionRatePercent")));
    }

    @Test
    @DisplayName("응답의 칸 — 위저드 값은 상태 · 진행 단계 · 마지막 수정 시각 · 공개 주소 · 1단계 · 5단계를 늘 담는다")
    void responseFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".EventWizardResponse.required", containsInAnyOrder("eventId", "status",
                        "wizardProgress", "lastModifiedAt", "publicAddress", "basicInformation", "bookingSettings")))
                .andExpect(jsonPath(SCHEMAS + ".EventWizardProgressResponse.required",
                        containsInAnyOrder("step", "stepNumber", "stepCount")))
                .andExpect(jsonPath(SCHEMAS + ".EventBasicInformationResponse.required", containsInAnyOrder("title", "type",
                        "categoryName", "introduction", "venueName", "venueAddress")))
                .andExpect(jsonPath(SCHEMAS + ".EventBookingSettingsResponse.required", containsInAnyOrder("visibility",
                        "passwordSet", "bookingCloseLeadMinutes", "checkinOpenLeadMinutes", "maxSeatsPerSelection",
                        "sessionChoice", "reentryAllowed", "cancellationFeeTiers", "cancellationPolicyStandard")))
                .andExpect(jsonPath(SCHEMAS + ".EventCategoriesResponse.required", containsInAnyOrder("types", "otherCategoryName")))
                .andExpect(jsonPath(SCHEMAS + ".EventWizardProgressResponse.properties.step.enum", containsInAnyOrder(
                        "BASIC_INFORMATION", "SCHEDULE", "SEATING", "PRICING", "BOOKING_SETTINGS", "ENTRANCES_AND_CHECKIN_ACCOUNTS",
                        "REVIEW")))
                .andExpect(jsonPath(SCHEMAS + ".EventWizardResponse.properties.status.enum", containsInAnyOrder("DRAFT",
                        "AWAITING_OPEN", "ON_SALE", "SALES_PAUSED", "SALES_CLOSED", "ENDED", "SETTLED", "CANCELLED")))
                .andExpect(jsonPath(SCHEMAS + ".EventBookingSettingsResponse.properties.visibility.enum",
                        containsInAnyOrder("PUBLIC", "LINK_ONLY", "PASSWORD_PROTECTED")));
    }
    @Test
    @DisplayName("포스터 올리기는 multipart/form-data의 poster 칸(파일 하나, 필수)으로 받아 EventWizardResponse를 돌려준다")
    void posterUploadIsMultipart() throws Exception {
        String body = POSTER_PATH + ".post.requestBody.content['multipart/form-data'].schema";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(POSTER_PATH + ".post.parameters[*].name", containsInAnyOrder("organizerId", "eventId")))
                .andExpect(jsonPath(body + ".properties.poster.type").value("string"))
                .andExpect(jsonPath(body + ".properties.poster.format").value("binary"))
                .andExpect(jsonPath(body + ".required", containsInAnyOrder("poster")))
                .andExpect(jsonPath(POSTER_PATH + ".post.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventWizardResponse"));
    }

    @Test
    @DisplayName("위저드 값의 포스터 주소는 올리기 전에 비어 있을 수 있다 — 필수 칸이 아니고 null을 받는다")
    void posterUrlIsNullable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SCHEMAS + ".EventWizardResponse.properties.posterUrl.type",
                        containsInAnyOrder("string", "null")));
    }

    @Test
    @DisplayName("포스터 파일은 포스터 열쇠 경로 하나로 열고, PNG · JPEG 그림을 돌려준다")
    void posterFileOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(POSTER_FILE_PATH + ".get.parameters[*].name", containsInAnyOrder("fileKey")))
                .andExpect(jsonPath(POSTER_FILE_PATH + ".get.responses['200'].content['image/png'].schema.format")
                        .value("binary"))
                .andExpect(jsonPath(POSTER_FILE_PATH + ".get.responses['200'].content['image/jpeg'].schema.format")
                        .value("binary"));
    }
}
