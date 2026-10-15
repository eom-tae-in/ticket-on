package com.ticketon.api.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.account.AdminLoginController;
import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.account.port.in.LogInAdminUseCase;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationUseCase;
import com.ticketon.application.admin.port.in.GetAdminWorkQueueUseCase;
import com.ticketon.application.admin.port.in.GetOrganizerApplicationReviewUseCase;
import com.ticketon.application.admin.port.in.IssueAdminAccountUseCase;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 관리자 로그인·로그아웃, 관리자 계정 발급, 처리 목록, 주최자 심사의 요청·응답
 * 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {AdminLoginController.class, AdminAccountController.class, AdminWorkQueueController.class,
        OrganizerApplicationReviewController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("관리자 로그인·관리자 계정 발급·처리 목록·주최자 심사 API 명세")
class AdminApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogInAdminUseCase logInAdminUseCase;

    @MockitoBean
    private IssueAdminAccountUseCase issueAdminAccountUseCase;

    @MockitoBean
    private GetAdminWorkQueueUseCase getAdminWorkQueueUseCase;

    @MockitoBean
    private GetOrganizerApplicationReviewUseCase getOrganizerApplicationReviewUseCase;

    @MockitoBean
    private ApproveOrganizerApplicationUseCase approveOrganizerApplicationUseCase;

    @MockitoBean
    private RejectOrganizerApplicationUseCase rejectOrganizerApplicationUseCase;

    @Test
    @DisplayName("로그인은 200에 LogInAdminResponse(returnPath·grade)를 돌려주고, 요청은 email·password가 필수이고 returnPath는 고를 수 있다")
    void logInOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/admin/login'].post.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/LogInAdminResponse"))
                .andExpect(jsonPath("$.components.schemas.LogInAdminRequest.required", containsInAnyOrder("email", "password")))
                .andExpect(jsonPath("$.components.schemas.LogInAdminRequest.properties.returnPath").exists())
                .andExpect(jsonPath("$.components.schemas.LogInAdminResponse.required",
                        containsInAnyOrder("returnPath", "grade")))
                .andExpect(jsonPath("$.components.schemas.LogInAdminResponse.properties.grade.enum",
                        containsInAnyOrder("ADMIN", "SUPER_ADMIN")));
    }

    @Test
    @DisplayName("로그아웃은 본문 없이 204로 끝난다")
    void logOutOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/admin/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/admin/logout'].post.requestBody").doesNotExist());
    }

    @Test
    @DisplayName("관리자 계정 발급은 최고 관리자 전용 경로에서 email·password·grade를 받아 201에 IssueAdminAccountResponse를 돌려준다 — 로그인 주체는 파라미터로 싣지 않는다")
    void issueOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/super/admin-accounts'].post.responses['201']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/IssueAdminAccountResponse"))
                .andExpect(jsonPath("$.paths['/api/admin/super/admin-accounts'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.IssueAdminAccountRequest.required",
                        containsInAnyOrder("email", "password", "grade")))
                .andExpect(jsonPath("$.components.schemas.IssueAdminAccountResponse.required",
                        containsInAnyOrder("email", "grade")));
    }
    @Test
    @DisplayName("처리 목록은 200에 AdminWorkQueueResponse를 돌려주고, 항목은 kind·targetId·title·receivedAt·highlighted가 필수다")
    void workQueueOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/work-queue'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/AdminWorkQueueResponse"))
                .andExpect(jsonPath("$.paths['/api/admin/work-queue'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AdminWorkQueueResponse.required", containsInAnyOrder("items")))
                .andExpect(jsonPath("$.components.schemas.AdminWorkItem.required",
                        containsInAnyOrder("kind", "targetId", "title", "receivedAt", "highlighted")))
                .andExpect(jsonPath("$.components.schemas.AdminWorkItem.properties.kind.enum",
                        containsInAnyOrder("ORGANIZER_APPLICATION")));
    }

    @Test
    @DisplayName("심사 화면은 신청서 id 경로에서 OrganizerApplicationReviewResponse를 돌려준다 — 처리 일시·반려 사유·사업자 정보만 비어 있을 수 있다")
    void reviewOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/organizer-applications/{applicationId}'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/OrganizerApplicationReviewResponse"))
                .andExpect(jsonPath("$.paths['/api/admin/organizer-applications/{applicationId}'].get.parameters[*].name",
                        containsInAnyOrder("applicationId")))
                .andExpect(jsonPath("$.components.schemas.OrganizerApplicationReviewResponse.required",
                        containsInAnyOrder("applicationId", "organizerId", "applicantStaffAccountId", "status",
                                "submittedAt", "reviewDueDate", "form", "checklist", "rejectionReasonTemplates",
                                "similarClosedOrganizers")))
                .andExpect(jsonPath("$.components.schemas.ReviewedApplicationForm.required",
                        containsInAnyOrder("organizerType", "name", "contactPhoneNumber", "email", "websiteAndSnsLinks",
                                "eventHistoryOrPlan", "businessStatus", "settlementAccount")))
                .andExpect(jsonPath("$.components.schemas.ReviewChecklistItem.properties.item.enum",
                        containsInAnyOrder("CONTACT_REACHED_BY_PHONE", "EXISTENCE_CONFIRMED_BY_LINKS_AND_HISTORY",
                                "EVENT_PLAN_IS_CONCRETE")))
                .andExpect(jsonPath("$.components.schemas.SimilarClosedOrganizer.required",
                        containsInAnyOrder("organizerId", "organizerName", "sameOwner", "sameName")));
    }

    @Test
    @DisplayName("승인은 본문 없이 ApproveOrganizerApplicationResponse를, 반려는 reason(필수)을 받아 RejectOrganizerApplicationResponse를 돌려준다")
    void approveAndRejectOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/organizer-applications/{applicationId}/approval'].post"
                        + ".responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApproveOrganizerApplicationResponse"))
                .andExpect(jsonPath("$.paths['/api/admin/organizer-applications/{applicationId}/approval'].post"
                        + ".requestBody").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/organizer-applications/{applicationId}/rejection'].post"
                        + ".responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/RejectOrganizerApplicationResponse"))
                .andExpect(jsonPath("$.components.schemas.RejectOrganizerApplicationRequest.required",
                        containsInAnyOrder("reason")))
                .andExpect(jsonPath("$.components.schemas.ApproveOrganizerApplicationResponse.required",
                        containsInAnyOrder("applicationId", "organizerId", "organizerName", "ownerStaffAccountId",
                                "approvedAt")))
                .andExpect(jsonPath("$.components.schemas.RejectOrganizerApplicationResponse.required",
                        containsInAnyOrder("applicationId", "organizerName", "rejectionReason", "rejectedAt")));
    }
}
