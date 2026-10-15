package com.ticketon.api.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면 타입은 /v3/api-docs에서 생성된다(npm run api:types). {@link FilledText} 칸이 {@code @NotBlank} 칸과 같은 모양으로 명세에 실리는지 본다.
 * DB 없이 돈다.
 */
@WebMvcTest(controllers = FilledTextApiDocsTest.SampleController.class)
@Import({FilledTextApiDocsTest.SampleController.class, FilledTextOpenApiConfiguration.class, SecurityConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
@DisplayName("OpenAPI 명세의 꼭 적어야 하는 글 칸(FilledText)")
class FilledTextApiDocsTest {

    private static final String SAMPLE_REQUEST = "$.components.schemas.FilledTextSampleRequest";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("FilledText 칸은 @NotBlank 칸처럼 minLength 1로 실린다 — 검증을 @NotBlank에서 FilledText로 바꿔도 명세는 그대로다")
    void filledTextIsPublishedLikeNotBlank() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SAMPLE_REQUEST + ".properties.filled.minLength").value(1))
                .andExpect(jsonPath(SAMPLE_REQUEST + ".properties.notBlank.minLength").value(1))
                .andExpect(jsonPath(SAMPLE_REQUEST + ".properties.optional.minLength").doesNotExist());
    }

    @RestController
    @RequestMapping("/test/filled-text")
    static class SampleController {

        @PostMapping
        void submit(@Valid @RequestBody FilledTextSampleRequest request) {
        }
    }

    record FilledTextSampleRequest(
            @FilledText(message = "적어 주세요.") String filled,
            @NotBlank(message = "적어 주세요.") String notBlank,
            String optional) {
    }
}
