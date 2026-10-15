package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.AcceptStaffInvitationResult;
import com.ticketon.application.organizer.port.in.AcceptStaffInvitationUseCase;
import com.ticketon.application.organizer.port.in.AnswerStaffInvitationCommand;
import com.ticketon.application.organizer.port.in.DeclineStaffInvitationUseCase;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 받은 운영자 초대 수락 · 거절 API — 로그인한 운영진과 경로의 초대 id를 그대로 넘기는지, 결과의 응답 모양과 거절 때의 상태 코드를 본다.
 * 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = ReceivedStaffInvitationController.class)
@Import(SecurityConfiguration.class)
@DisplayName("받은 운영자 초대 API")
class ReceivedStaffInvitationControllerTest {

    private static final String INVITATION_PATH = "/api/organizer/staff-invitations/51";

    private static final AccountRef DOHYUN = AccountRef.of(AccountKind.STAFF, 4);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AcceptStaffInvitationUseCase acceptStaffInvitationUseCase;

    @MockitoBean
    private DeclineStaffInvitationUseCase declineStaffInvitationUseCase;

    @Test
    @DisplayName("수락하면 들어간 주최자를 한국 시각과 함께 돌려준다")
    void accepts() throws Exception {
        given(acceptStaffInvitationUseCase.accept(new AnswerStaffInvitationCommand(DOHYUN, 51)))
                .willReturn(new AcceptStaffInvitationResult(12, "극단 열두달",
                        KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 21, 0))));

        mockMvc.perform(post(INVITATION_PATH + "/acceptance").with(csrf()).with(loggedInAs(DOHYUN)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"organizerId": 12, "organizerName": "극단 열두달", "joinedAt": "2026-10-16T21:00:00+09:00"}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("다른 이메일로 온 초대거나 없는 초대면 404 STAFF_INVITATION_NOT_FOUND다")
    void unknownInvitationIsNotFound() throws Exception {
        given(acceptStaffInvitationUseCase.accept(any()))
                .willThrow(new NotFoundException(OrganizerErrorCode.STAFF_INVITATION_NOT_FOUND));

        mockMvc.perform(post(INVITATION_PATH + "/acceptance").with(csrf()).with(loggedInAs(DOHYUN)))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("STAFF_INVITATION_NOT_FOUND", "받은 초대를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("유효 기간이 지났거나 같은 초대에 먼저 답했으면 409다")
    void expiredOrAnsweredIsConflict() throws Exception {
        given(acceptStaffInvitationUseCase.accept(any()))
                .willThrow(new RuleViolationException(OrganizerErrorCode.STAFF_INVITATION_EXPIRED))
                .willThrow(new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED));

        mockMvc.perform(post(INVITATION_PATH + "/acceptance").with(csrf()).with(loggedInAs(DOHYUN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("STAFF_INVITATION_EXPIRED",
                        "유효 기간이 지난 초대입니다. 소유자에게 다시 초대해 달라고 요청해 주세요."));
        mockMvc.perform(post(INVITATION_PATH + "/acceptance").with(csrf()).with(loggedInAs(DOHYUN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("STAFF_INVITATION_ALREADY_ANSWERED", "이미 답한 초대입니다."));
    }

    @Test
    @DisplayName("거절하면 204이고 로그인한 운영진 · 초대 id를 그대로 넘긴다")
    void declines() throws Exception {
        mockMvc.perform(post(INVITATION_PATH + "/declination").with(csrf()).with(loggedInAs(DOHYUN)))
                .andExpect(status().isNoContent());

        verify(declineStaffInvitationUseCase).decline(new AnswerStaffInvitationCommand(DOHYUN, 51));
    }

    @Test
    @DisplayName("거절할 수 없는 초대면 그 오류로 답한다")
    void declineRejected() throws Exception {
        willThrow(new NotFoundException(OrganizerErrorCode.STAFF_INVITATION_NOT_FOUND))
                .given(declineStaffInvitationUseCase).decline(any());

        mockMvc.perform(post(INVITATION_PATH + "/declination").with(csrf()).with(loggedInAs(DOHYUN)))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("STAFF_INVITATION_NOT_FOUND", "받은 초대를 찾을 수 없습니다."));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0", "-3", "fifty-one"})
    @DisplayName("초대 id가 1 이상의 정수가 아니면 400 INVALID_INPUT이다")
    void rejectsInvalidInvitationId(String invitationId) throws Exception {
        mockMvc.perform(post("/api/organizer/staff-invitations/" + invitationId + "/acceptance").with(csrf())
                        .with(loggedInAs(DOHYUN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(acceptStaffInvitationUseCase);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"AUDIENCE", "ADMIN", "CHECKIN"})
    @DisplayName("운영진이 아닌 계정은 403이고, 로그인하지 않았으면 401이다")
    void staffOnly(AccountKind kind) throws Exception {
        mockMvc.perform(post(INVITATION_PATH + "/acceptance").with(csrf()).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
        mockMvc.perform(post(INVITATION_PATH + "/acceptance").with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(acceptStaffInvitationUseCase);
    }
}
