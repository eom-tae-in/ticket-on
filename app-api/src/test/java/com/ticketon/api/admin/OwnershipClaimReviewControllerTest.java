package com.ticketon.api.admin;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static com.ticketon.api.security.TestLogin.loggedInAsAdmin;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.admin.port.in.ApproveOwnershipClaimCommand;
import com.ticketon.application.admin.port.in.ApproveOwnershipClaimUseCase;
import com.ticketon.application.admin.port.in.GetOwnershipClaimReviewQuery;
import com.ticketon.application.admin.port.in.GetOwnershipClaimReviewUseCase;
import com.ticketon.application.admin.port.in.OwnershipClaimReviewResult;
import com.ticketon.application.admin.port.in.RejectOwnershipClaimCommand;
import com.ticketon.application.admin.port.in.RejectOwnershipClaimUseCase;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.RejectionReason;
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

/**
 * 소유자 이전 신청 심사 API — 경로의 신청 id와 로그인한 관리자를 그대로 유스케이스에 넘기는지, 심사 화면의 응답 모양(한국 시각, 상태 이름), 요청
 * 검증, 유스케이스가 거절할 때의 상태 코드, 관리자만 열리는지를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OwnershipClaimReviewController.class)
@Import(SecurityConfiguration.class)
@DisplayName("소유자 이전 신청 심사 API — 관리자만")
class OwnershipClaimReviewControllerTest {

    private static final String CLAIM_PATH = "/api/admin/ownership-claims/4";

    private static final AccountRef ADMIN = AccountRef.of(AccountKind.ADMIN, 9);

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOwnershipClaimReviewUseCase getOwnershipClaimReviewUseCase;

    @MockitoBean
    private ApproveOwnershipClaimUseCase approveOwnershipClaimUseCase;

    @MockitoBean
    private RejectOwnershipClaimUseCase rejectOwnershipClaimUseCase;

    @Test
    @DisplayName("심사 화면을 한국 시각 · 상태 이름으로 돌려준다 — 처리 전이면 처리 일시 · 반려 사유가 null이다")
    void review() throws Exception {
        given(getOwnershipClaimReviewUseCase.getReview(new GetOwnershipClaimReviewQuery(ADMIN, 4)))
                .willReturn(new OwnershipClaimReviewResult(4, 12, "극단 열두달", OwnershipClaimStatus.UNDER_REVIEW,
                        "전 대표와 연락이 끊겼습니다.", SUBMITTED_AT, null, null,
                        new OwnershipClaimReviewResult.StaffMember(2, "푸른바람"), true,
                        new OwnershipClaimReviewResult.StaffMember(1, "조용한고래"),
                        new OwnershipClaimReviewResult.StaffMember(1, "조용한고래"),
                        new OwnershipClaimReviewResult.ApplicationContact(3, "010-2345-6789",
                                "twelve-months@example.com", List.of("https://www.instagram.com/twelve_months_theater"))));

        mockMvc.perform(get(CLAIM_PATH).with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"ownershipClaimId": 4, "organizerId": 12, "organizerName": "극단 열두달", "status": "UNDER_REVIEW",
                         "reason": "전 대표와 연락이 끊겼습니다.", "submittedAt": "2026-10-15T10:00:00+09:00",
                         "reviewedAt": null, "rejectionReason": null,
                         "claimant": {"staffAccountId": 2, "nickname": "푸른바람"}, "claimantIsOperator": true,
                         "ownerAtSubmission": {"staffAccountId": 1, "nickname": "조용한고래"},
                         "currentOwner": {"staffAccountId": 1, "nickname": "조용한고래"},
                         "applicationContact": {"organizerApplicationId": 3, "contactPhoneNumber": "010-2345-6789",
                             "email": "twelve-months@example.com",
                             "websiteAndSnsLinks": ["https://www.instagram.com/twelve_months_theater"]}}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("없는 신청이면 404 OWNERSHIP_CLAIM_NOT_FOUND다")
    void unknownClaim() throws Exception {
        given(getOwnershipClaimReviewUseCase.getReview(new GetOwnershipClaimReviewQuery(ADMIN, 4)))
                .willThrow(new NotFoundException(OrganizerErrorCode.OWNERSHIP_CLAIM_NOT_FOUND));

        mockMvc.perform(get(CLAIM_PATH).with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("OWNERSHIP_CLAIM_NOT_FOUND", "소유자 이전 신청을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("승인하면 204이고 경로의 신청 id와 로그인한 관리자를 넘긴다")
    void approves() throws Exception {
        mockMvc.perform(post(CLAIM_PATH + "/approval").with(csrf()).with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isNoContent());

        verify(approveOwnershipClaimUseCase).approve(new ApproveOwnershipClaimCommand(ADMIN, 4));
    }

    @Test
    @DisplayName("승인이 막히면 409다 — 이미 처리함, 소유자가 이미 바뀜, 신청한 사람이 운영자가 아님")
    void approvalRejections() throws Exception {
        willThrow(new ConflictException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED))
                .given(approveOwnershipClaimUseCase).approve(new ApproveOwnershipClaimCommand(ADMIN, 4));
        mockMvc.perform(post(CLAIM_PATH + "/approval").with(csrf()).with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("OWNERSHIP_CLAIM_ALREADY_REVIEWED", "이미 심사를 마친 소유자 이전 신청입니다."));

        willThrow(new RuleViolationException(OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED))
                .given(approveOwnershipClaimUseCase).approve(new ApproveOwnershipClaimCommand(ADMIN, 5));
        mockMvc.perform(post("/api/admin/ownership-claims/5/approval").with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("OWNERSHIP_ALREADY_TRANSFERRED", "이미 소유자가 바뀌었습니다."));

        willThrow(new RuleViolationException(OrganizerErrorCode.CLAIMANT_NOT_OPERATOR))
                .given(approveOwnershipClaimUseCase).approve(new ApproveOwnershipClaimCommand(ADMIN, 6));
        mockMvc.perform(post("/api/admin/ownership-claims/6/approval").with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("CLAIMANT_NOT_OPERATOR",
                        "신청한 사람이 이 주최자의 운영자가 아니어서 승인할 수 없습니다. 반려해 주세요."));
    }

    @Test
    @DisplayName("반려하면 204이고 사유는 앞뒤 공백을 걷어 넘긴다")
    void rejects() throws Exception {
        mockMvc.perform(post(CLAIM_PATH + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"  소유자와 통화해 직접 넘기기로 함 \"}")
                        .with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isNoContent());

        verify(rejectOwnershipClaimUseCase).reject(
                new RejectOwnershipClaimCommand(ADMIN, 4, RejectionReason.of("소유자와 통화해 직접 넘기기로 함")));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "　"})
    @DisplayName("반려 사유가 비었으면(전각 공백만 넣어도) 400 INVALID_INPUT이고 유스케이스를 부르지 않는다")
    void rejectsBlankReason(String reason) throws Exception {
        mockMvc.perform(post(CLAIM_PATH + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"" + reason + "\"}")
                        .with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("반려 사유를 입력해 주세요."));

        verifyNoInteractions(rejectOwnershipClaimUseCase);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0", "-1", "four"})
    @DisplayName("신청 id가 1 이상의 정수가 아니면 400 INVALID_INPUT이다")
    void rejectsInvalidId(String ownershipClaimId) throws Exception {
        mockMvc.perform(post("/api/admin/ownership-claims/" + ownershipClaimId + "/approval").with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(approveOwnershipClaimUseCase);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"AUDIENCE", "STAFF", "CHECKIN"})
    @DisplayName("관리자가 아닌 계정은 403이다 — 운영진이 자기 신청을 승인할 수 없다")
    void adminOnly(AccountKind kind) throws Exception {
        mockMvc.perform(post(CLAIM_PATH + "/approval").with(csrf()).with(loggedInAs(kind, 7)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));

        verifyNoInteractions(approveOwnershipClaimUseCase);
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get(CLAIM_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));

        verifyNoInteractions(getOwnershipClaimReviewUseCase);
    }
}
