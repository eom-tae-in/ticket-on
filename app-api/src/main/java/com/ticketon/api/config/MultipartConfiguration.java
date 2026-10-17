package com.ticketon.api.config;

import com.ticketon.domain.policy.ProposedPolicy;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * 파일 올리기(multipart) 요청의 크기 울타리 — 파일 하나와 요청 전체 모두 {@link ProposedPolicy#UPLOAD_REQUEST_MAXIMUM_SIZE}(확인 필요)까지 받는다.
 * <p>
 * 값은 정책 숫자 모음에서 가져온다. 설정 파일에 숫자를 따로 적지 않는다(.claude/rules/planning-docs.md "한곳에 모은다"). 이 빈이 있으면 Spring
 * Boot는 spring.servlet.multipart.max-file-size · max-request-size 대신 이 값을 쓴다. 울타리를 넘는 요청은 400 INVALID_INPUT이다
 * (ApiExceptionHandler). 파일마다의 상한(로고 2MB · 포스터 5MB)은 울타리 안에서 Request 검증이 보고 칸 옆에 까닭을 알려 준다.
 */
@Configuration(proxyBeanMethods = false)
public class MultipartConfiguration {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        DataSize limit = DataSize.ofBytes(ProposedPolicy.UPLOAD_REQUEST_MAXIMUM_SIZE.bytes());
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(limit);
        factory.setMaxRequestSize(limit);
        return factory.createMultipartConfig();
    }
}
