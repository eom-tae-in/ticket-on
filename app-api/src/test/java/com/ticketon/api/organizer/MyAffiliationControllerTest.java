package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.GetMyAffiliationsQuery;
import com.ticketon.application.organizer.port.in.GetMyAffiliationsUseCase;
import com.ticketon.application.organizer.port.in.MyAffiliationsResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** 내 소속 목록 API — 결과의 응답 모양(한국 시각, 역할 · 상태 이름)과 운영진만 열리는지를 본다. 유스케이스는 흉내 낸다. */
@WebMvcTest(controllers = MyAffiliationController.class)
@Import(SecurityConfiguration.class)
@DisplayName("내 소속 목록 API — 주최자 전환")
class MyAffiliationControllerTest {

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMyAffiliationsUseCase getMyAffiliationsUseCase;

    @Test
    @DisplayName("소속마다 주최자 id · 주최자명 · 역할 · 주최자 상태 · 소속 일시(한국 시각)를 돌려준다")
    void listsAffiliations() throws Exception {
        given(getMyAffiliationsUseCase.getMyAffiliations(new GetMyAffiliationsQuery(JUNGHOON)))
                .willReturn(new MyAffiliationsResult(List.of(
                        new MyAffiliationsResult.Affiliation(12, "극단 열두달", StaffRole.OWNER, OrganizerStatus.ACTIVE,
                                KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0))),
                        new MyAffiliationsResult.Affiliation(30, "늦여름", StaffRole.OPERATOR, OrganizerStatus.ACTIVE,
                                KoreanTime.toInstant(LocalDateTime.of(2023, 5, 1, 10, 0))))));

        mockMvc.perform(get("/api/organizer/affiliations").with(loggedInAs(JUNGHOON)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"affiliations": [
                            {"organizerId": 12, "organizerName": "극단 열두달", "role": "OWNER", "organizerStatus": "ACTIVE",
                             "since": "2019-03-04T10:00:00+09:00"},
                            {"organizerId": 30, "organizerName": "늦여름", "role": "OPERATOR", "organizerStatus": "ACTIVE",
                             "since": "2023-05-01T10:00:00+09:00"}]}""", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/organizer/affiliations"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"AUDIENCE", "ADMIN", "CHECKIN"})
    @DisplayName("운영진이 아닌 계정은 403이다")
    void staffOnly(AccountKind kind) throws Exception {
        mockMvc.perform(get("/api/organizer/affiliations").with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verifyNoInteractions(getMyAffiliationsUseCase);
    }
}
