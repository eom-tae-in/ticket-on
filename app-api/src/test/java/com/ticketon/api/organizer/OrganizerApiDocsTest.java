package com.ticketon.api.organizer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.GetOrganizerHomeUseCase;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 주최자 신청서 제출과 내 주최 홈의 요청·응답 모양이 명세에 그대로 실리는지
 * 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {OrganizerApplicationController.class, OrganizerHomeController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("주최자 신청서 제출·내 주최 홈 API 명세")
class OrganizerApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitOrganizerApplicationUseCase submitOrganizerApplicationUseCase;

    @MockitoBean
    private GetOrganizerHomeUseCase getOrganizerHomeUseCase;

    @Test
    @DisplayName("신청서 제출은 201에 심사 중인 신청을 돌려주고, 요청의 필수 칸은 신청 항목 표의 필수 항목이다 — 링크·사업자 정보는 선택")
    void submitOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/organizer/applications'].post.responses['201']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/OrganizerApplicationUnderReviewResponse"))
                .andExpect(jsonPath("$.paths['/api/organizer/applications'].post.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SubmitOrganizerApplicationRequest.required", containsInAnyOrder(
                        "organizerType", "name", "contactPhoneNumber", "email", "eventHistoryOrPlan", "businessStatus",
                        "settlementAccount")))
                .andExpect(jsonPath("$.components.schemas.SubmitOrganizerApplicationRequest.properties.organizerType.enum",
                        containsInAnyOrder("INDIVIDUAL", "GROUP")))
                .andExpect(jsonPath("$.components.schemas.SubmitOrganizerApplicationRequest.properties.businessStatus.enum",
                        containsInAnyOrder("BUSINESS", "NON_BUSINESS")))
                .andExpect(jsonPath("$.components.schemas.SettlementAccountRequest.required",
                        containsInAnyOrder("bankName", "accountNumber", "accountHolder")))
                .andExpect(jsonPath("$.components.schemas.BusinessInformationRequest.required").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrganizerApplicationUnderReviewResponse.required",
                        containsInAnyOrder("applicationId", "organizerName", "submittedAt")));
    }

    @Test
    @DisplayName("내 주최 홈은 200에 소속·심사 중·반려·받은 초대·다시 열기 자리를 돌려준다 — 심사 중·반려는 없으면 null")
    void homeOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/organizer/home'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/OrganizerHomeResponse"))
                .andExpect(jsonPath("$.paths['/api/organizer/home'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrganizerHomeResponse.required", containsInAnyOrder(
                        "affiliations", "receivedInvitations", "reopenableOrganizers")))
                .andExpect(jsonPath("$.components.schemas.OrganizerAffiliation.required", containsInAnyOrder(
                        "organizerId", "organizerName", "role", "since")))
                .andExpect(jsonPath("$.components.schemas.OrganizerAffiliation.properties.role.enum",
                        containsInAnyOrder("OWNER", "OPERATOR")))
                .andExpect(jsonPath("$.components.schemas.RejectedOrganizerApplication.required", containsInAnyOrder(
                        "applicationId", "organizerName", "submittedAt", "rejectedAt", "rejectionReason")));
    }
}
