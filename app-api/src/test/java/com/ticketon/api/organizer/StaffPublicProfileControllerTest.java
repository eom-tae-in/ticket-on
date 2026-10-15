package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.GetStaffPublicProfileQuery;
import com.ticketon.application.organizer.port.in.GetStaffPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.StaffPublicProfileResult;
import com.ticketon.application.organizer.port.in.StaffPublicProfileResult.ParticipatedOrganizer;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
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

/**
 * 운영진 프로필 API — 로그인 없이 열리는지, 응답 모양(한국 시각, 역할 이름, 활동 종료)과 실명 · 이메일 칸이 없는지, 없는 프로필의 404를 본다.
 * 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = StaffPublicProfileController.class)
@Import(SecurityConfiguration.class)
@DisplayName("운영진 프로필 API — 공개")
class StaffPublicProfileControllerTest {

    private static final String JUNGHOON_PROFILE_PATH = "/api/public/staff-profiles/7";

    /** docs/01 "운영진 프로필" 예시 — 조용한고래 / 극단에서 연출하고 있습니다 / 극단 열두달 · 소유자 · 2019~ / 늦여름 · 운영자 · 2023~2024. */
    private static final StaffPublicProfileResult JUNGHOON_PROFILE = new StaffPublicProfileResult("조용한고래",
            "극단에서 연출하고 있습니다", "조", 7, List.of(
                    new ParticipatedOrganizer(12, "극단 열두달", StaffRole.OWNER,
                            KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0)), null, false),
                    new ParticipatedOrganizer(30, "늦여름", StaffRole.OPERATOR,
                            KoreanTime.toInstant(LocalDateTime.of(2023, 5, 1, 10, 0)),
                            KoreanTime.toInstant(LocalDateTime.of(2024, 11, 30, 18, 0)), true)));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetStaffPublicProfileUseCase getStaffPublicProfileUseCase;

    @Test
    @DisplayName("로그인 없이 닉네임 · 소개 · 첫 글자 표시 · 참여한 주최자(이름 · 역할 · 기간 · 활동 종료)를 돌려준다 — 실명 · 이메일 칸은 없다")
    void returnsProfileWithoutLogin() throws Exception {
        given(getStaffPublicProfileUseCase.getProfile(new GetStaffPublicProfileQuery(7))).willReturn(JUNGHOON_PROFILE);

        mockMvc.perform(get(JUNGHOON_PROFILE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"nickname": "조용한고래", "introduction": "극단에서 연출하고 있습니다",
                         "avatar": {"initial": "조", "paletteIndex": 7},
                         "participatedOrganizers": [
                            {"organizerId": 12, "organizerName": "극단 열두달", "role": "OWNER",
                             "startedAt": "2019-03-04T10:00:00+09:00", "endedAt": null, "activityEnded": false},
                            {"organizerId": 30, "organizerName": "늦여름", "role": "OPERATOR",
                             "startedAt": "2023-05-01T10:00:00+09:00", "endedAt": "2024-11-30T18:00:00+09:00",
                             "activityEnded": true}]}""", JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @DisplayName("소개를 적지 않았고 참여한 주최자가 모두 빠졌으면 introduction은 null, 목록은 빈 배열이다")
    void emptyIntroductionAndList() throws Exception {
        given(getStaffPublicProfileUseCase.getProfile(any()))
                .willReturn(new StaffPublicProfileResult("조용한고래", null, "조", 7, List.of()));

        mockMvc.perform(get(JUNGHOON_PROFILE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"nickname": "조용한고래", "introduction": null, "avatar": {"initial": "조", "paletteIndex": 7},
                         "participatedOrganizers": []}""", JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AccountKind.class)
    @DisplayName("어느 계정으로 로그인해 있어도 똑같이 열린다 — 공개 영역")
    void openToAnyAccount(AccountKind kind) throws Exception {
        given(getStaffPublicProfileUseCase.getProfile(any())).willReturn(JUNGHOON_PROFILE);

        mockMvc.perform(get(JUNGHOON_PROFILE_PATH).with(loggedInAs(kind, 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("조용한고래"));
    }

    @Test
    @DisplayName("프로필이 없으면(소속된 적 없음 · 탈퇴 · 없는 계정) 404 STAFF_PROFILE_NOT_FOUND다")
    void notFound() throws Exception {
        given(getStaffPublicProfileUseCase.getProfile(any()))
                .willThrow(new NotFoundException(OrganizerErrorCode.STAFF_PROFILE_NOT_FOUND));

        mockMvc.perform(get(JUNGHOON_PROFILE_PATH))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("STAFF_PROFILE_NOT_FOUND", "운영진 프로필을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("계정 id가 1보다 작거나 숫자가 아니면 400이고 유스케이스를 부르지 않는다")
    void rejectsInvalidId() throws Exception {
        mockMvc.perform(get("/api/public/staff-profiles/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/public/staff-profiles/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getStaffPublicProfileUseCase);
    }
}
