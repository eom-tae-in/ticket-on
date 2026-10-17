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
import com.ticketon.application.event.port.in.AddAllDayDateCommand;
import com.ticketon.application.event.port.in.AddAllDayDateUseCase;
import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.AddTimedSessionUseCase;
import com.ticketon.application.event.port.in.ChangeDateOperatingHoursCommand;
import com.ticketon.application.event.port.in.ChangeDateOperatingHoursUseCase;
import com.ticketon.application.event.port.in.ChangeSessionDateTimeCommand;
import com.ticketon.application.event.port.in.ChangeSessionDateTimeUseCase;
import com.ticketon.application.event.port.in.ChangeSessionPerformersCommand;
import com.ticketon.application.event.port.in.ChangeSessionPerformersUseCase;
import com.ticketon.application.event.port.in.ChangeSessionPriceAdjustmentCommand;
import com.ticketon.application.event.port.in.ChangeSessionPriceAdjustmentUseCase;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.port.in.GetEventScheduleQuery;
import com.ticketon.application.event.port.in.GetEventScheduleUseCase;
import com.ticketon.application.event.port.in.OperatingHoursResult;
import com.ticketon.application.event.port.in.RemoveScheduleDateCommand;
import com.ticketon.application.event.port.in.RemoveScheduleDateUseCase;
import com.ticketon.application.event.port.in.RemoveSessionCommand;
import com.ticketon.application.event.port.in.RemoveSessionUseCase;
import com.ticketon.application.event.port.in.ScheduleDateResult;
import com.ticketon.application.event.port.in.ScheduleSessionResult;
import com.ticketon.application.event.port.in.SessionPriceAdjustmentResult;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPerformers;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.SessionPriceAdjustmentKind;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * 일정 API — 경로의 주최자 · 행사 · 회차 · 날짜와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 입력 검증(400 — 분 단위 시각, 시각의 차례, 회차
 * 방식과 운영 시간, 가격 조정 칸)과 응답 모양("HH:mm"), 유스케이스가 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다. 문서의 "3/14 15:00 ·
 * 19:00 회차"(docs/02), "10:00~18:00"(docs/02), "김배우 · 박배우"(docs/02), "+10,000원 · -20%"(docs/05).
 */
@WebMvcTest(controllers = EventScheduleController.class)
@Import(SecurityConfiguration.class)
@DisplayName("일정 API (위저드 2단계)")
class EventScheduleControllerTest {

    private static final String SCHEDULE_PATH = "/api/organizer/organizers/12/events/7/schedule";

    private static final AccountRef PLANNER = AccountRef.of(AccountKind.STAFF, 2);

    private static final ActingStaff PLANNER_IN_TWELVE_MONTHS = new ActingStaff(PLANNER, 12);

    private static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);

    private static final LocalDate MAY_3 = LocalDate.of(2026, 5, 3);

    private static final OperatingHours TEN_TO_SIX = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));

    /** 봄 정기공연 — 3/14 15:00~17:00(조정 없음), 19:00(종료 비움, 김배우 · 박배우, +10,000원). */
    private static final EventScheduleResult SPRING_PLAY = new EventScheduleResult(7, SessionMode.TIMED, null, List.of(
            new ScheduleDateResult(MARCH_14, null, null, List.of(
                    new ScheduleSessionResult(101, LocalTime.of(15, 0), LocalTime.of(17, 0), null,
                            new SessionPriceAdjustmentResult(SessionPriceAdjustmentKind.NONE, null, null), false),
                    new ScheduleSessionResult(102, LocalTime.of(19, 0), null, "김배우 · 박배우",
                            new SessionPriceAdjustmentResult(SessionPriceAdjustmentKind.AMOUNT, 10_000L, null), false)))));

    /** 늦여름 페스티벌 — 기본 10:00~18:00, 5/3은 10:00~20:00(마지막 입장 19:00)으로 덮어씀, 프리뷰 -20%. */
    private static final EventScheduleResult FESTIVAL = new EventScheduleResult(7, SessionMode.ALL_DAY,
            new OperatingHoursResult(LocalTime.of(10, 0), LocalTime.of(18, 0), null), List.of(
            new ScheduleDateResult(MAY_3, new OperatingHoursResult(LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(19, 0)),
                    new OperatingHoursResult(LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(19, 0)), List.of(
                    new ScheduleSessionResult(201, null, null, null,
                            new SessionPriceAdjustmentResult(SessionPriceAdjustmentKind.PERCENT, null, -20), false)))));

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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).with(loggedInAs(PLANNER)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    @DisplayName("일정 보기")
    class Read {

        @Test
        @DisplayName("회차형 일정은 날짜 아래 회차를 시작 시각 순으로, 시각은 HH:mm으로 돌려준다 — 비운 칸은 null")
        void timedSchedule() throws Exception {
            given(getEventScheduleUseCase.get(new GetEventScheduleQuery(PLANNER_IN_TWELVE_MONTHS, 7))).willReturn(SPRING_PLAY);

            mockMvc.perform(get(SCHEDULE_PATH).with(loggedInAs(PLANNER)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"eventId": 7, "sessionMode": "TIMED", "defaultOperatingHours": null, "dates": [
                              {"date": "2026-03-14", "operatingHours": null, "operatingHoursOverride": null, "sessions": [
                                {"sessionId": 101, "startTime": "15:00", "endTime": "17:00", "performers": null,
                                 "priceAdjustment": {"kind": "NONE", "amount": null, "percent": null}, "cancelled": false},
                                {"sessionId": 102, "startTime": "19:00", "endTime": null, "performers": "김배우 · 박배우",
                                 "priceAdjustment": {"kind": "AMOUNT", "amount": 10000, "percent": null}, "cancelled": false}]}]}""",
                            JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("종일형 일정은 기본 운영 시간과 날짜별로 실제 쓰는 운영 시간 · 덮어쓴 운영 시간을 돌려준다")
        void allDaySchedule() throws Exception {
            given(getEventScheduleUseCase.get(any())).willReturn(FESTIVAL);

            mockMvc.perform(get(SCHEDULE_PATH).with(loggedInAs(PLANNER)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"eventId": 7, "sessionMode": "ALL_DAY",
                             "defaultOperatingHours": {"openTime": "10:00", "closeTime": "18:00", "lastEntryTime": null},
                             "dates": [{"date": "2026-05-03",
                               "operatingHours": {"openTime": "10:00", "closeTime": "20:00", "lastEntryTime": "19:00"},
                               "operatingHoursOverride": {"openTime": "10:00", "closeTime": "20:00", "lastEntryTime": "19:00"},
                               "sessions": [{"sessionId": 201, "startTime": null, "endTime": null, "performers": null,
                                 "priceAdjustment": {"kind": "PERCENT", "amount": null, "percent": -20}, "cancelled": false}]}]}""",
                            JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("회차 방식을 고르기 전에는 회차 방식이 null이고 날짜가 없다")
        void notChosen() throws Exception {
            given(getEventScheduleUseCase.get(any())).willReturn(EventScheduleResult.notChosen(7));

            mockMvc.perform(get(SCHEDULE_PATH).with(loggedInAs(PLANNER)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"eventId": 7, "sessionMode": null, "defaultOperatingHours": null, "dates": []}""",
                            JsonCompareMode.STRICT));
        }
    }

    @Nested
    @DisplayName("회차 방식")
    class SessionModeChoice {

        @Test
        @DisplayName("회차형은 운영 시간 없이, 종일형은 기본 운영 시간과 함께 넘긴다")
        void choosesMode() throws Exception {
            given(chooseSessionModeUseCase.choose(any())).willReturn(SPRING_PLAY);

            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), "{\"sessionMode\": \"TIMED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionMode").value("TIMED"));
            then(chooseSessionModeUseCase).should()
                    .choose(new ChooseSessionModeCommand(PLANNER_IN_TWELVE_MONTHS, 7, SessionMode.TIMED, null));

            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), """
                            {"sessionMode": "ALL_DAY", "defaultOperatingHours": {"openTime": "10:00", "closeTime": "18:00"}}"""))
                    .andExpect(status().isOk());
            then(chooseSessionModeUseCase).should()
                    .choose(new ChooseSessionModeCommand(PLANNER_IN_TWELVE_MONTHS, 7, SessionMode.ALL_DAY, TEN_TO_SIX));
        }

        @Test
        @DisplayName("종일형인데 기본 운영 시간이 없거나, 회차형인데 있으면 400이다 — 유스케이스를 부르지 않는다")
        void hoursMustMatchMode() throws Exception {
            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), "{\"sessionMode\": \"ALL_DAY\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'defaultOperatingHours')].message")
                            .value("종일형은 기본 운영 시간을 입력해 주세요."));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), """
                            {"sessionMode": "TIMED", "defaultOperatingHours": {"openTime": "10:00", "closeTime": "18:00"}}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'defaultOperatingHours')].message")
                            .value("기본 운영 시간은 종일형을 고를 때만 입력합니다."));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), "{\"sessionMode\": null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'sessionMode')].message").value("회차 방식을 골라 주세요."));

            verifyNoInteractions(chooseSessionModeUseCase);
        }

        @Test
        @DisplayName("기본 운영 시간의 칸 — 초가 붙은 시각, 운영 시작보다 이른 종료, 운영 종료보다 늦은 마지막 입장은 그 칸의 400이다")
        void defaultHoursRules() throws Exception {
            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), """
                            {"sessionMode": "ALL_DAY", "defaultOperatingHours": {"openTime": "10:00:30", "closeTime": null}}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'defaultOperatingHours.openTime')].message")
                            .value("시각은 분 단위로 입력해 주세요. 예: 19:00"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'defaultOperatingHours.closeTime')].message")
                            .value("운영 종료 시각을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(2));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), """
                            {"sessionMode": "ALL_DAY", "defaultOperatingHours": {"openTime": "18:00", "closeTime": "02:00"}}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'defaultOperatingHours.closeTime')].message")
                            .value("운영 종료는 운영 시작보다 늦게 입력해 주세요. 자정을 넘겨 운영하는 날은 아직 받지 않습니다."));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), """
                            {"sessionMode": "ALL_DAY",
                             "defaultOperatingHours": {"openTime": "10:00", "closeTime": "18:00", "lastEntryTime": "18:30"}}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'defaultOperatingHours.lastEntryTime')].message")
                            .value("마지막 입장은 운영 시작보다 늦고 운영 종료보다 늦지 않게 입력해 주세요."));

            verifyNoInteractions(chooseSessionModeUseCase);
        }

        @Test
        @DisplayName("날짜가 있는데 회차 방식을 바꾸면 유스케이스가 막아 409다")
        void modeLockedByDates() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.SESSION_MODE_LOCKED_BY_DATES)).given(chooseSessionModeUseCase).choose(any());

            mockMvc.perform(json(put(SCHEDULE_PATH + "/session-mode"), "{\"sessionMode\": \"TIMED\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("SESSION_MODE_LOCKED_BY_DATES", "회차 방식을 바꾸려면 먼저 날짜를 모두 지워 주세요."));
        }
    }

    @Nested
    @DisplayName("날짜 · 회차 추가")
    class Addition {

        @Test
        @DisplayName("회차형 3/14 19:00~21:00을 더하면 날짜 · 시각을 넘기고 201에 일정을 돌려준다 — 초가 0인 \"19:00:00\"도 받는다")
        void addsTimedSession() throws Exception {
            given(addTimedSessionUseCase.add(any())).willReturn(SPRING_PLAY);

            mockMvc.perform(json(post(SCHEDULE_PATH + "/sessions"), """
                            {"date": "2026-03-14", "startTime": "19:00:00", "endTime": "21:00"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dates[0].sessions[1].sessionId").value(102));
            then(addTimedSessionUseCase).should().add(new AddTimedSessionCommand(PLANNER_IN_TWELVE_MONTHS, 7, MARCH_14,
                    TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0))));
        }

        @Test
        @DisplayName("빈 날짜 · 시작 시각, 초가 붙은 시각, 시작보다 이른 종료는 칸마다 400이다 (확인 필요 #41)")
        void rejectsInvalidTimes() throws Exception {
            mockMvc.perform(json(post(SCHEDULE_PATH + "/sessions"), "{\"date\": null, \"startTime\": null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'date')].message").value("회차 날짜를 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'startTime')].message").value("시작 시각을 입력해 주세요."));
            mockMvc.perform(json(post(SCHEDULE_PATH + "/sessions"), """
                            {"date": "2026-03-14", "startTime": "19:00:30", "endTime": "21:00:00.000001"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'startTime')].message").value("시각은 분 단위로 입력해 주세요. 예: 19:00"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'endTime')].message").value("시각은 분 단위로 입력해 주세요. 예: 19:00"))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(2));
            mockMvc.perform(json(post(SCHEDULE_PATH + "/sessions"), """
                            {"date": "2026-03-14", "startTime": "22:00", "endTime": "00:30"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'endTime')].message")
                            .value("종료 시각은 시작 시각보다 늦게 입력해 주세요. 자정을 넘겨 끝나는 회차는 아직 받지 않습니다."));
            mockMvc.perform(json(post(SCHEDULE_PATH + "/sessions"), """
                            {"date": "2026-03-14", "startTime": "25:00"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

            verifyNoInteractions(addTimedSessionUseCase);
        }

        @Test
        @DisplayName("종일형 날짜에 회차를 더하거나 같은 시각의 회차가 있거나 회차 방식을 고르지 않았으면 유스케이스가 막아 409다")
        void ruleViolationsAreConflicts() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE)).given(addTimedSessionUseCase).add(any());
            mockMvc.perform(json(post(SCHEDULE_PATH + "/sessions"), "{\"date\": \"2026-05-03\", \"startTime\": \"10:00\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("ALL_DAY_SINGLE_SESSION_PER_DATE",
                            "종일형은 날짜마다 회차가 하나입니다. 시간대를 나누려면 회차 방식을 회차형으로 골라 주세요."));

            willThrow(new RuleViolationException(EventErrorCode.SESSION_MODE_NOT_CHOSEN)).given(addAllDayDateUseCase).add(any());
            mockMvc.perform(json(post(SCHEDULE_PATH + "/dates"), "{\"date\": \"2026-05-03\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("SESSION_MODE_NOT_CHOSEN", "회차 방식을 먼저 골라 주세요."));

            willThrow(new RuleViolationException(EventErrorCode.TIMED_DATE_NEEDS_SESSION)).given(addAllDayDateUseCase).add(any());
            mockMvc.perform(json(post(SCHEDULE_PATH + "/dates"), "{\"date\": \"2026-05-03\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("TIMED_DATE_NEEDS_SESSION", "회차형은 날짜만 따로 추가하지 않습니다. 회차를 추가하면 그 날짜가 생깁니다."));
        }

        @Test
        @DisplayName("종일형 날짜를 더하면 날짜를 넘기고 201에 일정을 돌려준다")
        void addsAllDayDate() throws Exception {
            given(addAllDayDateUseCase.add(new AddAllDayDateCommand(PLANNER_IN_TWELVE_MONTHS, 7, MAY_3))).willReturn(FESTIVAL);

            mockMvc.perform(json(post(SCHEDULE_PATH + "/dates"), "{\"date\": \"2026-05-03\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dates[0].sessions[0].sessionId").value(201));
        }
    }

    @Nested
    @DisplayName("회차 일시 · 출연 · 가격 조정 · 삭제")
    class SessionChanges {

        @Test
        @DisplayName("회차 일시는 경로의 회차와 날짜 · 시각을 넘긴다 — 종일형은 시각을 비워 보낸다")
        void changesDateTime() throws Exception {
            given(changeSessionDateTimeUseCase.change(any())).willReturn(SPRING_PLAY);

            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/date-time"), """
                            {"date": "2026-03-15", "startTime": "20:00", "endTime": "22:00"}"""))
                    .andExpect(status().isOk());
            then(changeSessionDateTimeUseCase).should().change(new ChangeSessionDateTimeCommand(PLANNER_IN_TWELVE_MONTHS, 7, 102,
                    LocalDate.of(2026, 3, 15), LocalTime.of(20, 0), LocalTime.of(22, 0)));

            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/201/date-time"), "{\"date\": \"2026-05-10\"}"))
                    .andExpect(status().isOk());
            then(changeSessionDateTimeUseCase).should().change(new ChangeSessionDateTimeCommand(PLANNER_IN_TWELVE_MONTHS, 7, 201,
                    LocalDate.of(2026, 5, 10), null, null));
        }

        @Test
        @DisplayName("회차형인데 시작 시각이 없거나 시작 1주일 전이 지났으면 409, 없는 회차는 404다")
        void dateTimeRejections() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.SESSION_START_TIME_REQUIRED)).given(changeSessionDateTimeUseCase)
                    .change(any());
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/date-time"), "{\"date\": \"2026-03-15\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("SESSION_START_TIME_REQUIRED", "회차형 회차는 시작 시각을 입력해 주세요."));

            willThrow(new RuleViolationException(EventErrorCode.SESSION_TIME_EDIT_LOCKED)).given(changeSessionDateTimeUseCase)
                    .change(any());
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/date-time"), "{\"date\": \"2026-03-14\", \"startTime\": \"18:00\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("SESSION_TIME_EDIT_LOCKED", "회차 시작 1주일 전부터는 회차 시간을 바꿀 수 없습니다. 연기 신청을 이용해 주세요."));

            willThrow(new NotFoundException(EventErrorCode.SESSION_NOT_FOUND)).given(changeSessionDateTimeUseCase).change(any());
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/999/date-time"), "{\"date\": \"2026-03-14\", \"startTime\": \"18:00\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("SESSION_NOT_FOUND", "회차를 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("출연은 앞뒤 공백을 걷어 넘기고, 비우면 지운다(null) — 줄바꿈이 있거나 101자면 400이다")
        void changesPerformers() throws Exception {
            given(changeSessionPerformersUseCase.change(any())).willReturn(SPRING_PLAY);

            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/performers"), "{\"performers\": \" 김배우 · 박배우 \"}"))
                    .andExpect(status().isOk());
            then(changeSessionPerformersUseCase).should().change(new ChangeSessionPerformersCommand(PLANNER_IN_TWELVE_MONTHS, 7, 102,
                    new SessionPerformers("김배우 · 박배우")));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/performers"), "{\"performers\": \"　\"}"))
                    .andExpect(status().isOk());
            then(changeSessionPerformersUseCase).should()
                    .change(new ChangeSessionPerformersCommand(PLANNER_IN_TWELVE_MONTHS, 7, 102, null));

            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/performers"), "{\"performers\": \"김배우\\n박배우\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'performers')].message").value("출연은 줄바꿈 없이 100자 이하로 입력해 주세요."));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/performers"), "{\"performers\": \"%s\"}".formatted("배".repeat(101))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("가격 조정 +10,000원 · -20% · 조정 없음을 넘긴다 (docs/05 예시)")
        void changesPriceAdjustment() throws Exception {
            given(changeSessionPriceAdjustmentUseCase.change(any())).willReturn(SPRING_PLAY);

            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/price-adjustment"), "{\"kind\": \"AMOUNT\", \"amount\": 10000}"))
                    .andExpect(status().isOk());
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/101/price-adjustment"), "{\"kind\": \"PERCENT\", \"percent\": -20}"))
                    .andExpect(status().isOk());
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/101/price-adjustment"), "{\"kind\": \"NONE\"}"))
                    .andExpect(status().isOk());

            then(changeSessionPriceAdjustmentUseCase).should().change(new ChangeSessionPriceAdjustmentCommand(PLANNER_IN_TWELVE_MONTHS, 7,
                    102, SessionPriceAdjustment.amount(10_000)));
            then(changeSessionPriceAdjustmentUseCase).should().change(new ChangeSessionPriceAdjustmentCommand(PLANNER_IN_TWELVE_MONTHS, 7,
                    101, SessionPriceAdjustment.percent(-20)));
            then(changeSessionPriceAdjustmentUseCase).should().change(new ChangeSessionPriceAdjustmentCommand(PLANNER_IN_TWELVE_MONTHS, 7,
                    101, SessionPriceAdjustment.none()));
        }

        @Test
        @DisplayName("가격 조정 칸이 종류와 맞지 않거나 -100% 이하면 그 칸의 400이다 — 유스케이스를 부르지 않는다")
        void rejectsPriceAdjustmentShape() throws Exception {
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/price-adjustment"), "{\"kind\": \"AMOUNT\", \"percent\": 10}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')].message").value("조정 금액을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'percent')].message").value("조정 종류에 맞는 값만 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(2));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/price-adjustment"), "{\"kind\": \"PERCENT\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'percent')].message").value("조정 퍼센트를 입력해 주세요."));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/price-adjustment"), "{\"kind\": \"PERCENT\", \"percent\": -100}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'percent')].message").value("퍼센트 조정은 -100%보다 크게 입력해 주세요."));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/price-adjustment"), "{\"kind\": \"NONE\", \"amount\": 0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'amount')].message").value("조정 종류에 맞는 값만 입력해 주세요."));
            mockMvc.perform(json(put(SCHEDULE_PATH + "/sessions/102/price-adjustment"), "{\"amount\": 10000}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'kind')].message").value("가격 조정 종류를 골라 주세요."));

            verifyNoInteractions(changeSessionPriceAdjustmentUseCase);
        }

        @Test
        @DisplayName("회차를 지우면 204이고, 회차 id가 1 이상의 정수가 아니면 400이다")
        void removesSession() throws Exception {
            mockMvc.perform(delete(SCHEDULE_PATH + "/sessions/102").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNoContent());
            then(removeSessionUseCase).should().remove(new RemoveSessionCommand(PLANNER_IN_TWELVE_MONTHS, 7, 102));

            mockMvc.perform(delete(SCHEDULE_PATH + "/sessions/0").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("sessionId"));
        }
    }

    @Nested
    @DisplayName("날짜 — 운영 시간 · 삭제")
    class Dates {

        @Test
        @DisplayName("경로의 날짜에 운영 시간을 덮어쓰고, 지우면 null을 넘겨 204다")
        void overridesAndClearsOperatingHours() throws Exception {
            given(changeDateOperatingHoursUseCase.change(any())).willReturn(FESTIVAL);

            mockMvc.perform(json(put(SCHEDULE_PATH + "/dates/2026-05-03/operating-hours"), """
                            {"openTime": "10:00", "closeTime": "20:00", "lastEntryTime": "19:00"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dates[0].operatingHoursOverride.closeTime").value("20:00"));
            then(changeDateOperatingHoursUseCase).should().change(new ChangeDateOperatingHoursCommand(PLANNER_IN_TWELVE_MONTHS, 7, MAY_3,
                    OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(19, 0))));

            mockMvc.perform(delete(SCHEDULE_PATH + "/dates/2026-05-03/operating-hours").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNoContent());
            then(changeDateOperatingHoursUseCase).should()
                    .change(new ChangeDateOperatingHoursCommand(PLANNER_IN_TWELVE_MONTHS, 7, MAY_3, null));
        }

        @Test
        @DisplayName("회차형이면 409, 없는 날짜는 404, 날짜 꼴이 아니면 400이다")
        void rejections() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.OPERATING_HOURS_ONLY_FOR_ALL_DAY)).given(changeDateOperatingHoursUseCase)
                    .change(any());
            mockMvc.perform(json(put(SCHEDULE_PATH + "/dates/2026-03-14/operating-hours"), """
                            {"openTime": "10:00", "closeTime": "20:00"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("OPERATING_HOURS_ONLY_FOR_ALL_DAY",
                            "운영 시간은 종일형만 정합니다. 회차형은 회차마다 시작 · 종료 시각을 입력해 주세요."));

            willThrow(new NotFoundException(EventErrorCode.SCHEDULE_DATE_NOT_FOUND)).given(removeScheduleDateUseCase).remove(any());
            mockMvc.perform(delete(SCHEDULE_PATH + "/dates/2026-05-09").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("SCHEDULE_DATE_NOT_FOUND", "날짜를 찾을 수 없습니다."));

            mockMvc.perform(delete(SCHEDULE_PATH + "/dates/2026-13-01").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("date"));
        }

        @Test
        @DisplayName("날짜를 지우면 경로의 날짜를 넘기고 204다")
        void removesDate() throws Exception {
            mockMvc.perform(delete(SCHEDULE_PATH + "/dates/2026-05-03").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNoContent());
            then(removeScheduleDateUseCase).should().remove(new RemoveScheduleDateCommand(PLANNER_IN_TWELVE_MONTHS, 7, MAY_3));
        }
    }

    @Nested
    @DisplayName("권한")
    class Access {

        @Test
        @DisplayName("판매가 끝난 행사는 409, 다른 주최자의 운영진은 403, 남의 행사는 404다")
        void rejections() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED)).given(addAllDayDateUseCase).add(any());
            mockMvc.perform(json(post(SCHEDULE_PATH + "/dates"), "{\"date\": \"2026-05-03\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EDIT_LOCKED_AFTER_SALES_CLOSED", "판매가 끝난 행사는 입구 목록과 검표 계정만 고칠 수 있습니다."));

            willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF)).given(getEventScheduleUseCase).get(any());
            mockMvc.perform(get(SCHEDULE_PATH).with(loggedInAs(PLANNER)))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));

            willThrow(new NotFoundException(EventErrorCode.EVENT_NOT_FOUND)).given(removeSessionUseCase).remove(any());
            mockMvc.perform(delete(SCHEDULE_PATH + "/sessions/101").with(csrf()).with(loggedInAs(PLANNER)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("EVENT_NOT_FOUND", "행사를 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("운영진이 아닌 계정은 403, 로그인하지 않으면 401이다")
        void onlyStaffArea() throws Exception {
            mockMvc.perform(post(SCHEDULE_PATH + "/dates").with(csrf()).with(loggedInAs(AccountKind.AUDIENCE, 1))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"date\": \"2026-05-03\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(SCHEDULE_PATH)).andExpect(status().isUnauthorized());

            verifyNoInteractions(addAllDayDateUseCase, getEventScheduleUseCase);
        }
    }
}
