package com.ticketon.api.security;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). {@link CurrentStaff} 인자는 세션의 주체를 보내는 칸으로 싣지 않고, 대신
 * 경로 파라미터 organizerId(정수, 1 이상)가 실려 화면 타입이 주최자 id를 경로에 넣는지 본다.
 */
@WebMvcTest(controllers = StaffAreaTestController.class)
@Import({StaffAreaTestController.class, SecurityConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("API 명세의 운영진 · 주최자 인자")
class CurrentStaffApiDocsTest {

    private static final String ACTING_STAFF_OPERATION = "$.paths['/api/organizer/organizers/{organizerId}/test'].get";

    private static final String WITH_RESOURCE_OPERATION =
            "$.paths['/api/organizer/organizers/{organizerId}/test/events/{eventId}'].get";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckStaffPermissionUseCase checkStaffPermissionUseCase;

    @Test
    @DisplayName("@CurrentStaff 인자 대신 경로 파라미터 organizerId 하나만 싣는다 — 정수(int64), 1 이상, 필수")
    void documentsOrganizerIdPathParameter() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ACTING_STAFF_OPERATION + ".parameters.length()").value(1))
                .andExpect(jsonPath(ACTING_STAFF_OPERATION + ".parameters[0].name").value("organizerId"))
                .andExpect(jsonPath(ACTING_STAFF_OPERATION + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(ACTING_STAFF_OPERATION + ".parameters[0].required").value(true))
                .andExpect(jsonPath(ACTING_STAFF_OPERATION + ".parameters[0].schema.type").value("integer"))
                .andExpect(jsonPath(ACTING_STAFF_OPERATION + ".parameters[0].schema.format").value("int64"))
                .andExpect(jsonPath(ACTING_STAFF_OPERATION + ".parameters[0].schema.minimum").value(1))
                .andExpect(jsonPath(ACTING_STAFF_OPERATION + ".requestBody").doesNotExist());
    }

    @Test
    @DisplayName("경로의 다른 자원 id와 함께 실린다")
    void documentsOrganizerIdBesideOtherPathParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(WITH_RESOURCE_OPERATION + ".parameters[*].name",
                        containsInAnyOrder("organizerId", "eventId")))
                .andExpect(jsonPath(WITH_RESOURCE_OPERATION + ".parameters[*].in", containsInAnyOrder("path", "path")));
    }
}
