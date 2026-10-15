package com.ticketon.api.organizer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.AcceptStaffInvitationUseCase;
import com.ticketon.application.organizer.port.in.DeclineStaffInvitationUseCase;
import com.ticketon.application.organizer.port.in.GetStaffListUseCase;
import com.ticketon.application.organizer.port.in.InviteOperatorUseCase;
import com.ticketon.application.organizer.port.in.LeaveOrganizerUseCase;
import com.ticketon.application.organizer.port.in.RemoveOperatorUseCase;
import com.ticketon.application.organizer.port.in.TransferOwnershipUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 운영진 관리와 받은 초대 수락 · 거절의 요청 · 응답 모양이 명세에 그대로 실리는지
 * 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = {StaffManagementController.class, ReceivedStaffInvitationController.class})
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("운영진 관리 · 받은 운영자 초대 API 명세")
class StaffManagementApiDocsTest {

    private static final String ORGANIZER_PATH = "$.paths['/api/organizer/organizers/{organizerId}";

    private static final String INVITATION_PATH = "$.paths['/api/organizer/staff-invitations/{invitationId}";

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
    private AcceptStaffInvitationUseCase acceptStaffInvitationUseCase;

    @MockitoBean
    private DeclineStaffInvitationUseCase declineStaffInvitationUseCase;

    @Test
    @DisplayName("운영자 초대는 경로의 organizerId와 이메일 하나를 받아 201에 보낸 초대를 돌려준다")
    void inviteOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ORGANIZER_PATH + "/staff-invitations'].post.parameters[*].name")
                        .value(containsInAnyOrder("organizerId")))
                .andExpect(jsonPath(ORGANIZER_PATH + "/staff-invitations'].post.responses['201']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/StaffInvitationResponse"))
                .andExpect(jsonPath("$.components.schemas.InviteOperatorRequest.required", containsInAnyOrder("email")))
                .andExpect(jsonPath("$.components.schemas.StaffInvitationResponse.required", containsInAnyOrder(
                        "invitationId", "inviteeEmail", "invitedAt", "expiresAt")));
    }

    @Test
    @DisplayName("운영진 목록은 운영진마다 계정 id · 닉네임 · 역할 · 소속 일시를 돌려준다")
    void staffListOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ORGANIZER_PATH + "/staff'].get.responses['200']"
                        + ".content['application/json'].schema['$ref']").value("#/components/schemas/StaffListResponse"))
                .andExpect(jsonPath("$.components.schemas.StaffListResponse.required", containsInAnyOrder("members")))
                .andExpect(jsonPath("$.components.schemas.StaffMember.required", containsInAnyOrder(
                        "staffAccountId", "nickname", "role", "since")))
                .andExpect(jsonPath("$.components.schemas.StaffMember.properties.role.enum",
                        containsInAnyOrder("OWNER", "OPERATOR")));
    }

    @Test
    @DisplayName("내보내기는 경로의 organizerId · staffAccountId를, 나가기는 organizerId만 받고 본문 없이 204다")
    void removalAndDepartureOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ORGANIZER_PATH + "/staff/{staffAccountId}/removal'].post.parameters[*].name",
                        containsInAnyOrder("organizerId", "staffAccountId")))
                .andExpect(jsonPath(ORGANIZER_PATH + "/staff/{staffAccountId}/removal'].post.responses['204']").exists())
                .andExpect(jsonPath(ORGANIZER_PATH + "/staff/{staffAccountId}/removal'].post.requestBody").doesNotExist())
                .andExpect(jsonPath(ORGANIZER_PATH + "/departure'].post.parameters[*].name",
                        containsInAnyOrder("organizerId")))
                .andExpect(jsonPath(ORGANIZER_PATH + "/departure'].post.responses['204']").exists());
    }

    @Test
    @DisplayName("소유자 넘기기는 경로의 organizerId · staffAccountId(받을 운영자)만 받고 본문 없이 204다")
    void ownershipTransferOperation() throws Exception {
        String transferPath = ORGANIZER_PATH + "/staff/{staffAccountId}/ownership-transfer']";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(transferPath + ".post.parameters[*].name",
                        containsInAnyOrder("organizerId", "staffAccountId")))
                .andExpect(jsonPath(transferPath + ".post.responses['204']").exists())
                .andExpect(jsonPath(transferPath + ".post.requestBody").doesNotExist());
    }

    @Test
    @DisplayName("받은 초대 수락은 경로의 invitationId만 받아 200에 들어간 주최자를 돌려주고, 거절은 204다")
    void answerOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(INVITATION_PATH + "/acceptance'].post.parameters[*].name",
                        containsInAnyOrder("invitationId")))
                .andExpect(jsonPath(INVITATION_PATH + "/acceptance'].post.responses['200']"
                        + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AcceptStaffInvitationResponse"))
                .andExpect(jsonPath("$.components.schemas.AcceptStaffInvitationResponse.required", containsInAnyOrder(
                        "organizerId", "organizerName", "joinedAt")))
                .andExpect(jsonPath(INVITATION_PATH + "/declination'].post.responses['204']").exists());
    }
}
