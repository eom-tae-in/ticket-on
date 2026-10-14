package com.ticketon.batch.config;

import com.ticketon.common.WebAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 화면(웹) 기준 주소. 배치 작업이 부르는 서비스도 메일·문자에 링크를 넣는다. 값은 API 서버와 같은 설정
 * {@code ticketon.web.base-url}(환경 변수 WEB_BASE_URL)에서 읽는다.
 */
@Configuration(proxyBeanMethods = false)
public class WebAddressConfiguration {

    @Bean
    public WebAddress webAddress(@Value("${ticketon.web.base-url}") String baseUrl) {
        return WebAddress.of(baseUrl);
    }
}
