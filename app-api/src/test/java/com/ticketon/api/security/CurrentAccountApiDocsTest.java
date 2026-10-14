package com.ticketon.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 주체는 세션에서 오므로 화면 타입에 주체를 보내는 칸이 생기면 안 된다. 명세(/v3/api-docs)에 요청 파라미터로 실리지 않는지 본다.
 */
@WebMvcTest(controllers = AreaTestController.class)
@Import({AreaTestController.class, SecurityConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("API 명세의 로그인 주체 인자")
class CurrentAccountApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/audience/test", "/api/public/test"})
    @DisplayName("@CurrentAccount 인자는 요청 파라미터로 싣지 않는다 — AccountRef, Optional<AccountRef> 모두")
    void currentAccountIsNotRequestParameter(String path) throws Exception {
        String operation = "$.paths['%s'].get".formatted(path);

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation).exists())
                .andExpect(jsonPath(operation + ".parameters").doesNotExist())
                .andExpect(jsonPath(operation + ".requestBody").doesNotExist());
    }
}
