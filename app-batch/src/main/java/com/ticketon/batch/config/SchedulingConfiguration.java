package com.ticketon.batch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 정해진 시각 작업을 켠다(Spring 스케줄러 — docs/16 "정해진 시각 작업"). 작업마다의 시각은 각 작업이 등록한다.
 * <p>
 * {@code ticketon.batch.scheduling.enabled=false}면 켜지 않는다 — 통합 테스트가 작업을 직접 부르는 동안 실제 시각에 맞춰 작업이 끼어들지 않게 한다.
 * 설정하지 않으면 켠다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "ticketon.batch.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
