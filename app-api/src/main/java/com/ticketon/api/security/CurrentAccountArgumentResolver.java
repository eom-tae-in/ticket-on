package com.ticketon.api.security;

import com.ticketon.api.error.UnauthenticatedException;
import com.ticketon.domain.account.AccountRef;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentAccount}가 붙은 인자에 지금 로그인한 주체를 넣는다.
 * <p>
 * 인자 타입이 {@code AccountRef}나 {@code Optional<AccountRef>}가 아니면 개발 중 실수이므로 조용히 넘기지 않고 예외를 던진다.
 */
final class CurrentAccountArgumentResolver implements HandlerMethodArgumentResolver {

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAccount.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Optional<AccountRef> account = AccountAuthentication.accountOf(
                securityContextHolderStrategy.getContext().getAuthentication());
        if (isOptionalAccount(parameter)) {
            return account;
        }
        if (parameter.getParameterType() == AccountRef.class) {
            return account.orElseThrow(UnauthenticatedException::new);
        }
        throw new IllegalStateException("@CurrentAccount는 AccountRef 또는 Optional<AccountRef> 인자에만 붙인다: " + parameter);
    }

    private static boolean isOptionalAccount(MethodParameter parameter) {
        ResolvableType type = ResolvableType.forMethodParameter(parameter);
        return type.toClass() == Optional.class && type.getGeneric(0).toClass() == AccountRef.class;
    }
}
