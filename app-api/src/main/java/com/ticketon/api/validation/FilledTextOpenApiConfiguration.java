package com.ticketon.api.validation;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 명세에 {@link FilledText} 칸의 최소 길이(minLength 1)를 싣는다.
 * <p>
 * springdoc은 {@code @NotBlank}를 읽어 minLength 1을 싣지만 직접 만든 {@link FilledText}는 모른다. 빈 칸을 받지 않는다는 뜻은 같으므로
 * 명세에도 같게 싣는다 — 칸의 검증을 {@code @NotBlank}에서 {@link FilledText}로 바꿔도 명세는 바뀌지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class FilledTextOpenApiConfiguration {

    @Bean
    public PropertyCustomizer filledTextMinimumLengthCustomizer() {
        return (property, type) -> {
            if (hasFilledText(type.getCtxAnnotations())) {
                property.setMinLength(1);
            }
            return property;
        };
    }

    private static boolean hasFilledText(Annotation[] annotations) {
        return annotations != null && Arrays.stream(annotations).anyMatch(FilledText.class::isInstance);
    }
}
