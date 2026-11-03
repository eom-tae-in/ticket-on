package com.ticketon.api.event;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.DuplicateEventCommand;
import com.ticketon.application.event.port.in.DuplicateEventUseCase;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventVisibility;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.TicketsPerPersonBasis;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 행사 복제 API — 경로의 주최자 · 행사 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 201과 복제본의 위저드 값, 경로 검증(400)과 유스케이스가
 * 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = EventDuplicationController.class)
@Import(SecurityConfiguration.class)
@DisplayName("행사 복제 API")
class EventDuplicationControllerTest {

    private static final String DUPLICATION_PATH = "/api/organizer/organizers/12/events/7/duplication";

    private static final AccountRef PLANNER = AccountRef.of(AccountKind.STAFF, 2);

    private static final ActingStaff PLANNER_IN_TWELVE_MONTHS = new ActingStaff(PLANNER, 12);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DuplicateEventUseCase duplicateEventUseCase;

    /** 작년 봄 정기공연(행사 7)을 2026-01-15 10:00(한국 시각)에 복제한 행사 8 — 작성 중, 1단계, 공개, 오픈 시각 비움, 조정한 예매 설정 그대로. */
    private static EventWizardResult copyResult() {
        return new EventWizardResult(8, EventStatus.DRAFT, EventWizardStep.BASIC_INFORMATION, Instant.parse("2026-01-15T01:00:00Z"),
                "aB3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG9", null,
                new EventWizardResult.BasicInformation("2025 봄 정기공연 〈열두 번째 달〉 (복사본)", EventType.PERFORMANCE, "연극", null,
                        "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", "○○아트홀", "서울특별시 종로구 대학로 12",
                        "지하 1층, 주차 불가", null, null),
                new EventWizardResult.BookingStep(EventVisibility.PUBLIC, false, null, Duration.ofHours(2), Duration.ofMinutes(30), 2,
                        6, TicketsPerPersonBasis.EVENT_TOTAL, SessionChoiceRestriction.ONE_SESSION_PER_DATE, true, 19,
                        List.of(new EventWizardResult.CancellationFeeTier(3, 0), new EventWizardResult.CancellationFeeTier(1, 30),
                                new EventWizardResult.CancellationFeeTier(0, 90)),
                        false));
    }

    @Test
    @DisplayName("복제하면 경로의 주최자 · 행사 id · 로그인한 운영진을 넘기고 201에 복제본의 위저드 값을 돌려준다 — 요청 본문은 없다")
    void duplicates() throws Exception {
        given(duplicateEventUseCase.duplicate(new DuplicateEventCommand(PLANNER_IN_TWELVE_MONTHS, 7))).willReturn(copyResult());

        mockMvc.perform(post(DUPLICATION_PATH).with(csrf()).with(loggedInAs(PLANNER)))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"eventId": 8, "status": "DRAFT",
                         "wizardProgress": {"step": "BASIC_INFORMATION", "stepNumber": 1, "stepCount": 7},
                         "lastModifiedAt": "2026-01-15T10:00:00+09:00", "publicAddress": "aB3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG9",
                         "posterUrl": null,
                         "basicInformation": {"title": "2025 봄 정기공연 〈열두 번째 달〉 (복사본)", "type": "PERFORMANCE",
                                              "categoryName": "연극", "genreName": null,
                                              "introduction": "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.",
                                              "venueName": "○○아트홀", "venueAddress": "서울특별시 종로구 대학로 12",
                                              "venueDetails": "지하 1층, 주차 불가", "mapLink": null, "publicContact": null},
                         "bookingSettings": {"visibility": "PUBLIC", "passwordSet": false, "bookingOpensAt": null,
                                             "bookingCloseLeadMinutes": 120, "checkinOpenLeadMinutes": 30, "maxSeatsPerSelection": 2,
                                             "ticketsPerPersonLimit": {"maxTickets": 6, "basis": "EVENT_TOTAL"},
                                             "sessionChoice": "ONE_SESSION_PER_DATE", "reentryAllowed": true, "minimumAge": 19,
                                             "cancellationFeeTiers": [{"fromDaysBefore": 3, "deductionRatePercent": 0},
                                                                      {"fromDaysBefore": 1, "deductionRatePercent": 30},
                                                                      {"fromDaysBefore": 0, "deductionRatePercent": 90}],
                                             "cancellationPolicyStandard": false}}""", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("행사 id가 1 이상의 정수가 아니면 400이고 유스케이스를 부르지 않는다")
    void rejectsInvalidEventId() throws Exception {
        mockMvc.perform(post("/api/organizer/organizers/12/events/0/duplication").with(csrf()).with(loggedInAs(PLANNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("eventId"));

        verifyNoInteractions(duplicateEventUseCase);
    }

    @Test
    @DisplayName("다른 주최자의 운영진이면 403, 활동 중이 아닌 주최자면 403, 없거나 다른 주최자의 행사면 404다")
    void rejections() throws Exception {
        willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF)).given(duplicateEventUseCase).duplicate(any());
        mockMvc.perform(post(DUPLICATION_PATH).with(csrf()).with(loggedInAs(PLANNER)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));

        willThrow(new PermissionDeniedException(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE)).given(duplicateEventUseCase).duplicate(any());
        mockMvc.perform(post(DUPLICATION_PATH).with(csrf()).with(loggedInAs(PLANNER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZER_NOT_ACTIVE"));

        willThrow(new NotFoundException(EventErrorCode.EVENT_NOT_FOUND)).given(duplicateEventUseCase).duplicate(any());
        mockMvc.perform(post(DUPLICATION_PATH).with(csrf()).with(loggedInAs(PLANNER)))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("EVENT_NOT_FOUND", "행사를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("운영진이 아닌 계정은 403, 로그인하지 않으면 401, CSRF 토큰이 없으면 403이다")
    void onlyStaffArea() throws Exception {
        mockMvc.perform(post(DUPLICATION_PATH).with(csrf()).with(loggedInAs(AccountKind.AUDIENCE, 1)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(DUPLICATION_PATH).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(DUPLICATION_PATH).with(loggedInAs(PLANNER)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(duplicateEventUseCase);
    }
}
