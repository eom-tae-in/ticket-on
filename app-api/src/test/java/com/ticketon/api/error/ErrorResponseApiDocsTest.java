package com.ticketon.api.error;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * /v3/api-docs에 ErrorResponse 스키마와 공통 오류 응답이 실리는지 확인한다.
 * <p>
 * DB 없이 돌도록 웹 슬라이스에 springdoc 자동 설정만 더한다. 실제 보안 설정을 함께 올려 명세 경로가 로그인 없이 열리는지도 본다
 * (화면 타입 생성 npm run api:types가 로그인 없이 명세를 읽는다).
 */
@DisplayName("OpenAPI 명세의 공통 오류 응답")
class ErrorResponseApiDocsTest {

    private static final String ERROR_RESPONSE_REF = "#/components/schemas/ErrorResponse";

    @Nested
    @WebMvcTest(controllers = ErrorTestController.class)
    @Import({ErrorTestController.class, ErrorResponseOpenApiConfiguration.class, SecurityConfiguration.class})
    @ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
    @DisplayName("API가 있을 때")
    class WithOperations {

        @Autowired
        private MockMvc mockMvc;

        @ParameterizedTest(name = "{0} 응답의 본문은 ErrorResponse다")
        @ValueSource(strings = {"400", "401", "403", "404", "409", "500"})
        @DisplayName("모든 API에 공통 오류 응답이 ErrorResponse로 붙는다")
        void everyOperationHasCommonErrorResponses(String status) throws Exception {
            ResultActions apiDocs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

            for (String path : new String[] {"/test/errors/conflict", "/test/errors/not-found"}) {
                String response = "$.paths['%s'].get.responses['%s']".formatted(path, status);
                apiDocs.andExpect(jsonPath(response + ".content['application/json'].schema['$ref']").value(ERROR_RESPONSE_REF));
            }
        }

        @Test
        @DisplayName("ErrorResponse 스키마는 code·message·fieldErrors를 모두 필수로 갖는다")
        void errorResponseSchemaHasRequiredFields() throws Exception {
            expectErrorResponseSchemas(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        }
    }

    /** 지금 서버처럼 컨트롤러 없이 오류 처리기만 있는 상태. */
    @Nested
    @WebMvcTest(useDefaultFilters = false)
    @Import({ApiExceptionHandler.class, ErrorResponseOpenApiConfiguration.class, SecurityConfiguration.class})
    @ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
    @DisplayName("API가 하나도 없을 때")
    class WithoutOperations {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("ErrorResponse 스키마는 그래도 명세에 실린다")
        void errorResponseSchemaIsStillPublished() throws Exception {
            ResultActions apiDocs = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths").isEmpty());

            expectErrorResponseSchemas(apiDocs);
        }
    }

    private static void expectErrorResponseSchemas(ResultActions apiDocs) throws Exception {
        String errorResponse = "$.components.schemas.ErrorResponse";
        String fieldError = "$.components.schemas.FieldError";
        apiDocs.andExpect(jsonPath(errorResponse + ".required", containsInAnyOrder("code", "message", "fieldErrors")))
                .andExpect(jsonPath(errorResponse + ".properties.code.type").value("string"))
                .andExpect(jsonPath(errorResponse + ".properties.message.type").value("string"))
                .andExpect(jsonPath(errorResponse + ".properties.fieldErrors.type").value("array"))
                .andExpect(jsonPath(errorResponse + ".properties.fieldErrors.items['$ref']")
                        .value("#/components/schemas/FieldError"))
                .andExpect(jsonPath(fieldError + ".required", containsInAnyOrder("field", "message")))
                .andExpect(jsonPath(fieldError + ".properties.field.type").value("string"))
                .andExpect(jsonPath(fieldError + ".properties.message.type").value("string"));
    }
}
