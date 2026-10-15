package com.ticketon.api.admin;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static com.ticketon.api.security.TestLogin.loggedInAsAdmin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationCommand;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationResult;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationUseCase;
import com.ticketon.application.admin.port.in.GetOrganizerApplicationReviewQuery;
import com.ticketon.application.admin.port.in.GetOrganizerApplicationReviewUseCase;
import com.ticketon.application.admin.port.in.OrganizerApplicationReviewResult;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationCommand;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationResult;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationUseCase;
import com.ticketon.application.admin.query.SimilarClosedOrganizer;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerReviewChecklistItem;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.RejectionReason;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주최자 심사 API — 결과 → 응답 변환(한국 시각, 코드 이름), 요청 → Command 변환, 사유 없는 반려 400, 오류 응답, 관리자만 열리는지를 본다.
 * 유스케이스는 흉내 낸다. 저장 · 조작 기록 · 메일은 유스케이스 테스트와 통합 테스트가 본다.
 */
@WebMvcTest(controllers = OrganizerApplicationReviewController.class)
@Import(SecurityConfiguration.class)
@DisplayName("주최자 심사 API — 관리자만")
class OrganizerApplicationReviewControllerTest {

    private static final String REVIEW_PATH = "/api/admin/organizer-applications/3";

    private static final AccountRef DOHYUN = AccountRef.of(AccountKind.ADMIN, 4);

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant REVIEWED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private GetOrganizerApplicationReviewUseCase getReviewUseCase;

    @MockitoBean
    private ApproveOrganizerApplicationUseCase approveUseCase;

    @MockitoBean
    private RejectOrganizerApplicationUseCase rejectUseCase;

    @Nested
    @DisplayName("심사 화면")
    class Review {

        @Test
        @DisplayName("신청서 전체 · 체크리스트(항목·글) · 반려 템플릿 · 목표일 · 닫힌 주최자를 돌려주고, 유스케이스는 로그인한 관리자와 신청서 id를 받는다")
        void returnsReview() throws Exception {
            given(getReviewUseCase.getReview(any())).willReturn(lateSummerReview());

            mockMvc.perform(get(REVIEW_PATH).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {
                              "applicationId": 3,
                              "organizerId": 12,
                              "applicantStaffAccountId": 5,
                              "status": "UNDER_REVIEW",
                              "submittedAt": "2026-10-15T10:00:00+09:00",
                              "reviewDueDate": "2026-10-20",
                              "reviewedAt": null,
                              "rejectionReason": null,
                              "form": {
                                "organizerType": "GROUP",
                                "name": "늦여름",
                                "contactPhoneNumber": "010-9876-5432",
                                "email": "hello@late-summer.example",
                                "websiteAndSnsLinks": ["https://late-summer.example"],
                                "eventHistoryOrPlan": "인디 페스티벌 3회 개최.",
                                "businessStatus": "BUSINESS",
                                "businessInformation": {
                                  "tradeName": "늦여름 레코드",
                                  "representativeName": "최여름",
                                  "businessAddress": "서울특별시 마포구 와우산로 12",
                                  "businessPhoneNumber": "02-123-4567",
                                  "businessRegistrationNumber": "1234567890"
                                },
                                "settlementAccount": {
                                  "bankName": "신한은행",
                                  "accountNumber": "110-123-456789",
                                  "accountHolder": "늦여름 레코드"
                                }
                              },
                              "checklist": [
                                {"item": "CONTACT_REACHED_BY_PHONE", "label": "연락처로 통화 확인"},
                                {"item": "EXISTENCE_CONFIRMED_BY_LINKS_AND_HISTORY", "label": "링크·이력으로 실재 확인"},
                                {"item": "EVENT_PLAN_IS_CONCRETE", "label": "행사 계획이 구체적임"}
                              ],
                              "rejectionReasonTemplates": ["연락이 닿지 않음", "정보가 부족함", "서비스 성격과 맞지 않음"],
                              "similarClosedOrganizers": [
                                {"organizerId": 8, "organizerName": "늦여름", "sameOwner": false, "sameName": true}
                              ]
                            }""", JsonCompareMode.STRICT));

            ArgumentCaptor<GetOrganizerApplicationReviewQuery> query =
                    ArgumentCaptor.forClass(GetOrganizerApplicationReviewQuery.class);
            verify(getReviewUseCase).getReview(query.capture());
            assertThat(query.getValue()).isEqualTo(new GetOrganizerApplicationReviewQuery(DOHYUN, 3));
        }

        @Test
        @DisplayName("반려된 신청서는 처리 일시(한국 시각)와 사유가, 비사업자는 사업자 정보 null이 온다")
        void returnsRejectedNonBusinessReview() throws Exception {
            given(getReviewUseCase.getReview(any())).willReturn(rejectedTwelveMonthsReview());

            mockMvc.perform(get(REVIEW_PATH).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.reviewedAt").value("2026-10-17T15:00:00+09:00"))
                    .andExpect(jsonPath("$.rejectionReason").value("연락이 닿지 않음"))
                    .andExpect(jsonPath("$.form.businessStatus").value("NON_BUSINESS"))
                    .andExpect(jsonPath("$.form.businessInformation").isEmpty())
                    .andExpect(jsonPath("$.similarClosedOrganizers").isEmpty());
        }

        @Test
        @DisplayName("없는 신청서면 404 APPLICATION_NOT_FOUND")
        void unknownApplicationIsNotFound() throws Exception {
            given(getReviewUseCase.getReview(any()))
                    .willThrow(new NotFoundException(OrganizerErrorCode.APPLICATION_NOT_FOUND));

            mockMvc.perform(get(REVIEW_PATH).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isNotFound())
                    .andExpect(errorBody("APPLICATION_NOT_FOUND", "주최자 신청을 찾을 수 없습니다."));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"0", "-1"})
        @DisplayName("신청서 id가 1보다 작으면 400이고 유스케이스를 부르지 않는다")
        void rejectsNonPositiveId(String id) throws Exception {
            mockMvc.perform(get("/api/admin/organizer-applications/" + id).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

            verify(getReviewUseCase, never()).getReview(any());
        }
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("200에 승인 결과(한국 시각)를 돌려주고, 유스케이스는 로그인한 관리자와 신청서 id를 받는다 — 본문은 없다")
        void approves() throws Exception {
            given(approveUseCase.approve(any())).willReturn(
                    new ApproveOrganizerApplicationResult(3, 12, "극단 열두달", 5, REVIEWED_AT));

            mockMvc.perform(post(REVIEW_PATH + "/approval").with(csrf()).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"applicationId": 3, "organizerId": 12, "organizerName": "극단 열두달",
                             "ownerStaffAccountId": 5, "approvedAt": "2026-10-17T15:00:00+09:00"}""",
                            JsonCompareMode.STRICT));

            ArgumentCaptor<ApproveOrganizerApplicationCommand> command =
                    ArgumentCaptor.forClass(ApproveOrganizerApplicationCommand.class);
            verify(approveUseCase).approve(command.capture());
            assertThat(command.getValue()).isEqualTo(new ApproveOrganizerApplicationCommand(DOHYUN, 3));
        }

        @Test
        @DisplayName("이미 처리한 신청이면 409 APPLICATION_ALREADY_REVIEWED")
        void alreadyReviewedIsConflict() throws Exception {
            given(approveUseCase.approve(any()))
                    .willThrow(new RuleViolationException(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED));

            mockMvc.perform(post(REVIEW_PATH + "/approval").with(csrf()).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("APPLICATION_ALREADY_REVIEWED", "이미 심사를 마친 신청입니다."));
        }

        @Test
        @DisplayName("다른 관리자가 거의 동시에 먼저 처리해 진 쪽도 409 같은 코드다")
        void lostRaceIsConflict() throws Exception {
            given(approveUseCase.approve(any()))
                    .willThrow(new ConflictException(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED));

            mockMvc.perform(post(REVIEW_PATH + "/approval").with(csrf()).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("APPLICATION_ALREADY_REVIEWED", "이미 심사를 마친 신청입니다."));
        }

        @Test
        @DisplayName("CSRF 토큰 없이 보내면 403이고 유스케이스를 부르지 않는다")
        void requiresCsrfToken() throws Exception {
            mockMvc.perform(post(REVIEW_PATH + "/approval").with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isForbidden());

            verify(approveUseCase, never()).approve(any());
        }
    }

    @Nested
    @DisplayName("반려")
    class Reject {

        @Test
        @DisplayName("200에 반려 결과를 돌려주고, 유스케이스는 로그인한 관리자 · 신청서 id · 앞뒤 공백을 걷은 사유를 받는다")
        void rejects() throws Exception {
            given(rejectUseCase.reject(any())).willReturn(
                    new RejectOrganizerApplicationResult(3, "극단 열두달", "연락이 닿지 않음", REVIEWED_AT));

            mockMvc.perform(rejectRequest(Map.of("reason", "  연락이 닿지 않음 ")).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"applicationId": 3, "organizerName": "극단 열두달", "rejectionReason": "연락이 닿지 않음",
                             "rejectedAt": "2026-10-17T15:00:00+09:00"}""", JsonCompareMode.STRICT));

            ArgumentCaptor<RejectOrganizerApplicationCommand> command =
                    ArgumentCaptor.forClass(RejectOrganizerApplicationCommand.class);
            verify(rejectUseCase).reject(command.capture());
            assertThat(command.getValue()).isEqualTo(
                    new RejectOrganizerApplicationCommand(DOHYUN, 3, RejectionReason.of("연락이 닿지 않음")));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "   ", "　"})
        @DisplayName("사유가 비면(전각 공백만 넣은 것 포함) 400 '반려 사유를 입력해 주세요.'이고 유스케이스를 부르지 않는다")
        void rejectsBlankReason(String reason) throws Exception {
            mockMvc.perform(rejectRequest(Map.of("reason", reason)).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("반려 사유를 입력해 주세요."));

            verify(rejectUseCase, never()).reject(any());
        }

        @Test
        @DisplayName("사유 칸이 없으면 400이고 유스케이스를 부르지 않는다")
        void rejectsMissingReason() throws Exception {
            mockMvc.perform(rejectRequest(Map.of()).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));

            verify(rejectUseCase, never()).reject(any());
        }

        @Test
        @DisplayName("이미 처리한 신청이면 409 APPLICATION_ALREADY_REVIEWED")
        void alreadyReviewedIsConflict() throws Exception {
            given(rejectUseCase.reject(any()))
                    .willThrow(new ConflictException(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED));

            mockMvc.perform(rejectRequest(Map.of("reason", "정보가 부족함")).with(loggedInAsAdmin(4, AdminGrade.ADMIN)))
                    .andExpect(status().isConflict())
                    .andExpect(errorBody("APPLICATION_ALREADY_REVIEWED", "이미 심사를 마친 신청입니다."));
        }
    }

    @Nested
    @DisplayName("관리자만 연다 — 유스케이스를 부르지 않는다")
    class AdminOnly {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AccountKind.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("관리자가 아닌 주체는 심사 화면·승인·반려 모두 403이다")
        void nonAdminIsForbidden(AccountKind kind) throws Exception {
            mockMvc.perform(get(REVIEW_PATH).with(loggedInAs(kind, 7))).andExpect(status().isForbidden());
            mockMvc.perform(post(REVIEW_PATH + "/approval").with(csrf()).with(loggedInAs(kind, 7)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(rejectRequest(Map.of("reason", "정보가 부족함")).with(loggedInAs(kind, 7)))
                    .andExpect(status().isForbidden());

            verify(getReviewUseCase, never()).getReview(any());
            verify(approveUseCase, never()).approve(any());
            verify(rejectUseCase, never()).reject(any());
        }

        @Test
        @DisplayName("최고 관리자도 심사한다")
        void superAdminCanReview() throws Exception {
            given(getReviewUseCase.getReview(any())).willReturn(lateSummerReview());

            mockMvc.perform(get(REVIEW_PATH).with(loggedInAsAdmin(1, AdminGrade.SUPER_ADMIN)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("로그인하지 않았으면 401이다")
        void requiresLogin() throws Exception {
            mockMvc.perform(get(REVIEW_PATH))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }
    }

    private MockHttpServletRequestBuilder rejectRequest(Map<String, Object> body) {
        return post(REVIEW_PATH + "/rejection").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)).with(csrf());
    }

    private static OrganizerApplicationReviewResult lateSummerReview() {
        return new OrganizerApplicationReviewResult(3, 12, 5, OrganizerApplicationStatus.UNDER_REVIEW, SUBMITTED_AT,
                LocalDate.of(2026, 10, 20), null, null,
                new OrganizerApplicationReviewResult.Form(OrganizerType.GROUP, "늦여름", "010-9876-5432",
                        "hello@late-summer.example", List.of("https://late-summer.example"), "인디 페스티벌 3회 개최.",
                        BusinessStatus.BUSINESS,
                        new OrganizerApplicationReviewResult.BusinessInformation("늦여름 레코드", "최여름",
                                "서울특별시 마포구 와우산로 12", "02-123-4567", "1234567890"),
                        new OrganizerApplicationReviewResult.SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드")),
                List.of(OrganizerReviewChecklistItem.values()),
                List.of("연락이 닿지 않음", "정보가 부족함", "서비스 성격과 맞지 않음"),
                List.of(new SimilarClosedOrganizer(8, "늦여름", false, true)));
    }

    private static OrganizerApplicationReviewResult rejectedTwelveMonthsReview() {
        return new OrganizerApplicationReviewResult(3, 12, 5, OrganizerApplicationStatus.REJECTED, SUBMITTED_AT,
                LocalDate.of(2026, 10, 20), REVIEWED_AT, "연락이 닿지 않음",
                new OrganizerApplicationReviewResult.Form(OrganizerType.GROUP, "극단 열두달", "010-2345-6789",
                        "twelve-months@example.com", List.of(), "소극장 정기공연", BusinessStatus.NON_BUSINESS, null,
                        new OrganizerApplicationReviewResult.SettlementAccount("국민은행", "123456-78-901234", "박정훈")),
                List.of(OrganizerReviewChecklistItem.values()),
                List.of("연락이 닿지 않음", "정보가 부족함", "서비스 성격과 맞지 않음"),
                List.of());
    }
}
