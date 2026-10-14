package com.ticketon.api.security;

import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link CurrentAccount} 인자 해석기를 등록한다.
 * <p>
 * 로그인 주체는 세션에서 오므로 API 명세에 요청 파라미터로 싣지 않는다. 실으면 화면 타입에 주체를 보내는 칸이 생긴다.
 */
@Configuration(proxyBeanMethods = false)
public class CurrentAccountConfiguration implements WebMvcConfigurer {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentAccount.class);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentAccountArgumentResolver());
    }
}
