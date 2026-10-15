package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.GetOrganizerHomeQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerHomeUseCase;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.Affiliation;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ApplicationUnderReview;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ReceivedInvitation;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.RejectedApplication;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ReopenableOrganizer;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 내 주최 홈 상태 API — 운영진 영역인지, 결과가 응답 모양(한국 시각, 역할 이름)으로 바뀌는지 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OrganizerHomeController.class)
@Import(SecurityConfiguration.class)
@DisplayName("내 주최 홈 상태 API")
class OrganizerHomeControllerTest {

    private static final String HOME_PATH = "/api/organizer/home";

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 1);

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant REVIEWED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOrganizerHomeUseCase getOrganizerHomeUseCase;

    @Test
    @DisplayName("소속도 신청도 없으면 빈 목록과 null이다 — 화면은 '새 주최자 신청'과 '받은 초대'를 보인다")
    void noAffiliation() throws Exception {
        given(getOrganizerHomeUseCase.getHome(new GetOrganizerHomeQuery(JUNGHOON)))
                .willReturn(new OrganizerHomeResult(List.of(), null, null, List.of(), List.of()));

        mockMvc.perform(get(HOME_PATH).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"affiliations": [], "applicationUnderReview": null, "rejectedApplication": null,
                         "receivedInvitations": [], "reopenableOrganizers": []}""", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("심사 중인 신청을 한국 시각으로 돌려준다")
    void applicationUnderReview() throws Exception {
        given(getOrganizerHomeUseCase.getHome(any())).willReturn(new OrganizerHomeResult(List.of(),
                new ApplicationUnderReview(3, "극단 열두달", SUBMITTED_AT), null, List.of(), List.of()));

        mockMvc.perform(get(HOME_PATH).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"affiliations": [],
                         "applicationUnderReview": {"applicationId": 3, "organizerName": "극단 열두달",
                                                    "submittedAt": "2026-10-15T10:00:00+09:00"},
                         "rejectedApplication": null, "receivedInvitations": [], "reopenableOrganizers": []}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("반려된 신청은 반려 일시와 사유를 함께 돌려준다")
    void rejectedApplication() throws Exception {
        given(getOrganizerHomeUseCase.getHome(any())).willReturn(new OrganizerHomeResult(List.of(), null,
                new RejectedApplication(3, "극단 열두달", SUBMITTED_AT, REVIEWED_AT, "연락이 닿지 않음"), List.of(), List.of()));

        mockMvc.perform(get(HOME_PATH).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"affiliations": [], "applicationUnderReview": null,
                         "rejectedApplication": {"applicationId": 3, "organizerName": "극단 열두달",
                                                 "submittedAt": "2026-10-15T10:00:00+09:00",
                                                 "rejectedAt": "2026-10-17T15:00:00+09:00",
                                                 "rejectionReason": "연락이 닿지 않음"},
                         "receivedInvitations": [], "reopenableOrganizers": []}""", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("소속은 역할 이름과 함께, 받은 초대와 다시 열 수 있는 주최자는 id·이름으로 돌려준다")
    void affiliationsInvitationsAndReopenable() throws Exception {
        given(getOrganizerHomeUseCase.getHome(any())).willReturn(new OrganizerHomeResult(
                List.of(new Affiliation(12, "극단 열두달", StaffRole.OWNER, REVIEWED_AT),
                        new Affiliation(30, "늦여름", StaffRole.OPERATOR, REVIEWED_AT)),
                null, null,
                List.of(new ReceivedInvitation(41, "새 극단")),
                List.of(new ReopenableOrganizer(8, "극단 봄날"))));

        mockMvc.perform(get(HOME_PATH).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"affiliations": [
                            {"organizerId": 12, "organizerName": "극단 열두달", "role": "OWNER",
                             "since": "2026-10-17T15:00:00+09:00"},
                            {"organizerId": 30, "organizerName": "늦여름", "role": "OPERATOR",
                             "since": "2026-10-17T15:00:00+09:00"}],
                         "applicationUnderReview": null, "rejectedApplication": null,
                         "receivedInvitations": [{"organizerId": 41, "organizerName": "새 극단"}],
                         "reopenableOrganizers": [{"organizerId": 8, "organizerName": "극단 봄날"}]}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get(HOME_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"AUDIENCE", "ADMIN", "CHECKIN"})
    @DisplayName("운영진이 아닌 계정으로는 403이다")
    void otherAccountKindIsDenied(AccountKind kind) throws Exception {
        mockMvc.perform(get(HOME_PATH).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verify(getOrganizerHomeUseCase, never()).getHome(any());
    }
}
