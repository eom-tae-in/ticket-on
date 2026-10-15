package com.ticketon.api.security;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link CurrentAccount} · {@link CurrentStaff} 인자 해석기를 등록한다.
 * <p>
 * 로그인 주체는 세션에서 오므로 API 명세에 요청 파라미터로 싣지 않는다. 실으면 화면 타입에 주체를 보내는 칸이 생긴다.
 * {@link CurrentStaff}는 경로의 주최자 id를 함께 읽으므로, 그 인자를 받는 API에는 경로 파라미터 {@code organizerId}(정수, 1 이상)를
 * 대신 싣는다 — 화면 타입이 주최자 id를 경로에 넣게.
 */
@Configuration(proxyBeanMethods = false)
public class CurrentAccountConfiguration implements WebMvcConfigurer {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentAccount.class, CurrentStaff.class);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentAccountArgumentResolver());
        resolvers.add(new CurrentStaffArgumentResolver());
    }

    /** {@link CurrentStaff} 인자를 받는 API에 경로 파라미터 {@code organizerId}를 싣는다. */
    @Bean
    public OperationCustomizer organizerIdPathParameterCustomizer() {
        return (operation, handlerMethod) -> {
            if (actsAsStaff(handlerMethod) && !hasOrganizerIdParameter(operation)) {
                operation.addParametersItem(organizerIdPathParameter());
            }
            return operation;
        };
    }

    private static boolean actsAsStaff(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(CurrentStaff.class));
    }

    private static boolean hasOrganizerIdParameter(Operation operation) {
        return operation.getParameters() != null && operation.getParameters().stream()
                .anyMatch(parameter -> CurrentStaffArgumentResolver.ORGANIZER_ID_VARIABLE.equals(parameter.getName()));
    }

    private static Parameter organizerIdPathParameter() {
        return new PathParameter()
                .name(CurrentStaffArgumentResolver.ORGANIZER_ID_VARIABLE)
                .description("주최자 id — 주최자 전환으로 고른, 지금 일하는 주최자")
                .schema(new IntegerSchema().format("int64").minimum(BigDecimal.ONE));
    }
}
