package com.ticketon.api.config;

import com.ticketon.common.KoreanTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스 기준 시계 (한국 시각, Asia/Seoul).
 * <p>
 * application 서비스는 이 Clock을 주입받아 현재 시각을 만들고 도메인 메서드에 인자로 넘긴다(application의 package-info).
 * 테스트에서 시각을 고정하거나 옮기려면 {@code @TestBean(name = "clock")}으로 MutableClock을 넣는다.
 * 칸 타입은 Clock으로 둔다 — MutableClock이면 {@code @SpringBootTest}에서 컨텍스트가 뜨지 않는다(ClockOverrideTest).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.system(KoreanTime.ZONE);
    }
}
