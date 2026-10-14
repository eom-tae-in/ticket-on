package com.ticketon.batch.config;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 무작위 수 생성기. 서비스가 주입받아 여러 후보 가운데 하나를 고를 때 쓴다(예: 운영진 [랜덤 생성] 닉네임).
 * <p>
 * 추측할 수 없어야 하는 값(링크·예매번호 등)은 이것이 아니라 TokenGenerator로 만든다. 요청 여러 개가 동시에 같은 빈을 쓰므로
 * 여러 스레드에서 써도 안전한 SecureRandom을 둔다.
 */
@Configuration(proxyBeanMethods = false)
public class RandomGeneratorConfiguration {

    @Bean
    public RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
