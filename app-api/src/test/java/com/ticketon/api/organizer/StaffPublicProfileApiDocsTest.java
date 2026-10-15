package com.ticketon.api.organizer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.GetStaffPublicProfileUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 운영진 프로필의 응답 모양이 명세에 그대로 실리고, 실명 · 이메일 칸이 없는지 본다.
 * DB 없이 돈다.
 */
@WebMvcTest(controllers = StaffPublicProfileController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("운영진 프로필 API 명세")
class StaffPublicProfileApiDocsTest {

    private static final String PATH = "$.paths['/api/public/staff-profiles/{staffAccountId}']";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetStaffPublicProfileUseCase getStaffPublicProfileUseCase;

    @Test
    @DisplayName("운영진 계정 id 경로 하나로 GET하면 200에 StaffPublicProfileResponse를 돌려준다")
    void getProfileOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PATH + ".get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/StaffPublicProfileResponse"))
                .andExpect(jsonPath(PATH + ".get.parameters[*].name", containsInAnyOrder("staffAccountId")));
    }

    @Test
    @DisplayName("응답은 nickname · avatar · participatedOrganizers가 필수이고 introduction은 비어 있을 수 있다 — 이름 · 이메일 칸은 없다")
    void responseSchemaHasNoPersonalInformation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.StaffPublicProfileResponse.required",
                        containsInAnyOrder("nickname", "avatar", "participatedOrganizers")))
                .andExpect(jsonPath("$.components.schemas.StaffPublicProfileResponse.properties.introduction").exists())
                .andExpect(jsonPath("$.components.schemas.StaffPublicProfileResponse.properties.name").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.StaffPublicProfileResponse.properties.email").doesNotExist());
    }

    @Test
    @DisplayName("참여한 주최자 한 줄은 주최자 id · 주최자명 · 역할 · 시작 일시 · 활동 종료가 필수이고, 종료 일시는 지금 소속이면 비어 있다")
    void participatedOrganizerSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ParticipatedOrganizer.required", containsInAnyOrder(
                        "organizerId", "organizerName", "role", "startedAt", "activityEnded")))
                .andExpect(jsonPath("$.components.schemas.ParticipatedOrganizer.properties.endedAt").exists())
                .andExpect(jsonPath("$.components.schemas.ParticipatedOrganizer.properties.role.enum",
                        containsInAnyOrder("OWNER", "OPERATOR")));
    }
}
