package com.ticketon.api.event;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.CreateEventUseCase;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.EventCategoriesResult;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.GetEventCategoriesUseCase;
import com.ticketon.application.event.port.in.GetEventWizardQuery;
import com.ticketon.application.event.port.in.GetEventWizardUseCase;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.application.event.port.in.SaveEventBasicInformationUseCase;
import com.ticketon.application.event.port.in.SaveEventBookingSettingsCommand;
import com.ticketon.application.event.port.in.SaveEventBookingSettingsUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.EventPublicContact;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventVisibility;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.LeadTime;
import com.ticketon.domain.event.ReentrySetting;
import com.ticketon.domain.event.SeatsPerSelectionLimit;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.TicketsPerPersonBasis;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.policy.CancellationFeeTier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 행사 만들기 위저드 API — 경로의 주최자 · 행사 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 입력 검증(400)과 응답 모양, 유스케이스가 거절할
 * 때의 상태 코드를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = {OrganizerEventController.class, EventCategoryController.class})
@Import(SecurityConfiguration.class)
@DisplayName("행사 만들기 위저드 API")
class OrganizerEventControllerTest {

    private static final String EVENTS_PATH = "/api/organizer/organizers/12/events";

    private static final AccountRef PLANNER = AccountRef.of(AccountKind.STAFF, 2);

    private static final ActingStaff PLANNER_IN_TWELVE_MONTHS = new ActingStaff(PLANNER, 12);

    private static final String SPRING_PLAY_BODY = """
            {"title": " 2026 봄 정기공연 〈열두 번째 달〉 ", "type": "PERFORMANCE", "categoryName": "연극", "genreName": null,
             "introduction": "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", "venueName": "○○아트홀",
             "venueAddress": "서울특별시 종로구 대학로 12", "venueDetails": "지하 1층, 주차 불가",
             "mapLink": "https://map.example/place/12", "publicContact": "twelve-months@example.com"}""";

    private static final String DEFAULT_BOOKING_BODY = """
            {"visibility": "PUBLIC", "newPassword": null, "bookingOpensAt": null, "bookingCloseLeadMinutes": 60,
             "checkinOpenLeadMinutes": 60, "maxSeatsPerSelection": 4, "ticketsPerPersonLimit": null, "sessionChoice": "NONE",
             "reentryAllowed": false, "minimumAge": null,
             "cancellationFeeTiers": [{"fromDaysBefore": 10, "deductionRatePercent": 0}, {"fromDaysBefore": 7, "deductionRatePercent": 10},
                                      {"fromDaysBefore": 3, "deductionRatePercent": 20}, {"fromDaysBefore": 1, "deductionRatePercent": 30},
                                      {"fromDaysBefore": 0, "deductionRatePercent": 90}]}""";

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
    private GetEventCategoriesUseCase getEventCategoriesUseCase;

    private static EventBasicInformationItems springPlayItems() {
        return new EventBasicInformationItems(EventTitle.of("2026 봄 정기공연 〈열두 번째 달〉"), EventType.PERFORMANCE, "연극", null,
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"),
                "지하 1층, 주차 불가", ExternalLink.of("https://map.example/place/12"),
                EventPublicContact.of("twelve-months@example.com"));
    }

    /** 처음 값 그대로인 봄 정기공연의 위저드 값 — 행사 7, 2026-10-15 10:00(한국 시각)에 만들었다. */
    private static EventWizardResult springPlayResult() {
        return new EventWizardResult(7, EventStatus.DRAFT, EventWizardStep.BASIC_INFORMATION,
                Instant.parse("2026-10-15T01:00:00Z"), "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0",
                new EventWizardResult.BasicInformation("2026 봄 정기공연 〈열두 번째 달〉", EventType.PERFORMANCE, "연극", null,
                        "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", "○○아트홀", "서울특별시 종로구 대학로 12",
                        "지하 1층, 주차 불가", "https://map.example/place/12", "twelve-months@example.com"),
                new EventWizardResult.BookingStep(EventVisibility.PUBLIC, false, null, Duration.ofHours(1), Duration.ofHours(1), 4,
                        null, null, SessionChoiceRestriction.NONE, false, null,
                        List.of(new EventWizardResult.CancellationFeeTier(10, 0), new EventWizardResult.CancellationFeeTier(7, 10),
                                new EventWizardResult.CancellationFeeTier(3, 20), new EventWizardResult.CancellationFeeTier(1, 30),
                                new EventWizardResult.CancellationFeeTier(0, 90)),
                        true));
    }

    private static final String SPRING_PLAY_RESPONSE = """
            {"eventId": 7, "status": "DRAFT",
             "wizardProgress": {"step": "BASIC_INFORMATION", "stepNumber": 1, "stepCount": 7},
             "lastModifiedAt": "2026-10-15T10:00:00+09:00", "publicAddress": "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0",
             "basicInformation": {"title": "2026 봄 정기공연 〈열두 번째 달〉", "type": "PERFORMANCE", "categoryName": "연극",
                                  "genreName": null, "introduction": "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.",
                                  "venueName": "○○아트홀", "venueAddress": "서울특별시 종로구 대학로 12",
                                  "venueDetails": "지하 1층, 주차 불가", "mapLink": "https://map.example/place/12",
                                  "publicContact": "twelve-months@example.com"},
             "bookingSettings": {"visibility": "PUBLIC", "passwordSet": false, "bookingOpensAt": null,
                                 "bookingCloseLeadMinutes": 60, "checkinOpenLeadMinutes": 60, "maxSeatsPerSelection": 4,
                                 "ticketsPerPersonLimit": null, "sessionChoice": "NONE", "reentryAllowed": false,
                                 "minimumAge": null,
                                 "cancellationFeeTiers": [{"fromDaysBefore": 10, "deductionRatePercent": 0},
                                                          {"fromDaysBefore": 7, "deductionRatePercent": 10},
                                                          {"fromDaysBefore": 3, "deductionRatePercent": 20},
                                                          {"fromDaysBefore": 1, "deductionRatePercent": 30},
                                                          {"fromDaysBefore": 0, "deductionRatePercent": 90}],
                                 "cancellationPolicyStandard": true}}""";

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).with(loggedInAs(PLANNER)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    @DisplayName("행사 만들기 · 1단계 저장")
    class BasicInformation {

        @Test
        @DisplayName("만들면 경로의 주최자 · 로그인한 운영진 · 1단계 값을 넘기고 201에 위저드 값을 돌려준다 — 시각은 한국 시각")
        void creates() throws Exception {
            given(createEventUseCase.create(new CreateEventCommand(PLANNER_IN_TWELVE_MONTHS, springPlayItems())))
                    .willReturn(springPlayResult());

            mockMvc.perform(json(post(EVENTS_PATH), SPRING_PLAY_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(content().json(SPRING_PLAY_RESPONSE, JsonCompareMode.STRICT));
        }

        @Test
        @DisplayName("1단계를 저장하면 경로의 행사 id를 함께 넘긴다 — 비운 선택 칸은 적지 않은 것이다")
        void savesWithBlankOptionalItems() throws Exception {
            EventBasicInformationItems withoutOptionals = new EventBasicInformationItems(
                    EventTitle.of("2026 봄 정기공연 〈열두 번째 달〉"), EventType.PERFORMANCE, "연극", " ",
                    "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), "",
                    null, null);
            given(saveEventBasicInformationUseCase.save(
                    new SaveEventBasicInformationCommand(PLANNER_IN_TWELVE_MONTHS, 7, withoutOptionals)))
                    .willReturn(springPlayResult());

            mockMvc.perform(json(put(EVENTS_PATH + "/7/basic-information"), """
                            {"title": "2026 봄 정기공연 〈열두 번째 달〉", "type": "PERFORMANCE", "categoryName": "연극",
                             "genreName": " ", "introduction": "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.",
                             "venueName": "○○아트홀", "venueAddress": "서울특별시 종로구 대학로 12", "venueDetails": "",
                             "mapLink": " ", "publicContact": "　"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value(7));
        }

        @Test
        @DisplayName("필수 칸이 비었으면(전각 공백 포함) 칸마다 까닭을 담아 400이고 유스케이스를 부르지 않는다")
        void rejectsBlankRequiredItems() throws Exception {
            mockMvc.perform(json(post(EVENTS_PATH), """
                            {"title": "　", "type": null, "categoryName": "", "introduction": " ", "venueName": null,
                             "venueAddress": ""}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')].message").value("제목을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'type')].message").value("유형을 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'categoryName')].message").value("카테고리를 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'introduction')].message").value("소개를 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'venueName')].message").value("공연장 이름을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'venueAddress')].message").value("주소를 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(6));

            verifyNoInteractions(createEventUseCase);
        }

        @Test
        @DisplayName("제목 101자 · 장르명 21자 · http(s)가 아닌 지도 링크는 400이다 — 한글 · 이모지도 한 자로 센다")
        void rejectsTooLongAndMalformedItems() throws Exception {
            String title = "가".repeat(100) + "😀";
            String genre = "낭".repeat(21);

            mockMvc.perform(json(post(EVENTS_PATH), SPRING_PLAY_BODY
                            .replace("\" 2026 봄 정기공연 〈열두 번째 달〉 \"", "\"" + title + "\"")
                            .replace("\"genreName\": null", "\"genreName\": \"" + genre + "\"")
                            .replace("https://map.example/place/12", "map.example/place/12")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')].message").value("제목은 100자 이하로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'genreName')].message").value("장르명은 20자 이하로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'mapLink')].message")
                            .value("http:// 또는 https://로 시작하는 주소를 입력해 주세요."));

            verifyNoInteractions(createEventUseCase);
        }

        @Test
        @DisplayName("100자 제목(이모지 한 자 포함)은 받는다")
        void acceptsHundredCharacterTitle() throws Exception {
            given(createEventUseCase.create(any())).willReturn(springPlayResult());

            mockMvc.perform(json(post(EVENTS_PATH), SPRING_PLAY_BODY
                            .replace("\" 2026 봄 정기공연 〈열두 번째 달〉 \"", "\"" + "가".repeat(99) + "😀" + "\"")))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("유스케이스가 카테고리를 거절하면 409, 다른 주최자의 운영진이면 403, 다른 주최자의 행사면 404다")
        void rejections() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE)).given(createEventUseCase).create(any());
            mockMvc.perform(json(post(EVENTS_PATH), SPRING_PLAY_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EVENT_CATEGORY_NOT_IN_TYPE", "고른 유형에서 쓸 수 없는 카테고리입니다. 카테고리를 다시 골라 주세요."));

            willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF))
                    .given(saveEventBasicInformationUseCase).save(any());
            mockMvc.perform(json(put(EVENTS_PATH + "/7/basic-information"), SPRING_PLAY_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));

            willThrow(new NotFoundException(EventErrorCode.EVENT_NOT_FOUND)).given(getEventWizardUseCase).get(any());
            mockMvc.perform(get(EVENTS_PATH + "/7").with(loggedInAs(PLANNER)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("EVENT_NOT_FOUND", "행사를 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("위저드 값을 보면 경로의 주최자 · 행사 id를 넘기고 같은 모양을 돌려준다. 행사 id가 1 이상의 정수가 아니면 400이다")
        void getsWizard() throws Exception {
            given(getEventWizardUseCase.get(new GetEventWizardQuery(PLANNER_IN_TWELVE_MONTHS, 7))).willReturn(springPlayResult());

            mockMvc.perform(get(EVENTS_PATH + "/7").with(loggedInAs(PLANNER)))
                    .andExpect(status().isOk())
                    .andExpect(content().json(SPRING_PLAY_RESPONSE, JsonCompareMode.STRICT));
            mockMvc.perform(get(EVENTS_PATH + "/0").with(loggedInAs(PLANNER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("eventId"));
        }

        @Test
        @DisplayName("운영진이 아닌 계정은 403, 로그인하지 않으면 401이다")
        void onlyStaffArea() throws Exception {
            mockMvc.perform(post(EVENTS_PATH).with(csrf()).with(loggedInAs(AccountKind.AUDIENCE, 1))
                            .contentType(MediaType.APPLICATION_JSON).content(SPRING_PLAY_BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(EVENTS_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SPRING_PLAY_BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(createEventUseCase);
        }
    }

    @Nested
    @DisplayName("5단계 저장")
    class BookingSettingsStep {

        @Test
        @DisplayName("처음 값 그대로 보내면 처음 값의 예매 설정 · 기준표 취소 규정으로 넘긴다")
        void savesDefaults() throws Exception {
            given(saveEventBookingSettingsUseCase.save(new SaveEventBookingSettingsCommand(PLANNER_IN_TWELVE_MONTHS, 7,
                    EventVisibility.PUBLIC, null, BookingSettings.defaults(), CancellationPolicy.standard())))
                    .willReturn(springPlayResult());

            mockMvc.perform(json(put(EVENTS_PATH + "/7/booking-settings"), DEFAULT_BOOKING_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingSettings.maxSeatsPerSelection").value(4));
        }

        @Test
        @DisplayName("조정한 값 — 분 단위 앞섬, 한국 시각 오픈, 1인 총 매수, 연령 제한, 순서가 섞인 취소 규정 구간, 새 암호를 도메인 값으로 바꿔 넘긴다")
        void savesAdjustedValues() throws Exception {
            BookingSettings adjusted = new BookingSettings(Instant.parse("2027-03-01T05:00:00Z"), LeadTime.of(Duration.ofHours(2)),
                    LeadTime.of(Duration.ofMinutes(90)), SeatsPerSelectionLimit.of(6),
                    TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.PER_DATE), SessionChoiceRestriction.ONE_SESSION_PER_DATE,
                    ReentrySetting.ALLOWED, AgeRestriction.atLeast(19));
            CancellationPolicy fullRefundUntilThreeDaysBefore = CancellationPolicy.of(List.of(CancellationFeeTier.of(3, 0),
                    CancellationFeeTier.of(1, 30), CancellationFeeTier.of(0, 90)));
            given(saveEventBookingSettingsUseCase.save(new SaveEventBookingSettingsCommand(PLANNER_IN_TWELVE_MONTHS, 7,
                    EventVisibility.PASSWORD_PROTECTED, new EventPassword("0315"), adjusted, fullRefundUntilThreeDaysBefore)))
                    .willReturn(springPlayResult());

            mockMvc.perform(json(put(EVENTS_PATH + "/7/booking-settings"), """
                            {"visibility": "PASSWORD_PROTECTED", "newPassword": "0315", "bookingOpensAt": "2027-03-01T14:00:00+09:00",
                             "bookingCloseLeadMinutes": 120, "checkinOpenLeadMinutes": 90, "maxSeatsPerSelection": 6,
                             "ticketsPerPersonLimit": {"maxTickets": 4, "basis": "PER_DATE"}, "sessionChoice": "ONE_SESSION_PER_DATE",
                             "reentryAllowed": true, "minimumAge": 19,
                             "cancellationFeeTiers": [{"fromDaysBefore": 0, "deductionRatePercent": 90},
                                                      {"fromDaysBefore": 3, "deductionRatePercent": 0},
                                                      {"fromDaysBefore": 1, "deductionRatePercent": 30}]}"""))
                    .andExpect(status().isOk());

            then(saveEventBookingSettingsUseCase).should().save(any());
        }

        @Test
        @DisplayName("빠진 칸 · 범위를 벗어난 숫자는 칸마다 까닭을 담아 400이다")
        void rejectsMissingAndOutOfRangeValues() throws Exception {
            mockMvc.perform(json(put(EVENTS_PATH + "/7/booking-settings"), """
                            {"visibility": null, "bookingCloseLeadMinutes": -1, "checkinOpenLeadMinutes": null,
                             "maxSeatsPerSelection": 0, "ticketsPerPersonLimit": {"maxTickets": 0, "basis": null},
                             "sessionChoice": null, "reentryAllowed": null, "minimumAge": 0,
                             "cancellationFeeTiers": [{"fromDaysBefore": 0, "deductionRatePercent": 101}]}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'visibility')].message").value("공개 수준을 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'bookingCloseLeadMinutes')].message").value("0분 이상으로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'checkinOpenLeadMinutes')].message")
                            .value("검표 시작 시각을 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'maxSeatsPerSelection')].message").value("1석 이상으로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'ticketsPerPersonLimit.maxTickets')].message")
                            .value("1장 이상으로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'ticketsPerPersonLimit.basis')].message")
                            .value("1인 총 매수를 세는 범위를 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'sessionChoice')].message").value("회차 선택 제약을 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reentryAllowed')].message").value("재입장을 골라 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'minimumAge')].message")
                            .value("연령 제한은 만 1세 이상으로 입력해 주세요."))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'cancellationFeeTiers[0].deductionRatePercent')].message")
                            .value("공제율은 0~100 사이로 입력해 주세요."));

            verifyNoInteractions(saveEventBookingSettingsUseCase);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "[{\"fromDaysBefore\": 3, \"deductionRatePercent\": 0}]",
                "[{\"fromDaysBefore\": 0, \"deductionRatePercent\": 90}, {\"fromDaysBefore\": 0, \"deductionRatePercent\": 80}]",
                "[]"})
        @DisplayName("관람일 당일 구간이 없거나 같은 날 구간이 둘이거나 비었으면 cancellationFeeTiers 칸의 400이다")
        void rejectsMalformedTiers(String tiers) throws Exception {
            mockMvc.perform(json(put(EVENTS_PATH + "/7/booking-settings"),
                            DEFAULT_BOOKING_BODY.substring(0, DEFAULT_BOOKING_BODY.indexOf("\"cancellationFeeTiers\""))
                                    + "\"cancellationFeeTiers\": " + tiers + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("cancellationFeeTiers"))
                    .andExpect(jsonPath("$.fieldErrors.length()").value(1));

            verifyNoInteractions(saveEventBookingSettingsUseCase);
        }

        @Test
        @DisplayName("암호 보호가 아닌데 암호를 적으면 newPassword 칸의 400이다 — 말없이 버리지 않는다")
        void rejectsPasswordWithoutProtection() throws Exception {
            mockMvc.perform(json(put(EVENTS_PATH + "/7/booking-settings"),
                            DEFAULT_BOOKING_BODY.replace("\"visibility\": \"PUBLIC\", \"newPassword\": null",
                                    "\"visibility\": \"LINK_ONLY\", \"newPassword\": \"0315\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("암호는 공개 수준을 암호 보호로 고를 때만 입력합니다."));

            verifyNoInteractions(saveEventBookingSettingsUseCase);
        }

        @Test
        @DisplayName("기준보다 불리한 취소 규정은 409 CANCELLATION_POLICY_STRICTER_THAN_STANDARD다 — 7일 전부터 환불 불가")
        void rejectsStricterPolicy() throws Exception {
            mockMvc.perform(json(put(EVENTS_PATH + "/7/booking-settings"),
                            DEFAULT_BOOKING_BODY.substring(0, DEFAULT_BOOKING_BODY.indexOf("\"cancellationFeeTiers\""))
                                    + "\"cancellationFeeTiers\": [{\"fromDaysBefore\": 7, \"deductionRatePercent\": 100},"
                                    + " {\"fromDaysBefore\": 10, \"deductionRatePercent\": 0},"
                                    + " {\"fromDaysBefore\": 0, \"deductionRatePercent\": 100}]}"))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("CANCELLATION_POLICY_STRICTER_THAN_STANDARD",
                            "취소 규정은 기준보다 관객에게 불리하게 정할 수 없습니다. 모든 구간의 공제율을 기준 이하로 정해 주세요."));

            verifyNoInteractions(saveEventBookingSettingsUseCase);
        }

        @Test
        @DisplayName("유스케이스가 암호 없음 · 수정 범위로 거절하면 409다")
        void useCaseRejections() throws Exception {
            willThrow(new RuleViolationException(EventErrorCode.EVENT_PASSWORD_REQUIRED))
                    .given(saveEventBookingSettingsUseCase).save(any());

            mockMvc.perform(json(put(EVENTS_PATH + "/7/booking-settings"), DEFAULT_BOOKING_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("EVENT_PASSWORD_REQUIRED", "암호 보호 행사는 암호를 입력해 주세요."));
        }
    }

    @Test
    @DisplayName("정식 카테고리 목록은 유형마다의 이름과 \"기타\"를 돌려준다")
    void listsCategories() throws Exception {
        given(getEventCategoriesUseCase.get()).willReturn(EventCategoriesResult.of(List.of(
                new EventCategoriesResult.TypeCategories(EventType.PERFORMANCE, List.of("연극", "뮤지컬")),
                new EventCategoriesResult.TypeCategories(EventType.EXHIBITION, List.of("회화")),
                new EventCategoriesResult.TypeCategories(EventType.FESTIVAL, List.of()))));

        mockMvc.perform(get("/api/organizer/event-categories").with(loggedInAs(PLANNER)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"types": [{"type": "PERFORMANCE", "categoryNames": ["연극", "뮤지컬"]},
                                   {"type": "EXHIBITION", "categoryNames": ["회화"]},
                                   {"type": "FESTIVAL", "categoryNames": []}],
                         "otherCategoryName": "기타"}""", JsonCompareMode.STRICT));
    }
}
