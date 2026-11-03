package com.ticketon.api.event;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import com.ticketon.application.event.port.in.DuplicateEventUseCase;
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
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). 행사 복제가 요청 본문 없이 경로만으로 부르고, 201에 위저드 값과 같은 모양을 돌려주는지
 * 명세로 본다. DB 없이 돈다.
 */
@WebMvcTest(controllers = EventDuplicationController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("행사 복제 API 명세")
class EventDuplicationApiDocsTest {

    private static final String DUPLICATION_PATH =
            "$.paths['/api/organizer/organizers/{organizerId}/events/{eventId}/duplication']";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DuplicateEventUseCase duplicateEventUseCase;

    @Test
    @DisplayName("행사 복제는 주최자 · 행사 id 경로만 받고 요청 본문이 없으며, 201에 EventWizardResponse를 돌려준다")
    void operation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DUPLICATION_PATH + ".post.parameters[*].name", containsInAnyOrder("organizerId", "eventId")))
                .andExpect(jsonPath(DUPLICATION_PATH + ".post.requestBody").doesNotExist())
                .andExpect(jsonPath(DUPLICATION_PATH + ".post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/EventWizardResponse"));
    }
}
