package com.ticketon.api.event;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.GetOrganizerEventListQuery;
import com.ticketon.application.event.port.in.GetOrganizerEventListUseCase;
import com.ticketon.application.event.port.in.OrganizerEventListResult;
import com.ticketon.application.event.port.in.OrganizerEventListResult.DraftEvent;
import com.ticketon.application.event.port.in.OrganizerEventListResult.ListedEvent;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 내 행사 목록 API — 경로의 주최자와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 응답 모양(시각은 한국 시각, 단계는 이름 · 번호), 유스케이스가
 * 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OrganizerEventListController.class)
@Import(SecurityConfiguration.class)
@DisplayName("내 행사 목록 API")
class OrganizerEventListControllerTest {

    private static final String EVENTS_PATH = "/api/organizer/organizers/12/events";

    private static final AccountRef PLANNER = AccountRef.of(AccountKind.STAFF, 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOrganizerEventListUseCase getOrganizerEventListUseCase;

    @Test
    @DisplayName("경로의 주최자로 목록을 읽어 세 구역을 돌려준다 — '4/7 단계 · 가격 설정까지 완료'의 단계 이름 · 번호와 이어서 할 단계, 한국 시각")
    void listsThreeSections() throws Exception {
        given(getOrganizerEventListUseCase.get(new GetOrganizerEventListQuery(new ActingStaff(PLANNER, 12)))).willReturn(
                new OrganizerEventListResult(
                        List.of(new DraftEvent(2L, "○○밴드 단독공연", "http://files.ticket-on.test/event-posters/a.jpg",
                                EventWizardStep.PRICING, EventWizardStep.BOOKING_SETTINGS,
                                KoreanTime.toInstant(LocalDateTime.of(2026, 10, 12, 21, 30)), LocalDate.of(2026, 12, 24),
                                LocalDate.of(2026, 12, 24), false),
                                new DraftEvent(5L, "여름 워크숍 공연", null, EventWizardStep.BASIC_INFORMATION, EventWizardStep.SCHEDULE,
                                        KoreanTime.toInstant(LocalDateTime.of(2026, 6, 1, 9, 0)), null, null, false)),
                        List.of(new ListedEvent(4L, "가을 정기공연", null, EventStatus.SALES_PAUSED, LocalDate.of(2026, 10, 24),
                                LocalDate.of(2026, 10, 25))),
                        List.of(new ListedEvent(1L, "2026 봄 정기공연 〈열두 번째 달〉", null, EventStatus.SETTLED,
                                LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 15)))));

        mockMvc.perform(get(EVENTS_PATH).with(loggedInAs(PLANNER)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"drafts": [
                           {"eventId": 2, "title": "○○밴드 단독공연", "posterUrl": "http://files.ticket-on.test/event-posters/a.jpg",
                            "wizardProgress": {"step": "PRICING", "stepNumber": 4, "stepCount": 7}, "resumeStep": "BOOKING_SETTINGS",
                            "lastModifiedAt": "2026-10-12T21:30:00+09:00", "firstDate": "2026-12-24", "lastDate": "2026-12-24",
                            "folded": false},
                           {"eventId": 5, "title": "여름 워크숍 공연", "posterUrl": null,
                            "wizardProgress": {"step": "BASIC_INFORMATION", "stepNumber": 1, "stepCount": 7}, "resumeStep": "SCHEDULE",
                            "lastModifiedAt": "2026-06-01T09:00:00+09:00", "firstDate": null, "lastDate": null, "folded": false}],
                         "inProgress": [
                           {"eventId": 4, "title": "가을 정기공연", "posterUrl": null, "status": "SALES_PAUSED", "firstDate": "2026-10-24",
                            "lastDate": "2026-10-25"}],
                         "past": [
                           {"eventId": 1, "title": "2026 봄 정기공연 〈열두 번째 달〉", "posterUrl": null, "status": "SETTLED",
                            "firstDate": "2026-03-14", "lastDate": "2026-03-15"}]}""", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 403이다")
    void outsiders() throws Exception {
        willThrow(new PermissionDeniedException(OrganizerErrorCode.NOT_ORGANIZER_STAFF)).given(getOrganizerEventListUseCase).get(any());

        mockMvc.perform(get(EVENTS_PATH).with(loggedInAs(PLANNER)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));
    }

    @Test
    @DisplayName("운영진이 아닌 계정은 403, 로그인하지 않으면 401이다")
    void onlyStaffArea() throws Exception {
        mockMvc.perform(get(EVENTS_PATH).with(loggedInAs(AccountKind.AUDIENCE, 1)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(EVENTS_PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getOrganizerEventListUseCase);
    }
}
