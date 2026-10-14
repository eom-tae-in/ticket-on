package com.ticketon.api.config;

import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 입력 검증의 '지금'을 서비스 기준 시계(한국 시각)로 맞춘다.
 * <p>
 * {@code @PastOrPresent} 같은 날짜 제약은 검증기의 시계로 '오늘'을 정한다. 기본 시계는 서버 시간대를 따르므로, 서버가 UTC면
 * 한국 날짜와 하루 어긋나는 시간대가 생긴다(한국 0~9시). 서비스가 도메인에 넘기는 시각과 같은 Clock 빈을 쓰게 해 어긋나지 않게 한다.
 * 테스트에서 Clock 빈을 고정하면 검증도 같은 시각을 본다.
 */
@Configuration(proxyBeanMethods = false)
public class ValidationClockConfiguration {

    @Bean
    public ValidationConfigurationCustomizer serviceClockForValidation(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
