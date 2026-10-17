package com.ticketon.api.event;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.AddSeatBlockCommand;
import com.ticketon.application.event.port.in.AddSeatBlockUseCase;
import com.ticketon.application.event.port.in.AssignSeatsToZoneCommand;
import com.ticketon.application.event.port.in.AssignSeatsToZoneUseCase;
import com.ticketon.application.event.port.in.CanvasPointResult;
import com.ticketon.application.event.port.in.ChangeSeatActivationCommand;
import com.ticketon.application.event.port.in.ChangeSeatActivationUseCase;
import com.ticketon.application.event.port.in.ChangeSeatAttributeCommand;
import com.ticketon.application.event.port.in.ChangeSeatAttributeUseCase;
import com.ticketon.application.event.port.in.FloorPlanResult;
import com.ticketon.application.event.port.in.GetEventFloorPlanQuery;
import com.ticketon.application.event.port.in.GetEventFloorPlanUseCase;
import com.ticketon.application.event.port.in.MoveSeatBlockCommand;
import com.ticketon.application.event.port.in.MoveSeatBlockUseCase;
import com.ticketon.application.event.port.in.PlaceStageCommand;
import com.ticketon.application.event.port.in.PlaceStageUseCase;
import com.ticketon.application.event.port.in.RemoveSeatBlockCommand;
import com.ticketon.application.event.port.in.RemoveSeatBlockUseCase;
import com.ticketon.application.event.port.in.RenameSeatBlockCommand;
import com.ticketon.application.event.port.in.RenameSeatBlockUseCase;
import com.ticketon.application.event.port.in.RotateSeatBlockCommand;
import com.ticketon.application.event.port.in.RotateSeatBlockUseCase;
import com.ticketon.application.event.port.in.SeatBlockResult;
import com.ticketon.application.event.port.in.SeatResult;
import com.ticketon.application.event.port.in.SeatsResult;
import com.ticketon.application.event.port.in.ZoneResult;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.SeatAttribute;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 배치도 편집 API(블록 · 좌석 묶음 · 무대 · 도면 조회) — 경로의 주최자 · 행사 · 블록 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 입력
 * 검증(400)과 응답 모양, 유스케이스가 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다. 문서의 10행 × 20열(docs/02), "1증" → "1층",
 * "R석, 5만원".
 */
@WebMvcTest(controllers = {EventFloorPlanController.class, EventSeatBlockController.class, EventSeatController.class})
@Import(SecurityConfiguration.class)
@DisplayName("배치도 편집 API (위저드 3단계)")
class FloorPlanEditorControllerTest {

    private static final String EVENT_PATH = "/api/organizer/organizers/12/events/7";

    private static final String BLOCKS_PATH = EVENT_PATH + "/blocks";

    private static final String SEATS_PATH = EVENT_PATH + "/seats";

    private static final AccountRef PLANNER = AccountRef.of(AccountKind.STAFF, 2);

    private static final ActingStaff PLANNER_IN_TWELVE_MONTHS = new ActingStaff(PLANNER, 12);

    private static final SeatResult FIRST_SEAT = new SeatResult(10_001, 1, 1, null, SeatAttribute.GENERAL, true);

    private static final SeatResult SECOND_SEAT = new SeatResult(10_002, 1, 2, 31L, SeatAttribute.WHEELCHAIR, false);

    private static final SeatBlockResult FIRST_FLOOR = new SeatBlockResult(41, "1층", 1, 2, new CanvasPointResult(120, 80), 345,
            List.of(FIRST_SEAT, SECOND_SEAT));

    private static final String FIRST_FLOOR_JSON = """
            {"blockId": 41, "name": "1층", "rows": 1, "columns": 2, "position": {"x": 120, "y": 80}, "angleDegrees": 345,
             "seats": [
               {"seatId": 10001, "row": 1, "number": 1, "zoneId": null, "attribute": "GENERAL", "active": true},
               {"seatId": 10002, "row": 1, "number": 2, "zoneId": 31, "attribute": "WHEELCHAIR", "active": false}]}""";

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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).with(loggedInAs(PLANNER)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    @DisplayName("블록")
    class Blocks {

        @Test
        @DisplayName("10행 × 20열 '1층'을 얹으면 앞뒤 공백을 걷은 이름 · 크기 · 자리 · 0~360으로 맞춘 각도를 넘기고 201에 블록과 좌석을 돌려준다")
        void addsBlock() throws Exception {
            given(addSeatBlockUseCase.add(new AddSeatBlockCommand(PLANNER_IN_TWELVE_MONTHS, 7, BlockName.of("1층"), BlockSize.of(10, 20),
                    CanvasPoint.of(120, 80), BlockAngle.ofDegrees(345)))).willReturn(FIRST_FLOOR);

            mockMvc.perform(json(post(BLOCKS_PATH), """
                            {"name": " 1층 ", "rows": 10, "columns": 20, "position": {"x": 120, "y": 80}, "angleDegrees": -15}"""))
                    .andExpect(status().isCreated())
                    .andExpect(content().json(FIRST_FLOOR_JSON, JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("이름 · 자리 · 각도를 비우면 '블록 N'(이름 없음) · 원점 · 0°로 넘긴다")
        void addsBlockWithDefaults() throws Exception {
            given(addSeatBlockUseCase.add(new AddSeatBlockCommand(PLANNER_IN_TWELVE_MONTHS, 7, null, BlockSize.of(5, 10),
                    CanvasPoint.ORIGIN, BlockAngle.NONE))).willReturn(FIRST_FLOOR);

            mockMvc.perform(json(post(BLOCKS_PATH), """
                            {"name": "  ", "rows": 5, "columns": 10}"""))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("빈 행 수 · 51열 · 21자 이름 · 세로 좌표 없는 자리는 칸마다 까닭을 담아 400이고 유스케이스를 부르지 않는다")
        void rejectsInvalidBlock() throws Exception {
            mockMvc.perform(json(post(BLOCKS_PATH), """
                            {"name": "%s", "rows": null, "columns": 51, "position": {"x": 120}}""".formatted("층".repeat(21))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].message").value("블록 이름은 20자 이하로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'rows')].message").value("행 수를 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'columns')].message").value("열 수는 1 이상 50 이하로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'position.y')].message").value("세로 위치를 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(4));
            mockMvc.perform(json(post(BLOCKS_PATH), """
                            {"rows": 0, "columns": 10}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'rows')].message").value("행 수는 1 이상 50 이하로 입력해 주세요."));

            verifyNoInteractions(addSeatBlockUseCase);
        }

        @Test
        @DisplayName("50행 × 50열(2,500석)과 20자 이름(이모지 한 자 포함)은 받는다")
        void acceptsLargestBlock() throws Exception {
            given(addSeatBlockUseCase.add(any())).willReturn(FIRST_FLOOR);

            mockMvc.perform(json(post(BLOCKS_PATH), """
                            {"name": "%s", "rows": 50, "columns": 50}""".formatted("층".repeat(19) + "😀")))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("이름이 겹치면 유스케이스가 막아 409다")
        void nameConflicts() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.BLOCK_NAME_ALREADY_TAKEN)).given(addSeatBlockUseCase).add(any());
            mockMvc.perform(json(post(BLOCKS_PATH), """
                            {"name": "1층", "rows": 10, "columns": 20}"""))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("BLOCK_NAME_ALREADY_TAKEN", "이미 있는 블록 이름입니다. 다른 이름을 입력해 주세요."));

            willThrow(new RuleViolationException(EventErrorCode.BLOCK_NAME_TAKEN_BY_ZONE)).given(renameSeatBlockUseCase).rename(any());
            mockMvc.perform(json(put(BLOCKS_PATH + "/41/name"), "{\"name\": \"R석\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("BLOCK_NAME_TAKEN_BY_ZONE", "이미 구역 이름으로 쓰고 있습니다. 다른 이름을 입력해 주세요."));
        }

        @Test
        @DisplayName("'1증'을 '1층'으로 고치면 경로의 블록 id와 이름을 넘기고, 이름을 비우면 '블록 N'(이름 없음)으로 넘긴다")
        void renamesBlock() throws Exception {
            given(renameSeatBlockUseCase.rename(new RenameSeatBlockCommand(PLANNER_IN_TWELVE_MONTHS, 7, 41, BlockName.of("1층"))))
                    .willReturn(FIRST_FLOOR);

            mockMvc.perform(json(put(BLOCKS_PATH + "/41/name"), "{\"name\": \"1층\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("1층"));

            mockMvc.perform(json(put(BLOCKS_PATH + "/41/name"), "{}"));
            then(renameSeatBlockUseCase).should().rename(new RenameSeatBlockCommand(PLANNER_IN_TWELVE_MONTHS, 7, 41, null));
        }

        @Test
        @DisplayName("블록을 옮기고 돌리면 자리 · 0~360으로 맞춘 각도를 넘긴다 — 좌표 · 각도가 비면 400이다")
        void movesAndRotatesBlock() throws Exception {
            given(moveSeatBlockUseCase.move(new MoveSeatBlockCommand(PLANNER_IN_TWELVE_MONTHS, 7, 41, CanvasPoint.of(-40, 260))))
                    .willReturn(FIRST_FLOOR);
            given(rotateSeatBlockUseCase.rotate(new RotateSeatBlockCommand(PLANNER_IN_TWELVE_MONTHS, 7, 41, BlockAngle.ofDegrees(15))))
                    .willReturn(FIRST_FLOOR);

            mockMvc.perform(json(put(BLOCKS_PATH + "/41/position"), "{\"x\": -40, \"y\": 260}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.blockId").value(41));
            mockMvc.perform(json(put(BLOCKS_PATH + "/41/angle"), "{\"degrees\": 375}"))
                    .andExpect(status().isOk());
            mockMvc.perform(json(put(BLOCKS_PATH + "/41/position"), "{\"y\": 260}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'x')].message").value("가로 위치를 입력해 주세요."));
            mockMvc.perform(json(put(BLOCKS_PATH + "/41/angle"), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'degrees')].message").value("각도를 입력해 주세요."));
        }

        @Test
        @DisplayName("블록을 지우면 204이고, 이미 지운 블록은 404, 판매가 끝난 행사는 409다. 블록 id가 1 이상의 정수가 아니면 400이다")
        void removesBlock() throws Exception {
            mockMvc.perform(delete(BLOCKS_PATH + "/41").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNoContent());
            then(removeSeatBlockUseCase).should().remove(new RemoveSeatBlockCommand(PLANNER_IN_TWELVE_MONTHS, 7, 41));

            willThrow(new NotFoundException(EventErrorCode.BLOCK_NOT_FOUND)).given(removeSeatBlockUseCase).remove(any());
            mockMvc.perform(delete(BLOCKS_PATH + "/41").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("BLOCK_NOT_FOUND", "블록을 찾을 수 없습니다."));

            willThrow(new RuleViolationException(EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED)).given(removeSeatBlockUseCase)
                    .remove(any());
            mockMvc.perform(delete(BLOCKS_PATH + "/41").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EDIT_LOCKED_AFTER_SALES_CLOSED", "판매가 끝난 행사는 입구 목록과 검표 계정만 고칠 수 있습니다."));

            mockMvc.perform(delete(BLOCKS_PATH + "/0").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("blockId"));
        }
    }

    @Nested
    @DisplayName("좌석 묶음")
    class Seats {

        @Test
        @DisplayName("좌석 여러 개를 R석에 칠하면 겹친 id는 한 번으로 묶어 넘기고 그 좌석들의 지금 값을 돌려준다 — 구역을 비우면 배정을 푼다")
        void assignsZone() throws Exception {
            given(assignSeatsToZoneUseCase.assign(new AssignSeatsToZoneCommand(PLANNER_IN_TWELVE_MONTHS, 7, Set.of(10_001L, 10_002L), 31L)))
                    .willReturn(new SeatsResult(7, List.of(FIRST_SEAT, SECOND_SEAT)));

            mockMvc.perform(json(put(SEATS_PATH + "/zone"), "{\"seatIds\": [10001, 10002, 10001], \"zoneId\": 31}"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"eventId": 7, "seats": [
                              {"seatId": 10001, "row": 1, "number": 1, "zoneId": null, "attribute": "GENERAL", "active": true},
                              {"seatId": 10002, "row": 1, "number": 2, "zoneId": 31, "attribute": "WHEELCHAIR", "active": false}]}""",
                            JsonCompareMode.STRICT));

            mockMvc.perform(json(put(SEATS_PATH + "/zone"), "{\"seatIds\": [10001], \"zoneId\": null}"));
            then(assignSeatsToZoneUseCase).should().assign(new AssignSeatsToZoneCommand(PLANNER_IN_TWELVE_MONTHS, 7, Set.of(10_001L), null));
        }

        @Test
        @DisplayName("빈 좌석 목록 · 1 이상이 아닌 좌석 id · 구역 id는 400이고 유스케이스를 부르지 않는다")
        void rejectsInvalidSelection() throws Exception {
            mockMvc.perform(json(put(SEATS_PATH + "/zone"), "{\"seatIds\": [], \"zoneId\": 31}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'seatIds')].message").value("좌석을 골라 주세요."));
            mockMvc.perform(json(put(SEATS_PATH + "/zone"), "{\"seatIds\": [0], \"zoneId\": 31}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(startsWith("seatIds")))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("형식이 올바르지 않습니다."));
            mockMvc.perform(json(put(SEATS_PATH + "/zone"), "{\"seatIds\": [10001], \"zoneId\": 0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'zoneId')].message").value("형식이 올바르지 않습니다."));
            mockMvc.perform(json(put(SEATS_PATH + "/activation"), "{\"active\": false}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'seatIds')].message").value("좌석을 골라 주세요."));

            verifyNoInteractions(assignSeatsToZoneUseCase, changeSeatActivationUseCase);
        }

        @Test
        @DisplayName("휠체어로 정하고 좌석을 끄면 속성 · 켤지 끌지를 넘긴다 — 속성이 비거나 모르는 값, 켤지 끌지가 비면 400이다")
        void changesAttributeAndActivation() throws Exception {
            given(changeSeatAttributeUseCase.change(new ChangeSeatAttributeCommand(PLANNER_IN_TWELVE_MONTHS, 7, Set.of(10_002L),
                    SeatAttribute.WHEELCHAIR))).willReturn(new SeatsResult(7, List.of(SECOND_SEAT)));
            given(changeSeatActivationUseCase.change(new ChangeSeatActivationCommand(PLANNER_IN_TWELVE_MONTHS, 7, Set.of(10_002L), false)))
                    .willReturn(new SeatsResult(7, List.of(SECOND_SEAT)));

            mockMvc.perform(json(put(SEATS_PATH + "/attribute"), "{\"seatIds\": [10002], \"attribute\": \"WHEELCHAIR\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seats[0].attribute").value("WHEELCHAIR"));
            mockMvc.perform(json(put(SEATS_PATH + "/activation"), "{\"seatIds\": [10002], \"active\": false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seats[0].active").value(false));
            mockMvc.perform(json(put(SEATS_PATH + "/attribute"), "{\"seatIds\": [10002]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'attribute')].message").value("좌석 속성을 골라 주세요."));
            mockMvc.perform(json(put(SEATS_PATH + "/attribute"), "{\"seatIds\": [10002], \"attribute\": \"EXCLUDED\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(put(SEATS_PATH + "/activation"), "{\"seatIds\": [10002]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'active')].message").value("켤지 끌지 골라 주세요."));
        }

        @Test
        @DisplayName("도면에 없는 좌석 · 구역은 404, 수량형 구역은 409다")
        void rejections() throws Exception {
            willThrow(new NotFoundException(EventErrorCode.SEAT_NOT_FOUND)).given(changeSeatActivationUseCase).change(any());
            mockMvc.perform(json(put(SEATS_PATH + "/activation"), "{\"seatIds\": [99999], \"active\": false}"))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("SEAT_NOT_FOUND", "좌석을 찾을 수 없습니다."));

            willThrow(new RuleViolationException(EventErrorCode.QUANTITY_ZONE_HAS_NO_SEATS)).given(assignSeatsToZoneUseCase).assign(any());
            mockMvc.perform(json(put(SEATS_PATH + "/zone"), "{\"seatIds\": [10001], \"zoneId\": 33}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("QUANTITY_ZONE_HAS_NO_SEATS", "수량형 구역에는 좌석을 배정할 수 없습니다."));
        }
    }

    @Nested
    @DisplayName("도면 · 무대")
    class FloorPlanAndStage {

        @Test
        @DisplayName("도면 전체를 열면 구역 · 블록과 좌석 · 무대 위치를 돌려준다 — 무대를 놓지 않았으면 null이다")
        void readsFloorPlan() throws Exception {
            given(getEventFloorPlanUseCase.get(new GetEventFloorPlanQuery(PLANNER_IN_TWELVE_MONTHS, 7)))
                    .willReturn(new FloorPlanResult(7, null, List.of(new ZoneResult(31, ZoneKind.DESIGNATED, "R석", "#E5484D", 50_000, null)),
                            List.of(FIRST_FLOOR)));

            mockMvc.perform(get(EVENT_PATH + "/floor-plan").with(loggedInAs(PLANNER)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"eventId": 7, "stagePosition": null,
                             "zones": [{"zoneId": 31, "kind": "DESIGNATED", "name": "R석", "color": "#E5484D", "basePrice": 50000,
                                        "capacity": null}],
                             "blocks": [%s]}""".formatted(FIRST_FLOOR_JSON), JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("무대를 놓으면 자리를 넘기고 놓은 자리를 돌려준다 — 좌표가 비면 400이다")
        void placesStage() throws Exception {
            given(placeStageUseCase.place(new PlaceStageCommand(PLANNER_IN_TWELVE_MONTHS, 7, CanvasPoint.of(400, -30))))
                    .willReturn(new CanvasPointResult(400, -30));

            mockMvc.perform(json(put(EVENT_PATH + "/stage"), "{\"x\": 400, \"y\": -30}"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"x\": 400, \"y\": -30}", JsonCompareMode.STRICT));
            mockMvc.perform(json(put(EVENT_PATH + "/stage"), "{\"x\": 400}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'y')].message").value("세로 위치를 입력해 주세요."));
        }

        @Test
        @DisplayName("다른 주최자의 운영진은 403, 남의 행사는 404, 운영진이 아닌 계정은 403, 로그인하지 않으면 401이다")
        void rejections() throws Exception {
            willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF)).given(getEventFloorPlanUseCase).get(any());
            mockMvc.perform(get(EVENT_PATH + "/floor-plan").with(loggedInAs(PLANNER)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));

            willThrow(new NotFoundException(EventErrorCode.EVENT_NOT_FOUND)).given(placeStageUseCase).place(any());
            mockMvc.perform(json(put(EVENT_PATH + "/stage"), "{\"x\": 400, \"y\": -30}"))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("EVENT_NOT_FOUND", "행사를 찾을 수 없습니다."));

            mockMvc.perform(post(BLOCKS_PATH).with(csrf()).with(loggedInAs(AccountKind.AUDIENCE, 1))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"rows\": 10, \"columns\": 20}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(EVENT_PATH + "/floor-plan")).andExpect(status().isUnauthorized());
            verifyNoInteractions(addSeatBlockUseCase);
        }
    }
}
