package com.ticketon.api.organizer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.GetOrganizerBusinessDetailsUseCase;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 사업자 정보 · 정산 계좌 보기와 변경 요청의 요청 · 응답 모양이 명세에 그대로 실리는지
 * 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = OrganizerBusinessDetailsController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("사업자 정보 · 정산 계좌 API 명세")
class OrganizerBusinessDetailsApiDocsTest {

    private static final String BASE = "$.paths['/api/organizer/organizers/{organizerId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOrganizerBusinessDetailsUseCase getBusinessDetailsUseCase;

    @MockitoBean
    private RequestOrganizerChangeUseCase requestOrganizerChangeUseCase;

    @Test
    @DisplayName("보기는 200에 OrganizerBusinessDetailsResponse를 돌려준다 — 계좌번호는 가린 칸(maskedAccountNumber)만 있다")
    void businessDetailsOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(BASE + "/business-details'].get.parameters[*].name", containsInAnyOrder("organizerId")))
                .andExpect(jsonPath(BASE + "/business-details'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/OrganizerBusinessDetailsResponse"))
                .andExpect(jsonPath("$.components.schemas.OrganizerBusinessDetailsResponse.required",
                        containsInAnyOrder("organizerId", "businessStatus", "settlementAccount")))
                .andExpect(jsonPath("$.components.schemas.MaskedSettlementAccount.required",
                        containsInAnyOrder("bankName", "maskedAccountNumber", "accountHolder")))
                .andExpect(jsonPath("$.components.schemas.OwnerBusinessInformation.required", containsInAnyOrder("tradeName",
                        "representativeName", "businessAddress", "businessPhoneNumber", "businessRegistrationNumber")))
                .andExpect(jsonPath("$.components.schemas.PendingSettlementAccountChange.required",
                        containsInAnyOrder("changeRequestId", "requestedAt", "requested")));
    }

    @Test
    @DisplayName("변경 요청은 정산 계좌 · 사업자 정보마다 요청 본문을 받아 201에 OrganizerChangeRequestResponse를 돌려준다")
    void changeRequestOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(BASE + "/settlement-account-change-requests'].post.requestBody.content['application/json']"
                        + ".schema['$ref']").value("#/components/schemas/ChangeSettlementAccountRequest"))
                .andExpect(jsonPath(BASE + "/settlement-account-change-requests'].post.responses['201'].content"
                        + "['application/json'].schema['$ref']").value("#/components/schemas/OrganizerChangeRequestResponse"))
                .andExpect(jsonPath(BASE + "/business-information-change-requests'].post.requestBody.content"
                        + "['application/json'].schema['$ref']").value("#/components/schemas/ChangeBusinessInformationRequest"))
                .andExpect(jsonPath(BASE + "/business-information-change-requests'].post.responses['201'].content"
                        + "['application/json'].schema['$ref']").value("#/components/schemas/OrganizerChangeRequestResponse"))
                .andExpect(jsonPath("$.components.schemas.ChangeSettlementAccountRequest.required",
                        containsInAnyOrder("bankName", "accountNumber", "accountHolder")))
                .andExpect(jsonPath("$.components.schemas.ChangeBusinessInformationRequest.required", containsInAnyOrder(
                        "tradeName", "representativeName", "businessAddress", "businessPhoneNumber",
                        "businessRegistrationNumber")))
                .andExpect(jsonPath("$.components.schemas.OrganizerChangeRequestResponse.required",
                        containsInAnyOrder("changeRequestId", "kind", "requestedAt")))
                .andExpect(jsonPath("$.components.schemas.OrganizerChangeRequestResponse.properties.kind.enum",
                        containsInAnyOrder("SETTLEMENT_ACCOUNT", "BUSINESS_INFORMATION")));
    }
}
