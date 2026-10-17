package com.ticketon.api.event;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
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
import com.ticketon.application.event.port.in.AddEntranceCommand;
import com.ticketon.application.event.port.in.AddEntranceUseCase;
import com.ticketon.application.event.port.in.AddZoneCommand;
import com.ticketon.application.event.port.in.AddZoneUseCase;
import com.ticketon.application.event.port.in.ChangeZoneCommand;
import com.ticketon.application.event.port.in.ChangeZoneUseCase;
import com.ticketon.application.event.port.in.EntranceResult;
import com.ticketon.application.event.port.in.EventEntrancesResult;
import com.ticketon.application.event.port.in.EventZonesResult;
import com.ticketon.application.event.port.in.GetEventEntrancesQuery;
import com.ticketon.application.event.port.in.GetEventEntrancesUseCase;
import com.ticketon.application.event.port.in.GetEventZonesQuery;
import com.ticketon.application.event.port.in.GetEventZonesUseCase;
import com.ticketon.application.event.port.in.RemoveEntranceCommand;
import com.ticketon.application.event.port.in.RemoveEntranceUseCase;
import com.ticketon.application.event.port.in.RemoveZoneCommand;
import com.ticketon.application.event.port.in.RemoveZoneUseCase;
import com.ticketon.application.event.port.in.RenameEntranceCommand;
import com.ticketon.application.event.port.in.RenameEntranceUseCase;
import com.ticketon.application.event.port.in.ZoneResult;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EntranceName;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneSettings;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.util.List;
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
 * 구역 · 입구 API — 경로의 주최자 · 행사 · 구역 · 입구 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 입력 검증(400)과 응답 모양, 유스케이스가
 * 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다. 문서의 "스탠딩A 300", "R석 5만원", "정문"(docs/02).
 */
@WebMvcTest(controllers = {EventZoneController.class, EventEntranceController.class})
@Import(SecurityConfiguration.class)
@DisplayName("구역 · 입구 API (위저드 3 · 6단계)")
class EventZoneEntranceControllerTest {

    private static final String ZONES_PATH = "/api/organizer/organizers/12/events/7/zones";

    private static final String ENTRANCES_PATH = "/api/organizer/organizers/12/events/7/entrances";

    private static final AccountRef PLANNER = AccountRef.of(AccountKind.STAFF, 2);

    private static final ActingStaff PLANNER_IN_TWELVE_MONTHS = new ActingStaff(PLANNER, 12);

    private static final ZoneSettings STANDING_A_300 = ZoneSettings.quantity(ZoneName.of("스탠딩A"), ZoneColor.of("#30A46C"),
            ZoneBasePrice.of(30_000), ZoneCapacity.of(300));

    private static final ZoneResult STANDING_A_RESULT = new ZoneResult(31, ZoneKind.QUANTITY, "스탠딩A", "#30A46C", 30_000, 300);

    private static final ZoneResult R_SEAT_RESULT = new ZoneResult(32, ZoneKind.DESIGNATED, "R석", "#E5484D", 50_000, null);

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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).with(loggedInAs(PLANNER)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    @DisplayName("구역")
    class Zones {

        @Test
        @DisplayName("수량형 '스탠딩A 300'을 더하면 경로의 주최자 · 행사와 로그인한 운영진, 앞뒤 공백을 걷고 대문자로 맞춘 칸을 넘기고 201에 구역을 돌려준다")
        void addsQuantityZone() throws Exception {
            given(addZoneUseCase.add(new AddZoneCommand(PLANNER_IN_TWELVE_MONTHS, 7, ZoneKind.QUANTITY, STANDING_A_300)))
                    .willReturn(STANDING_A_RESULT);

            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": "QUANTITY", "name": " 스탠딩A ", "color": "#30a46c", "basePrice": 30000, "capacity": 300}"""))
                    .andExpect(status().isCreated())
                    .andExpect(content().json("""
                            {"zoneId": 31, "kind": "QUANTITY", "name": "스탠딩A", "color": "#30A46C", "basePrice": 30000,
                             "capacity": 300}""", JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("지정형 'R석 5만원'은 정원을 비워 보내고, 응답의 정원은 null이다")
        void addsDesignatedZone() throws Exception {
            given(addZoneUseCase.add(new AddZoneCommand(PLANNER_IN_TWELVE_MONTHS, 7, ZoneKind.DESIGNATED,
                    ZoneSettings.designated(ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000)))))
                    .willReturn(R_SEAT_RESULT);

            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": "DESIGNATED", "name": "R석", "color": "#E5484D", "basePrice": 50000}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.capacity").value((Object) null))
                    .andExpect(jsonPath("$.kind").value("DESIGNATED"));
        }

        @Test
        @DisplayName("빈 칸(전각 공백 포함)은 칸마다 까닭을 담아 400이고 유스케이스를 부르지 않는다")
        void rejectsBlankFields() throws Exception {
            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": null, "name": "　", "color": "", "basePrice": null}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'kind')].message").value("구역 종류를 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].message").value("구역 이름을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'color')].message").value("구역 색을 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'basePrice')].message").value("기본가를 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(4));

            verifyNoInteractions(addZoneUseCase);
        }

        @Test
        @DisplayName("21자 이름 · #RRGGBB가 아닌 색 · 0원 기본가 · 0 정원은 값 객체와 같은 규칙으로 400이다")
        void rejectsOutOfRuleFields() throws Exception {
            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": "QUANTITY", "name": "%s", "color": "red", "basePrice": 0, "capacity": 0}"""
                            .formatted("스".repeat(21))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].message").value("구역 이름은 20자 이하로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'color')].message").value("구역 색은 #RRGGBB 꼴로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'basePrice')].message").value("기본가는 1원 이상으로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'capacity')].message").value("정원은 1 이상으로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(4));

            verifyNoInteractions(addZoneUseCase);
        }

        @Test
        @DisplayName("20자 이름(이모지 한 자 포함)은 받는다")
        void acceptsTwentyCharacterName() throws Exception {
            given(addZoneUseCase.add(any())).willReturn(STANDING_A_RESULT);

            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": "QUANTITY", "name": "%s", "color": "#30A46C", "basePrice": 30000, "capacity": 300}"""
                            .formatted("스".repeat(19) + "😀")))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("모르는 종류는 400이다")
        void rejectsUnknownKind() throws Exception {
            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": "STANDING", "name": "스탠딩A", "color": "#30A46C", "basePrice": 30000, "capacity": 300}"""))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(addZoneUseCase);
        }

        @Test
        @DisplayName("정원이 종류와 맞지 않거나 이름이 겹치면 유스케이스가 막아 409다")
        void ruleViolationsAreConflicts() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.ZONE_CAPACITY_REQUIRED)).given(addZoneUseCase).add(any());
            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": "QUANTITY", "name": "스탠딩A", "color": "#30A46C", "basePrice": 30000}"""))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("ZONE_CAPACITY_REQUIRED", "수량형 구역은 정원을 입력해 주세요."));

            willThrow(new RuleViolationException(EventErrorCode.ZONE_CAPACITY_ONLY_FOR_QUANTITY)).given(changeZoneUseCase)
                    .change(any());
            mockMvc.perform(json(put(ZONES_PATH + "/32"), """
                            {"name": "R석", "color": "#E5484D", "basePrice": 50000, "capacity": 120}"""))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("ZONE_CAPACITY_ONLY_FOR_QUANTITY", "정원은 수량형 구역에만 입력합니다."));

            willThrow(new RuleViolationException(EventErrorCode.ZONE_NAME_TAKEN_BY_BLOCK)).given(addZoneUseCase).add(any());
            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": "DESIGNATED", "name": "1층", "color": "#E5484D", "basePrice": 50000}"""))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("ZONE_NAME_TAKEN_BY_BLOCK", "이미 블록 이름으로 쓰고 있습니다. 다른 이름을 입력해 주세요."));
        }

        @Test
        @DisplayName("구역을 고치면 경로의 구역 id와 칸을 모두 넘기고 고친 구역을 돌려준다")
        void changesZone() throws Exception {
            ZoneSettings enlarged = ZoneSettings.quantity(ZoneName.of("스탠딩A"), ZoneColor.of("#30A46C"), ZoneBasePrice.of(30_000),
                    ZoneCapacity.of(500));
            given(changeZoneUseCase.change(new ChangeZoneCommand(PLANNER_IN_TWELVE_MONTHS, 7, 31, enlarged)))
                    .willReturn(new ZoneResult(31, ZoneKind.QUANTITY, "스탠딩A", "#30A46C", 30_000, 500));

            mockMvc.perform(json(put(ZONES_PATH + "/31"), """
                            {"name": "스탠딩A", "color": "#30A46C", "basePrice": 30000, "capacity": 500}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.capacity").value(500));
        }

        @Test
        @DisplayName("구역을 지우면 204이고, 이미 지운 구역은 404다")
        void removesZone() throws Exception {
            mockMvc.perform(delete(ZONES_PATH + "/31").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNoContent());
            then(removeZoneUseCase).should().remove(new RemoveZoneCommand(PLANNER_IN_TWELVE_MONTHS, 7, 31));

            willThrow(new NotFoundException(EventErrorCode.ZONE_NOT_FOUND)).given(removeZoneUseCase).remove(any());
            mockMvc.perform(delete(ZONES_PATH + "/31").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("ZONE_NOT_FOUND", "구역을 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("구역 목록을 보면 더한 순서대로 돌려준다. 행사 · 구역 id가 1 이상의 정수가 아니면 400이다")
        void listsZones() throws Exception {
            given(getEventZonesUseCase.get(new GetEventZonesQuery(PLANNER_IN_TWELVE_MONTHS, 7)))
                    .willReturn(new EventZonesResult(7, List.of(STANDING_A_RESULT, R_SEAT_RESULT)));

            mockMvc.perform(get(ZONES_PATH).with(loggedInAs(PLANNER)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"eventId": 7, "zones": [
                              {"zoneId": 31, "kind": "QUANTITY", "name": "스탠딩A", "color": "#30A46C", "basePrice": 30000,
                               "capacity": 300},
                              {"zoneId": 32, "kind": "DESIGNATED", "name": "R석", "color": "#E5484D", "basePrice": 50000,
                               "capacity": null}]}""", JsonCompareMode.STRICT));
            mockMvc.perform(get("/api/organizer/organizers/12/events/0/zones").with(loggedInAs(PLANNER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("eventId"));
            mockMvc.perform(delete(ZONES_PATH + "/0").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("zoneId"));
        }

        @Test
        @DisplayName("판매가 끝난 행사는 409, 다른 주최자의 운영진은 403, 남의 행사는 404다")
        void rejections() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED)).given(addZoneUseCase).add(any());
            mockMvc.perform(json(post(ZONES_PATH), """
                            {"kind": "DESIGNATED", "name": "R석", "color": "#E5484D", "basePrice": 50000}"""))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EDIT_LOCKED_AFTER_SALES_CLOSED", "판매가 끝난 행사는 입구 목록과 검표 계정만 고칠 수 있습니다."));

            willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF)).given(getEventZonesUseCase).get(any());
            mockMvc.perform(get(ZONES_PATH).with(loggedInAs(PLANNER)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));

            willThrow(new NotFoundException(EventErrorCode.EVENT_NOT_FOUND)).given(changeZoneUseCase).change(any());
            mockMvc.perform(json(put(ZONES_PATH + "/31"), """
                            {"name": "스탠딩A", "color": "#30A46C", "basePrice": 30000, "capacity": 300}"""))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("EVENT_NOT_FOUND", "행사를 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("운영진이 아닌 계정은 403, 로그인하지 않으면 401이다")
        void onlyStaffArea() throws Exception {
            mockMvc.perform(post(ZONES_PATH).with(csrf()).with(loggedInAs(AccountKind.AUDIENCE, 1))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kind\": \"QUANTITY\", \"name\": \"스탠딩A\", \"color\": \"#30A46C\", \"basePrice\": 30000, \"capacity\": 300}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(ZONES_PATH)).andExpect(status().isUnauthorized());

            verifyNoInteractions(addZoneUseCase, getEventZonesUseCase);
        }
    }

    @Nested
    @DisplayName("입구")
    class Entrances {

        @Test
        @DisplayName("입구를 더하면 앞뒤 공백을 걷은 이름을 넘기고 201에 입구를 돌려준다")
        void addsEntrance() throws Exception {
            given(addEntranceUseCase.add(new AddEntranceCommand(PLANNER_IN_TWELVE_MONTHS, 7, EntranceName.of("정문"))))
                    .willReturn(new EntranceResult(41, "정문"));

            mockMvc.perform(json(post(ENTRANCES_PATH), "{\"name\": \" 정문 \"}"))
                    .andExpect(status().isCreated())
                    .andExpect(content().json("{\"entranceId\": 41, \"name\": \"정문\"}", JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("빈 이름은 400, 21자 이름도 400이다 — 유스케이스를 부르지 않는다")
        void rejectsInvalidNames() throws Exception {
            mockMvc.perform(json(post(ENTRANCES_PATH), "{\"name\": \"　\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].message").value("입구 이름을 입력해 주세요."));
            mockMvc.perform(json(put(ENTRANCES_PATH + "/41"), "{\"name\": \"%s\"}".formatted("문".repeat(21))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')].message").value("입구 이름은 20자 이하로 입력해 주세요."));

            verifyNoInteractions(addEntranceUseCase, renameEntranceUseCase);
        }

        @Test
        @DisplayName("이름을 바꾸면 경로의 입구 id와 새 이름을 넘기고, 같은 이름이 있으면 409다")
        void renamesEntrance() throws Exception {
            given(renameEntranceUseCase.rename(new RenameEntranceCommand(PLANNER_IN_TWELVE_MONTHS, 7, 41, EntranceName.of("옆문"))))
                    .willReturn(new EntranceResult(41, "옆문"));

            mockMvc.perform(json(put(ENTRANCES_PATH + "/41"), "{\"name\": \"옆문\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("옆문"));

            willThrow(new RuleViolationException(EventErrorCode.ENTRANCE_NAME_ALREADY_TAKEN)).given(renameEntranceUseCase)
                    .rename(any());
            mockMvc.perform(json(put(ENTRANCES_PATH + "/41"), "{\"name\": \"정문\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("ENTRANCE_NAME_ALREADY_TAKEN", "이미 있는 입구 이름입니다. 다른 이름을 입력해 주세요."));
        }

        @Test
        @DisplayName("입구를 지우면 204, 지난 행사는 409, 이미 지운 입구는 404다")
        void removesEntrance() throws Exception {
            mockMvc.perform(delete(ENTRANCES_PATH + "/41").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNoContent());
            then(removeEntranceUseCase).should().remove(new RemoveEntranceCommand(PLANNER_IN_TWELVE_MONTHS, 7, 41));

            willThrow(new RuleViolationException(EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT)).given(removeEntranceUseCase).remove(any());
            mockMvc.perform(delete(ENTRANCES_PATH + "/41").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EDIT_LOCKED_FOR_PAST_EVENT", "지난 행사는 고칠 수 없습니다. 복제해서 새 행사로 만들 수 있습니다."));

            willThrow(new NotFoundException(EventErrorCode.ENTRANCE_NOT_FOUND)).given(removeEntranceUseCase).remove(any());
            mockMvc.perform(delete(ENTRANCES_PATH + "/41").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("ENTRANCE_NOT_FOUND", "입구를 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("입구 목록을 보면 더한 순서대로 돌려준다")
        void listsEntrances() throws Exception {
            given(getEventEntrancesUseCase.get(new GetEventEntrancesQuery(PLANNER_IN_TWELVE_MONTHS, 7)))
                    .willReturn(new EventEntrancesResult(7, List.of(new EntranceResult(41, "정문"),
                            new EntranceResult(42, "2층 전용"), new EntranceResult(43, "VIP 입구"))));

            mockMvc.perform(get(ENTRANCES_PATH).with(loggedInAs(PLANNER)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"eventId": 7, "entrances": [{"entranceId": 41, "name": "정문"}, {"entranceId": 42, "name": "2층 전용"},
                                                         {"entranceId": 43, "name": "VIP 입구"}]}""", JsonCompareMode.STRICT));
        }
    }
}
