package com.ticketon.api.error;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 명세에 {@link ErrorResponse} 스키마를 늘 싣는다.
 * <p>
 * API마다 붙는 공통 오류 응답(400·401·403·404·409·500)은 springdoc이 {@link ApiExceptionHandler}의
 * {@code @ResponseStatus}를 읽어 만든다. 다만 그 스키마는 API가 하나라도 있어야 생기므로, API가 없을 때도
 * 화면 타입 생성(openapi-typescript)에 오류 타입이 나오도록 여기서 넣어 둔다. 이미 있으면 그대로 둔다.
 */
@Configuration(proxyBeanMethods = false)
public class ErrorResponseOpenApiConfiguration {

    @Bean
    public OpenApiCustomizer errorResponseSchemaCustomizer() {
        return openApi -> {
            Components components = componentsOf(openApi);
            errorResponseSchemas(openApi.getSpecVersion()).forEach((name, schema) -> {
                if (components.getSchemas() == null || !components.getSchemas().containsKey(name)) {
                    components.addSchemas(name, schema);
                }
            });
        };
    }

    private static Components componentsOf(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        return openApi.getComponents();
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Schema> errorResponseSchemas(SpecVersion specVersion) {
        return ModelConverters.getInstance(specVersion == SpecVersion.V31)
                .readAll(new AnnotatedType(ErrorResponse.class));
    }
}
