package com.ticketon.api.config;

import com.ticketon.common.KoreanTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스 기준 시계 (한국 시각, Asia/Seoul, 1마이크로초 단위).
 * <p>
 * application 서비스는 이 Clock을 주입받아 현재 시각을 만들고 도메인 메서드에 인자로 넘긴다(application의 package-info).
 * <p>
 * 시각은 {@link KoreanTime#PRECISION}(1마이크로초) 단위로 끊는다. PostgreSQL {@code timestamptz}는 마이크로초까지만 저장하는데,
 * Linux 시스템 시계는 나노초까지 읽힌다. 끊지 않으면 방금 만든 도메인 객체로 응답한 시각(나노초)과 DB에서 다시 읽은 같은 시각(마이크로초)이
 * 달라진다(#207). macOS 시계는 원래 마이크로초라 로컬에서는 드러나지 않는다.
 * <p>
 * 테스트에서 시각을 고정하거나 옮기려면 {@code @TestBean(name = "clock")}으로 MutableClock을 넣는다.
 * 칸 타입은 Clock으로 둔다 — MutableClock이면 {@code @SpringBootTest}에서 컨텍스트가 뜨지 않는다(ClockOverrideTest).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.system(KoreanTime.ZONE), KoreanTime.PRECISION);
    }
}
