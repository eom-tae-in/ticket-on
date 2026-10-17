package com.ticketon.api.event;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.AddSeatBlockUseCase;
import com.ticketon.application.event.port.in.AssignSeatsToZoneUseCase;
import com.ticketon.application.event.port.in.ChangeSeatActivationUseCase;
import com.ticketon.application.event.port.in.ChangeSeatAttributeUseCase;
import com.ticketon.application.event.port.in.GetEventFloorPlanUseCase;
import com.ticketon.application.event.port.in.MoveSeatBlockUseCase;
import com.ticketon.application.event.port.in.PlaceStageUseCase;
import com.ticketon.application.event.port.in.RemoveSeatBlockUseCase;
import com.ticketon.application.event.port.in.RenameSeatBlockUseCase;
import com.ticketon.application.event.port.in.RotateSeatBlockUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 배치도 편집 API의 요청 · 응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {EventFloorPlanController.class, EventSeatBlockController.class, EventSeatController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("배치도 편집 API 명세")
class FloorPlanEditorApiDocsTest {

    private static final String EVENT = "/api/organizer/organizers/{organizerId}/events/{eventId}";
    private static final String FLOOR_PLAN_PATH = "$.paths['" + EVENT + "/floor-plan']";
    private static final String STAGE_PATH = "$.paths['" + EVENT + "/stage']";
    private static final String BLOCKS_PATH = "$.paths['" + EVENT + "/blocks']";
    private static final String BLOCK_PATH = "$.paths['" + EVENT + "/blocks/{blockId}']";
    private static final String BLOCK_NAME_PATH = "$.paths['" + EVENT + "/blocks/{blockId}/name']";
    private static final String BLOCK_POSITION_PATH = "$.paths['" + EVENT + "/blocks/{blockId}/position']";
    private static final String BLOCK_ANGLE_PATH = "$.paths['" + EVENT + "/blocks/{blockId}/angle']";
    private static final String SEATS_ZONE_PATH = "$.paths['" + EVENT + "/seats/zone']";
    private static final String SEATS_ATTRIBUTE_PATH = "$.paths['" + EVENT + "/seats/attribute']";
    private static final String SEATS_ACTIVATION_PATH = "$.paths['" + EVENT + "/seats/activation']";
    private static final String SCHEMAS = "$.components.schemas";
    private static final String JSON_BODY = ".requestBody.content['application/json'].schema['$ref']";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetEventFloorPlanUseCase getEventFloorPlanUseCase;

    @MockitoBean
    private PlaceStageUseCase placeStageUseCase;

    @MockitoBean
    private AddSeatBlockUseCase addSeatBlockUseCase;

    @MockitoBean
    private RenameSeatBlockUseCase renameSeatBlockUseCase;

    @MockitoBean
    private MoveSeatBlockUseCase moveSeatBlockUseCase;

    @MockitoBean
    private RotateSeatBlockUseCase rotateSeatBlockUseCase;

    @MockitoBean
    private RemoveSeatBlockUseCase removeSeatBlockUseCase;

    @MockitoBean
    private AssignSeatsToZoneUseCase assignSeatsToZoneUseCase;

    @MockitoBean
    private ChangeSeatAttributeUseCase changeSeatAttributeUseCase;

    @MockitoBean
    private ChangeSeatActivationUseCase changeSeatActivationUseCase;

    private static String response(String status) {
        return ".responses['" + status + "'].content['application/json'].schema['$ref']";
    }

    @Test
    @DisplayName("도면 조회 200 · 무대 200, 블록은 얹기 201 · 이름 · 위치 · 각도 200 · 삭제 204, 좌석 묶음은 200")
    void operations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FLOOR_PLAN_PATH + ".get" + response("200")).value("#/components/schemas/FloorPlanResponse"))
                .andExpect(jsonPath(STAGE_PATH + ".put" + JSON_BODY).value("#/components/schemas/CanvasPointRequest"))
                .andExpect(jsonPath(STAGE_PATH + ".put" + response("200")).value("#/components/schemas/CanvasPointResponse"))
                .andExpect(jsonPath(BLOCKS_PATH + ".post" + JSON_BODY).value("#/components/schemas/AddSeatBlockRequest"))
                .andExpect(jsonPath(BLOCKS_PATH + ".post" + response("201")).value("#/components/schemas/SeatBlockResponse"))
                .andExpect(jsonPath(BLOCK_NAME_PATH + ".put.parameters[*].name",
                        containsInAnyOrder("organizerId", "eventId", "blockId")))
                .andExpect(jsonPath(BLOCK_NAME_PATH + ".put" + JSON_BODY).value("#/components/schemas/RenameSeatBlockRequest"))
                .andExpect(jsonPath(BLOCK_NAME_PATH + ".put" + response("200")).value("#/components/schemas/SeatBlockResponse"))
                .andExpect(jsonPath(BLOCK_POSITION_PATH + ".put" + JSON_BODY).value("#/components/schemas/CanvasPointRequest"))
                .andExpect(jsonPath(BLOCK_ANGLE_PATH + ".put" + JSON_BODY).value("#/components/schemas/RotateSeatBlockRequest"))
                .andExpect(jsonPath(BLOCK_PATH + ".delete.responses['204']").exists())
                .andExpect(jsonPath(SEATS_ZONE_PATH + ".put" + JSON_BODY).value("#/components/schemas/AssignSeatsToZoneRequest"))
                .andExpect(jsonPath(SEATS_ZONE_PATH + ".put" + response("200")).value("#/components/schemas/SeatsResponse"))
                .andExpect(jsonPath(SEATS_ATTRIBUTE_PATH + ".put" + JSON_BODY).value("#/components/schemas/ChangeSeatAttributeRequest"))
                .andExpect(jsonPath(SEATS_ACTIVATION_PATH + ".put" + JSON_BODY)
                        .value("#/components/schemas/ChangeSeatActivationRequest"));
    }

    @Test
    @DisplayName("요청의 필수 칸 — 블록은 행 · 열(이름 · 자리 · 각도는 비워도 된다), 좌석 묶음은 좌석 id와 값(구역 id는 비우면 배정 풀기)")
    void requiredRequestFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".AddSeatBlockRequest.required", containsInAnyOrder("rows", "columns")))
                .andExpect(jsonPath(SCHEMAS + ".CanvasPointRequest.required", containsInAnyOrder("x", "y")))
                .andExpect(jsonPath(SCHEMAS + ".RotateSeatBlockRequest.required", containsInAnyOrder("degrees")))
                .andExpect(jsonPath(SCHEMAS + ".AssignSeatsToZoneRequest.required", containsInAnyOrder("seatIds")))
                .andExpect(jsonPath(SCHEMAS + ".ChangeSeatAttributeRequest.required", containsInAnyOrder("seatIds", "attribute")))
                .andExpect(jsonPath(SCHEMAS + ".ChangeSeatActivationRequest.required", containsInAnyOrder("seatIds", "active")))
                .andExpect(jsonPath(SCHEMAS + ".ChangeSeatAttributeRequest.properties.attribute.enum",
                        containsInAnyOrder("GENERAL", "WHEELCHAIR", "RESTRICTED_VIEW")));
    }

    @Test
    @DisplayName("응답의 칸 — 좌석의 구역 id와 도면의 무대 위치만 null일 수 있다")
    void responseFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(SCHEMAS + ".FloorPlanResponse.required", containsInAnyOrder("eventId", "zones", "blocks")))
                .andExpect(jsonPath(SCHEMAS + ".SeatBlockResponse.required",
                        containsInAnyOrder("blockId", "name", "rows", "columns", "position", "angleDegrees", "seats")))
                .andExpect(jsonPath(SCHEMAS + ".SeatResponse.required",
                        containsInAnyOrder("seatId", "row", "number", "attribute", "active")))
                .andExpect(jsonPath(SCHEMAS + ".SeatResponse.properties.zoneId.type", containsInAnyOrder("integer", "null")))
                .andExpect(jsonPath(SCHEMAS + ".SeatsResponse.required", containsInAnyOrder("eventId", "seats")))
                .andExpect(jsonPath(SCHEMAS + ".CanvasPointResponse.required", containsInAnyOrder("x", "y")));
    }
}
