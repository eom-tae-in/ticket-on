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
import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestCommand;
import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestUseCase;
import com.ticketon.application.admin.port.in.GetOrganizerChangeRequestReviewQuery;
import com.ticketon.application.admin.port.in.GetOrganizerChangeRequestReviewUseCase;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult;
import com.ticketon.application.admin.port.in.RejectOrganizerChangeRequestCommand;
import com.ticketon.application.admin.port.in.RejectOrganizerChangeRequestUseCase;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RejectionReason;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * 정산 계좌 · 사업자 정보 변경 확인 API — 경로의 요청 id와 로그인한 관리자를 그대로 유스케이스에 넘기는지, 확인 화면의 응답 모양(한국 시각,
 * 종류 · 상태 이름, 종류에 맞는 값만), 요청 검증, 유스케이스가 거절할 때의 상태 코드, 관리자만 열리는지를 본다. 유스케이스는 흉내 낸다.
 */
@WebMvcTest(controllers = OrganizerChangeRequestReviewController.class)
@Import(SecurityConfiguration.class)
@DisplayName("정산 계좌 · 사업자 정보 변경 확인 API — 관리자만")
class OrganizerChangeRequestReviewControllerTest {

    private static final String REQUEST_PATH = "/api/admin/organizer-change-requests/8";

    private static final AccountRef ADMIN = AccountRef.of(AccountKind.ADMIN, 9);

    private static final Instant REQUESTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOrganizerChangeRequestReviewUseCase getReviewUseCase;

    @MockitoBean
    private ApproveOrganizerChangeRequestUseCase approveUseCase;

    @MockitoBean
    private RejectOrganizerChangeRequestUseCase rejectUseCase;

    @Test
    @DisplayName("정산 계좌 변경 확인 화면을 한국 시각 · 종류 · 상태 이름으로 돌려준다 — 계좌번호를 가리지 않고, 사업자 정보 칸은 null이다")
    void review() throws Exception {
        given(getReviewUseCase.getReview(new GetOrganizerChangeRequestReviewQuery(ADMIN, 8)))
                .willReturn(new OrganizerChangeRequestReviewResult(8, 13, "늦여름", OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT,
                        OrganizerChangeRequestStatus.UNDER_REVIEW, REQUESTED_AT, null, null,
                        new OrganizerChangeRequestReviewResult.StaffMember(3, "늦은여름"), true,
                        new OrganizerChangeRequestReviewResult.StaffMember(3, "늦은여름"),
                        new OrganizerChangeRequestReviewResult.SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"),
                        new OrganizerChangeRequestReviewResult.SettlementAccount("우리은행", "1002-345-678901", "최여름"),
                        null, null));

        mockMvc.perform(get(REQUEST_PATH).with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"changeRequestId": 8, "organizerId": 13, "organizerName": "늦여름", "kind": "SETTLEMENT_ACCOUNT",
                         "status": "UNDER_REVIEW", "requestedAt": "2026-10-15T10:00:00+09:00", "reviewedAt": null,
                         "rejectionReason": null,
                         "requester": {"staffAccountId": 3, "nickname": "늦은여름"}, "requesterIsOwner": true,
                         "currentOwner": {"staffAccountId": 3, "nickname": "늦은여름"},
                         "currentSettlementAccount": {"bankName": "신한은행", "accountNumber": "110-123-456789",
                                                      "accountHolder": "늦여름 레코드"},
                         "requestedSettlementAccount": {"bankName": "우리은행", "accountNumber": "1002-345-678901",
                                                        "accountHolder": "최여름"},
                         "currentBusinessInformation": null, "requestedBusinessInformation": null}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("사업자 정보 변경 확인 화면은 사업자 정보 칸을 채우고 정산 계좌 칸이 null이다")
    void reviewBusinessInformation() throws Exception {
        given(getReviewUseCase.getReview(new GetOrganizerChangeRequestReviewQuery(ADMIN, 8)))
                .willReturn(new OrganizerChangeRequestReviewResult(8, 13, "늦여름", OrganizerChangeRequestKind.BUSINESS_INFORMATION,
                        OrganizerChangeRequestStatus.REJECTED, REQUESTED_AT, REQUESTED_AT.plusSeconds(3_600),
                        "사업자등록증의 주소와 다릅니다", new OrganizerChangeRequestReviewResult.StaffMember(3, "늦은여름"), false,
                        new OrganizerChangeRequestReviewResult.StaffMember(4, "밤의기타"), null, null,
                        new OrganizerChangeRequestReviewResult.BusinessInformation("늦여름 레코드", "최여름",
                                "서울특별시 마포구 와우산로 12", "02-123-4567", "1234567890"),
                        new OrganizerChangeRequestReviewResult.BusinessInformation("늦여름 레코드", "최여름",
                                "서울특별시 마포구 독막로 7", "02-765-4321", "1234567890")));

        mockMvc.perform(get(REQUEST_PATH).with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("BUSINESS_INFORMATION"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedAt").value("2026-10-15T11:00:00+09:00"))
                .andExpect(jsonPath("$.requesterIsOwner").value(false))
                .andExpect(jsonPath("$.currentOwner.nickname").value("밤의기타"))
                .andExpect(jsonPath("$.currentSettlementAccount").isEmpty())
                .andExpect(jsonPath("$.requestedBusinessInformation.businessAddress").value("서울특별시 마포구 독막로 7"))
                .andExpect(jsonPath("$.currentBusinessInformation.businessRegistrationNumber").value("1234567890"));
    }

    @Test
    @DisplayName("없는 요청이면 404 ORGANIZER_CHANGE_REQUEST_NOT_FOUND다")
    void unknownRequest() throws Exception {
        given(getReviewUseCase.getReview(new GetOrganizerChangeRequestReviewQuery(ADMIN, 8)))
                .willThrow(new NotFoundException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_NOT_FOUND));

        mockMvc.perform(get(REQUEST_PATH).with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("ORGANIZER_CHANGE_REQUEST_NOT_FOUND", "변경 요청을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("승인하면 204이고 경로의 요청 id와 로그인한 관리자를 넘긴다")
    void approves() throws Exception {
        mockMvc.perform(post(REQUEST_PATH + "/approval").with(csrf()).with(loggedInAsAdmin(9, AdminGrade.SUPER_ADMIN)))
                .andExpect(status().isNoContent());

        verify(approveUseCase).approve(new ApproveOrganizerChangeRequestCommand(ADMIN, 8));
    }

    @Test
    @DisplayName("승인이 막히면 409다 — 이미 처리함, 요청한 사람이 지금 소유자가 아님")
    void approvalRejections() throws Exception {
        willThrow(new ConflictException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED))
                .given(approveUseCase).approve(new ApproveOrganizerChangeRequestCommand(ADMIN, 8));
        mockMvc.perform(post(REQUEST_PATH + "/approval").with(csrf()).with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED", "이미 처리한 변경 요청입니다."));

        willThrow(new RuleViolationException(OrganizerErrorCode.CHANGE_REQUESTER_NOT_OWNER))
                .given(approveUseCase).approve(new ApproveOrganizerChangeRequestCommand(ADMIN, 9));
        mockMvc.perform(post("/api/admin/organizer-change-requests/9/approval").with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(errorBody("CHANGE_REQUESTER_NOT_OWNER", "요청한 사람이 지금 소유자가 아니어서 승인할 수 없습니다. 반려해 주세요."));
    }

    @Test
    @DisplayName("반려하면 204이고 사유는 앞뒤 공백을 걷어 넘긴다")
    void rejects() throws Exception {
        mockMvc.perform(post(REQUEST_PATH + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"  예금주가 대표자 이름과 다릅니다 \"}")
                        .with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isNoContent());

        verify(rejectUseCase).reject(
                new RejectOrganizerChangeRequestCommand(ADMIN, 8, RejectionReason.of("예금주가 대표자 이름과 다릅니다")));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "　"})
    @DisplayName("반려 사유가 비었으면(전각 공백만 넣어도) 400 INVALID_INPUT이고 유스케이스를 부르지 않는다")
    void rejectsBlankReason(String reason) throws Exception {
        mockMvc.perform(post(REQUEST_PATH + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"" + reason + "\"}")
                        .with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("반려 사유를 입력해 주세요."));

        verifyNoInteractions(rejectUseCase);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0", "-1", "eight"})
    @DisplayName("요청 id가 1 이상의 정수가 아니면 400 INVALID_INPUT이다")
    void rejectsInvalidId(String changeRequestId) throws Exception {
        mockMvc.perform(post("/api/admin/organizer-change-requests/" + changeRequestId + "/approval").with(csrf())
                        .with(loggedInAsAdmin(9, AdminGrade.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(approveUseCase);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AccountKind.class, names = {"AUDIENCE", "STAFF", "CHECKIN"})
    @DisplayName("관리자가 아닌 계정은 403이다 — 소유자가 자기 요청을 승인할 수 없다")
    void adminOnly(AccountKind kind) throws Exception {
        mockMvc.perform(post(REQUEST_PATH + "/approval").with(csrf()).with(loggedInAs(kind, 3)))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("ACCESS_DENIED", "이 요청을 처리할 권한이 없습니다."));
        mockMvc.perform(get(REQUEST_PATH).with(loggedInAs(kind, 3)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(approveUseCase, getReviewUseCase);
    }

    @Test
    @DisplayName("로그인하지 않았으면 401이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get(REQUEST_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));

        verifyNoInteractions(getReviewUseCase);
    }
}
