package com.ticketon.api.organizer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.organizer.port.in.RenameOrganizerUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 주최자명 변경의 요청 · 응답 모양이 명세에 그대로 실리는지 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = OrganizerNameController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("주최자명 변경 API 명세")
class OrganizerNameApiDocsTest {

    private static final String NAME_PATH = "$.paths['/api/organizer/organizers/{organizerId}/name']";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RenameOrganizerUseCase renameOrganizerUseCase;

    @Test
    @DisplayName("주최자명 바꾸기는 경로의 organizerId와 name(필수)을 받아 200에 OrganizerNameResponse(organizerId · name)를 돌려준다")
    void renameOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(NAME_PATH + ".put.parameters[*].name", containsInAnyOrder("organizerId")))
                .andExpect(jsonPath(NAME_PATH + ".put.requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/RenameOrganizerRequest"))
                .andExpect(jsonPath(NAME_PATH + ".put.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/OrganizerNameResponse"))
                .andExpect(jsonPath("$.components.schemas.RenameOrganizerRequest.required", containsInAnyOrder("name")))
                .andExpect(jsonPath("$.components.schemas.OrganizerNameResponse.required",
                        containsInAnyOrder("organizerId", "name")));
    }
}
