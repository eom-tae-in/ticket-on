package com.ticketon.api.event;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.AddEntranceUseCase;
import com.ticketon.application.event.port.in.AddZoneUseCase;
import com.ticketon.application.event.port.in.ChangeZoneUseCase;
import com.ticketon.application.event.port.in.GetEventEntrancesUseCase;
import com.ticketon.application.event.port.in.GetEventZonesUseCase;
import com.ticketon.application.event.port.in.RemoveEntranceUseCase;
import com.ticketon.application.event.port.in.RemoveZoneUseCase;
import com.ticketon.application.event.port.in.RenameEntranceUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 구역 · 입구 API의 요청 · 응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {EventZoneController.class, EventEntranceController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("구역 · 입구 API 명세")
class EventZoneEntranceApiDocsTest {

    private static final String ZONES_PATH = "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/zones']";
    private static final String ZONE_PATH = "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/zones/{zoneId}']";
    private static final String ENTRANCES_PATH = "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/entrances']";
    private static final String ENTRANCE_PATH =
            "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/entrances/{entranceId}']";
    private static final String SCHEMAS = "$.components.schemas";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddZoneUseCase addZoneUseCase;

    @MockitoBean
    private ChangeZoneUseCase changeZoneUseCase;

    @MockitoBean
    private RemoveZoneUseCase removeZoneUseCase;

    @MockitoBean
    private GetEventZonesUseCase getEventZonesUseCase;

    @MockitoBean
    private AddEntranceUseCase addEntranceUseCase;

    @MockitoBean
    private RenameEntranceUseCase renameEntranceUseCase;

    @MockitoBean
    private RemoveEntranceUseCase removeEntranceUseCase;

    @MockitoBean
    private GetEventEntrancesUseCase getEventEntrancesUseCase;

    @Test
    @DisplayName("구역은 목록 200 · 추가 201 · 수정 200 · 삭제 204, 입구도 같다")
    void operations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ZONES_PATH + ".get.parameters[*].name", containsInAnyOrder("organizerId", "eventId")))
                .andExpect(jsonPath(ZONES_PATH + ".get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventZonesResponse"))
                .andExpect(jsonPath(ZONES_PATH + ".post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AddZoneRequest"))
                .andExpect(jsonPath(ZONES_PATH + ".post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ZoneResponse"))
                .andExpect(jsonPath(ZONE_PATH + ".put.parameters[*].name", containsInAnyOrder("organizerId", "eventId", "zoneId")))
                .andExpect(jsonPath(ZONE_PATH + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ChangeZoneRequest"))
                .andExpect(jsonPath(ZONE_PATH + ".put.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ZoneResponse"))
                .andExpect(jsonPath(ZONE_PATH + ".delete.responses['204']").exists())
                .andExpect(jsonPath(ENTRANCES_PATH + ".get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventEntrancesResponse"))
                .andExpect(jsonPath(ENTRANCES_PATH + ".post.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EntranceRequest"))
                .andExpect(jsonPath(ENTRANCES_PATH + ".post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EntranceResponse"))
                .andExpect(jsonPath(ENTRANCE_PATH + ".put.parameters[*].name",
                        containsInAnyOrder("organizerId", "eventId", "entranceId")))
                .andExpect(jsonPath(ENTRANCE_PATH + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EntranceRequest"))
                .andExpect(jsonPath(ENTRANCE_PATH + ".delete.responses['204']").exists());
    }

    @Test
    @DisplayName("요청의 필수 칸 — 구역은 정원 밖의 칸, 입구는 이름")
    void requiredRequestFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".AddZoneRequest.required", containsInAnyOrder("kind", "name", "color", "basePrice")))
                .andExpect(jsonPath(SCHEMAS + ".ChangeZoneRequest.required", containsInAnyOrder("name", "color", "basePrice")))
                .andExpect(jsonPath(SCHEMAS + ".EntranceRequest.required", containsInAnyOrder("name")))
                .andExpect(jsonPath(SCHEMAS + ".AddZoneRequest.properties.kind.enum", containsInAnyOrder("DESIGNATED", "QUANTITY")));
    }

    @Test
    @DisplayName("응답의 칸 — 구역의 정원만 null일 수 있다")
    void responseFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".ZoneResponse.required",
                        containsInAnyOrder("zoneId", "kind", "name", "color", "basePrice")))
                .andExpect(jsonPath(SCHEMAS + ".ZoneResponse.properties.capacity.type", containsInAnyOrder("integer", "null")))
                .andExpect(jsonPath(SCHEMAS + ".EventZonesResponse.required", containsInAnyOrder("eventId", "zones")))
                .andExpect(jsonPath(SCHEMAS + ".EntranceResponse.required", containsInAnyOrder("entranceId", "name")))
                .andExpect(jsonPath(SCHEMAS + ".EventEntrancesResponse.required", containsInAnyOrder("eventId", "entrances")));
    }
}
