package com.ticketon.api.config;

import com.ticketon.common.WebAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 화면(웹) 기준 주소. 서비스가 주입받아 메일·문자에 넣을 링크(이메일 인증 등)를 만든다.
 * <p>
 * 값은 설정 {@code ticketon.web.base-url}(환경 변수 WEB_BASE_URL)에서 읽는다. 개발 기본값은 로컬 화면 주소이고,
 * 운영 프로필은 기본값 없이 환경 변수를 꼭 받는다(application-prod.yml) — 운영 메일에 로컬 주소가 나가지 않게.
 */
@Configuration(proxyBeanMethods = false)
public class WebAddressConfiguration {

    @Bean
    public WebAddress webAddress(@Value("${ticketon.web.base-url}") String baseUrl) {
        return WebAddress.of(baseUrl);
    }
}
