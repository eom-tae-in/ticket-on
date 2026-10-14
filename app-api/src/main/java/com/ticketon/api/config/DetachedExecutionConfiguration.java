package com.ticketon.api.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 응답과 떼어 실행하기({@link DetachedExecution})의 작업 실행기 — Spring Boot가 만드는 애플리케이션 작업 실행기(applicationTaskExecutor)를
 * 그대로 쓴다. 스레드 수 같은 값은 Spring Boot 설정(spring.task.execution.*)을 따르고, 여기서 따로 정하지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class DetachedExecutionConfiguration {

    @Bean
    public DetachedExecution detachedExecution(
            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) Executor applicationTaskExecutor) {
        return new DetachedExecution(applicationTaskExecutor);
    }
}
