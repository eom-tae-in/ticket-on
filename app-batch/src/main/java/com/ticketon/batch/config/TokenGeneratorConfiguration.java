package com.ticketon.batch.config;

import com.ticketon.common.SecureRandomTokenGenerator;
import com.ticketon.common.TokenGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 추측 불가 값 생성기. 서비스가 주입받아 행사 주소·예매번호·QR 열쇠·링크 값을 만들고 도메인에 인자로 넘긴다.
 */
@Configuration(proxyBeanMethods = false)
public class TokenGeneratorConfiguration {

    @Bean
    public TokenGenerator tokenGenerator() {
        return new SecureRandomTokenGenerator();
    }
}
