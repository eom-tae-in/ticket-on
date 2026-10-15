package com.ticketon.api.security;

import com.ticketon.api.error.UnauthenticatedException;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@link CurrentStaff}가 붙은 인자에 로그인한 운영진과 경로의 주최자 id로 만든 {@link ActingStaff}를 넣는다.
 * <p>
 * 인자 타입이 {@code ActingStaff}가 아니거나 경로에 {@code {organizerId}}가 없으면 개발 중 실수이므로 조용히 넘기지 않고 예외를 던진다.
 */
final class CurrentStaffArgumentResolver implements HandlerMethodArgumentResolver {

    /** 주최자 하나의 일을 두는 경로 — 주최자 id를 담는다. */
    static final String ORGANIZER_PATH = "/api/organizer/organizers/{organizerId}";

    /** 경로에서 주최자 id를 담는 변수 이름. API 명세의 경로 파라미터 이름도 이것이다. */
    static final String ORGANIZER_ID_VARIABLE = "organizerId";

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentStaff.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        if (parameter.getParameterType() != ActingStaff.class) {
            throw new IllegalStateException("@CurrentStaff는 ActingStaff 인자에만 붙인다: " + parameter);
        }
        AccountRef account = AccountAuthentication.accountOf(securityContextHolderStrategy.getContext().getAuthentication())
                .orElseThrow(UnauthenticatedException::new);
        if (account.kind() != AccountKind.STAFF) {
            throw new AccessDeniedException("주최 관리는 운영진 계정만 합니다: " + account.principalName());
        }
        return new ActingStaff(account, organizerIdOf(parameter, webRequest));
    }

    private static long organizerIdOf(MethodParameter parameter, NativeWebRequest webRequest) {
        String value = pathVariablesOf(webRequest).get(ORGANIZER_ID_VARIABLE);
        if (value == null) {
            throw new IllegalStateException("@CurrentStaff는 경로에 {" + ORGANIZER_ID_VARIABLE + "}가 있는 주소에만 붙인다: " + parameter);
        }
        try {
            long organizerId = Long.parseLong(value);
            if (organizerId >= 1) {
                return organizerId;
            }
            throw invalidOrganizerId(value, parameter, null);
        } catch (NumberFormatException exception) {
            throw invalidOrganizerId(value, parameter, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariablesOf(NativeWebRequest webRequest) {
        Object variables = webRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return variables == null ? Map.of() : (Map<String, String>) variables;
    }

    /** 숫자가 아니거나 1보다 작은 주최자 id — 경로 파라미터 형식 오류(400 INVALID_INPUT, 칸 organizerId)로 응답한다. */
    private static MethodArgumentTypeMismatchException invalidOrganizerId(String value, MethodParameter parameter,
            NumberFormatException cause) {
        return new MethodArgumentTypeMismatchException(value, long.class, ORGANIZER_ID_VARIABLE, parameter, cause);
    }
}
