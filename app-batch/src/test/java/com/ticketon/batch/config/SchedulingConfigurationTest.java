package com.ticketon.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

@DisplayName("정해진 시각 작업 켜기")
class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test
    @DisplayName("설정하지 않으면 스케줄러를 켠다")
    void enabledByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    @DisplayName("ticketon.batch.scheduling.enabled=false면 켜지 않는다 — 통합 테스트가 작업을 직접 부를 때")
    void disabledByProperty() {
        contextRunner.withPropertyValues("ticketon.batch.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
