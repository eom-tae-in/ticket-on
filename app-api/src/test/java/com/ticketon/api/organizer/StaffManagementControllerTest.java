package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.GetStaffListQuery;
import com.ticketon.application.organizer.port.in.GetStaffListUseCase;
import com.ticketon.application.organizer.port.in.InviteOperatorCommand;
import com.ticketon.application.organizer.port.in.InviteOperatorResult;
import com.ticketon.application.organizer.port.in.InviteOperatorUseCase;
import com.ticketon.application.organizer.port.in.LeaveOrganizerCommand;
import com.ticketon.application.organizer.port.in.LeaveOrganizerUseCase;
import com.ticketon.application.organizer.port.in.RemoveOperatorCommand;
import com.ticketon.application.organizer.port.in.RemoveOperatorUseCase;
import com.ticketon.application.organizer.port.in.StaffListResult;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimCommand;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimResult;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimUseCase;
import com.ticketon.application.organizer.port.in.TransferOwnershipCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipUseCase;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * 운영진 관리 API — 경로의 주최자 id와 로그인한 운영진을 그대로 유스케이스에 넘기는지, 요청 검증과 결과의 응답 모양(한국 시각, 역할 이름),
 * 유스케이스가 거절할 때의 상태 코드를 본다. 유스케이스는 흉내 낸다. 권한 판정 자체는 유스케이스 테스트와 통합 테스트가 본다.
 */
@WebMvcTest(controllers = StaffManagementController.class)
@Import(SecurityConfiguration.class)
@DisplayName("운영진 관리 API")
class StaffManagementControllerTest {

    private static final String ORGANIZER_PATH = "/api/organizer/organizers/12";

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 1);

    private static final ActingStaff JUNGHOON_IN_TWELVE_MONTHS = new ActingStaff(JUNGHOON, 12);

    private static final Instant INVITED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetStaffListUseCase getStaffListUseCase;

    @MockitoBean
    private InviteOperatorUseCase inviteOperatorUseCase;

    @MockitoBean
    private RemoveOperatorUseCase removeOperatorUseCase;

    @MockitoBean
    private LeaveOrganizerUseCase leaveOrganizerUseCase;

    @MockitoBean
    private TransferOwnershipUseCase transferOwnershipUseCase;

    @MockitoBean
    private SubmitOwnershipClaimUseCase submitOwnershipClaimUseCase;

    @Test
    @DisplayName("운영진 목록을 닉네임 · 역할 이름 · 한국 시각으로 돌려준다")
    void staffList() throws Exception {
        given(getStaffListUseCase.getStaffList(new GetStaffListQuery(JUNGHOON_IN_TWELVE_MONTHS)))
                .willReturn(new StaffListResult(List.of(
                        new StaffListResult.Member(1, "조용한고래", StaffRole.OWNER, INVITED_AT),
                        new StaffListResult.Member(4, "밤의기타", StaffRole.OPERATOR, INVITED_AT.plus(Duration.ofHours(35))))));

        mockMvc.perform(get(ORGANIZER_PATH + "/staff").with(loggedInAs(JUNGHOON)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"members": [
                            {"staffAccountId": 1, "nickname": "조용한고래", "role": "OWNER",
                             "since": "2026-10-15T10:00:00+09:00"},
                            {"staffAccountId": 4, "nickname": "밤의기타", "role": "OPERATOR",
                             "since": "2026-10-16T21:00:00+09:00"}]}""", JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("초대하면 201에 보낸 초대를 한국 시각으로 돌려주고, 이메일은 앞뒤 공백을 걷어 넘긴다")
    void invites() throws Exception {
        given(inviteOperatorUseCase.invite(new InviteOperatorCommand(JUNGHOON_IN_TWELVE_MONTHS,
                EmailAddress.of("dohyun@example.com"))))
                .willReturn(new InviteOperatorResult(51, "dohyun@example.com", INVITED_AT, INVITED_AT.plus(Duration.ofDays(7))));

        mockMvc.perform(invite("{\"email\": \"  dohyun@example.com \"}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"invitationId": 51, "inviteeEmail": "dohyun@example.com",
                         "invitedAt": "2026-10-15T10:00:00+09:00", "expiresAt": "2026-10-22T10:00:00+09:00"}""",
                        JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "　"})
    @DisplayName("이메일이 비었으면(전각 공백만 넣어도) 400 INVALID_INPUT이고 유스케이스를 부르지 않는다")
    void rejectsBlankEmail(String email) throws Exception {
        mockMvc.perform(invite("{\"email\": \"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("이메일을 입력해 주세요."));

        verifyNoInteractions(inviteOperatorUseCase);
    }

    @Test
    @DisplayName("이메일 모양이 아니면 400 INVALID_INPUT이다")
    void rejectsMalformedEmail() throws Exception {
        mockMvc.perform(invite("{\"email\": \"dohyun.example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("이메일 주소를 확인해 주세요."));

        verifyNoInteractions(inviteOperatorUseCase);
    }

    @Test
    @DisplayName("운영자가 초대해 유스케이스가 거절하면 403 OWNER_ONLY_ACTION이다")
    void operatorInviteIsForbidden() throws Exception {
        given(inviteOperatorUseCase.invite(any()))
                .willThrow(new PermissionDeniedException(OrganizerErrorCode.OWNER_ONLY_ACTION));

        mockMvc.perform(invite("{\"email\": \"dohyun@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("OWNER_ONLY_ACTION", "소유자만 할 수 있는 일입니다."));
    }

    @Test
    @DisplayName("이미 운영진이거나 같은 이메일로 기다리는 초대가 있으면 409다")
    void duplicateInviteIsConflict() throws Exception {
        given(inviteOperatorUseCase.invite(any()))
                .willThrow(new RuleViolationException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING));

        mockMvc.perform(invite("{\"email\": \"dohyun@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(errorBody("STAFF_INVITATION_ALREADY_PENDING",
                        "이미 초대를 보낸 이메일입니다. 받은 분의 답을 기다려 주세요."));
    }

    @Test
    @DisplayName("내보내면 204이고 경로의 주최자 · 운영진 계정 id를 그대로 넘긴다")
    void removesOperator() throws Exception {
        mockMvc.perform(post(ORGANIZER_PATH + "/staff/4/removal").with(csrf()).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isNoContent());

        verify(removeOperatorUseCase).remove(new RemoveOperatorCommand(JUNGHOON_IN_TWELVE_MONTHS, 4));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0", "-1", "four"})
    @DisplayName("내보낼 운영진 계정 id가 1 이상의 정수가 아니면 400 INVALID_INPUT이다")
    void rejectsInvalidStaffAccountId(String staffAccountId) throws Exception {
        mockMvc.perform(post(ORGANIZER_PATH + "/staff/" + staffAccountId + "/removal").with(csrf())
                        .with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(removeOperatorUseCase, never()).remove(any());
    }

    @Test
    @DisplayName("그 사이 먼저 나간 운영자를 내보내면 409 MEMBERSHIP_ALREADY_ENDED다")
    void removingAlreadyEndedIsConflict() throws Exception {
        willThrow(new ConflictException(OrganizerErrorCode.MEMBERSHIP_ALREADY_ENDED))
                .given(removeOperatorUseCase).remove(any());

        mockMvc.perform(post(ORGANIZER_PATH + "/staff/4/removal").with(csrf()).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("MEMBERSHIP_ALREADY_ENDED", "이미 운영진에서 빠졌습니다."));
    }

    @Test
    @DisplayName("소유자를 넘기면 204이고 경로의 주최자 · 받을 운영진 계정 id를 그대로 넘긴다")
    void transfersOwnership() throws Exception {
        mockMvc.perform(post(ORGANIZER_PATH + "/staff/2/ownership-transfer").with(csrf()).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isNoContent());

        verify(transferOwnershipUseCase).transfer(new TransferOwnershipCommand(JUNGHOON_IN_TWELVE_MONTHS, 2));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0", "-1", "two"})
    @DisplayName("받을 운영진 계정 id가 1 이상의 정수가 아니면 400 INVALID_INPUT이고 유스케이스를 부르지 않는다")
    void rejectsInvalidNewOwnerId(String staffAccountId) throws Exception {
        mockMvc.perform(post(ORGANIZER_PATH + "/staff/" + staffAccountId + "/ownership-transfer").with(csrf())
                        .with(loggedInAs(JUNGHOON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(transferOwnershipUseCase);
    }

    @Test
    @DisplayName("운영자가 아닌 사람에게 넘기려 하면 409 OWNERSHIP_RECEIVER_NOT_OPERATOR, 운영자가 넘기려 하면 403 OWNER_ONLY_ACTION이다")
    void transferRejections() throws Exception {
        willThrow(new RuleViolationException(OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR))
                .given(transferOwnershipUseCase).transfer(new TransferOwnershipCommand(JUNGHOON_IN_TWELVE_MONTHS, 4));
        mockMvc.perform(post(ORGANIZER_PATH + "/staff/4/ownership-transfer").with(csrf()).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("OWNERSHIP_RECEIVER_NOT_OPERATOR",
                        "소유자는 이 주최자의 운영자에게만 넘길 수 있습니다. 바깥 사람이면 먼저 운영자로 초대해 주세요."));

        AccountRef planner = AccountRef.of(AccountKind.STAFF, 2);
        willThrow(new PermissionDeniedException(OrganizerErrorCode.OWNER_ONLY_ACTION))
                .given(transferOwnershipUseCase).transfer(new TransferOwnershipCommand(new ActingStaff(planner, 12), 1));
        mockMvc.perform(post(ORGANIZER_PATH + "/staff/1/ownership-transfer").with(csrf()).with(loggedInAs(planner)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("OWNER_ONLY_ACTION", "소유자만 할 수 있는 일입니다."));
    }

    @Test
    @DisplayName("그 사이 같은 소유자가 먼저 넘겼으면 409 OWNERSHIP_ALREADY_TRANSFERRED다")
    void concurrentTransferIsConflict() throws Exception {
        willThrow(new ConflictException(OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED))
                .given(transferOwnershipUseCase).transfer(any());

        mockMvc.perform(post(ORGANIZER_PATH + "/staff/2/ownership-transfer").with(csrf()).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("OWNERSHIP_ALREADY_TRANSFERRED", "이미 소유자가 바뀌었습니다."));
    }

    @Test
    @DisplayName("소유자 이전을 신청하면 201에 신청 id · 한국 시각을 돌려주고, 사유는 앞뒤 공백을 걷어 넘긴다")
    void submitsOwnershipClaim() throws Exception {
        AccountRef planner = AccountRef.of(AccountKind.STAFF, 2);
        given(submitOwnershipClaimUseCase.submit(new SubmitOwnershipClaimCommand(new ActingStaff(planner, 12),
                OwnershipClaimReason.of("전 대표와 연락이 끊겼습니다."))))
                .willReturn(new SubmitOwnershipClaimResult(4, INVITED_AT));

        mockMvc.perform(claim(planner, "{\"reason\": \"  전 대표와 연락이 끊겼습니다. \"}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"ownershipClaimId": 4, "submittedAt": "2026-10-15T10:00:00+09:00"}""", JsonCompareMode.STRICT));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "　"})
    @DisplayName("신청 사유가 비었으면(전각 공백만 넣어도) 400 INVALID_INPUT이고 유스케이스를 부르지 않는다")
    void rejectsBlankClaimReason(String reason) throws Exception {
        mockMvc.perform(claim(AccountRef.of(AccountKind.STAFF, 2), "{\"reason\": \"" + reason + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("신청 사유를 입력해 주세요."));

        verifyNoInteractions(submitOwnershipClaimUseCase);
    }

    @Test
    @DisplayName("소유자가 신청하면 409 OWNER_CANNOT_CLAIM_OWNERSHIP, 심사 중인 신청이 있으면 409 OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW다")
    void ownershipClaimRejections() throws Exception {
        given(submitOwnershipClaimUseCase.submit(new SubmitOwnershipClaimCommand(JUNGHOON_IN_TWELVE_MONTHS,
                OwnershipClaimReason.of("연락 두절"))))
                .willThrow(new RuleViolationException(OrganizerErrorCode.OWNER_CANNOT_CLAIM_OWNERSHIP));
        mockMvc.perform(claim(JUNGHOON, "{\"reason\": \"연락 두절\"}"))
                .andExpect(status().isConflict())
                .andExpect(errorBody("OWNER_CANNOT_CLAIM_OWNERSHIP", "소유자는 이전 신청 없이 운영자에게 직접 넘길 수 있습니다."));

        AccountRef planner = AccountRef.of(AccountKind.STAFF, 2);
        given(submitOwnershipClaimUseCase.submit(new SubmitOwnershipClaimCommand(new ActingStaff(planner, 12),
                OwnershipClaimReason.of("연락 두절"))))
                .willThrow(new RuleViolationException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW));
        mockMvc.perform(claim(planner, "{\"reason\": \"연락 두절\"}"))
                .andExpect(status().isConflict())
                .andExpect(errorBody("OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW",
                        "심사 중인 소유자 이전 신청이 있습니다. 심사 결과를 기다려 주세요."));
    }

    @Test
    @DisplayName("나가면 204이고, 소유자가 나가려 해 거절되면 409 OWNER_MUST_HAND_OVER_FIRST다")
    void leaves() throws Exception {
        AccountRef planner = AccountRef.of(AccountKind.STAFF, 2);
        mockMvc.perform(post(ORGANIZER_PATH + "/departure").with(csrf()).with(loggedInAs(planner)))
                .andExpect(status().isNoContent());
        verify(leaveOrganizerUseCase).leave(new LeaveOrganizerCommand(new ActingStaff(planner, 12)));

        willThrow(new RuleViolationException(OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST))
                .given(leaveOrganizerUseCase).leave(new LeaveOrganizerCommand(JUNGHOON_IN_TWELVE_MONTHS));
        mockMvc.perform(post(ORGANIZER_PATH + "/departure").with(csrf()).with(loggedInAs(JUNGHOON)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("OWNER_MUST_HAND_OVER_FIRST", "먼저 소유자를 넘기거나 주최자를 닫아야 합니다."));
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get(ORGANIZER_PATH + "/staff"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"AUDIENCE", "ADMIN", "CHECKIN"})
    @DisplayName("운영진이 아닌 계정은 403이다")
    void staffOnly(AccountKind kind) throws Exception {
        mockMvc.perform(post(ORGANIZER_PATH + "/departure").with(csrf()).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verifyNoInteractions(leaveOrganizerUseCase);
    }

    private MockHttpServletRequestBuilder claim(AccountRef staff, String body) {
        return post(ORGANIZER_PATH + "/ownership-claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf())
                .with(loggedInAs(staff));
    }

    private MockHttpServletRequestBuilder invite(String body) {
        return post(ORGANIZER_PATH + "/staff-invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf())
                .with(loggedInAs(JUNGHOON));
    }
}
