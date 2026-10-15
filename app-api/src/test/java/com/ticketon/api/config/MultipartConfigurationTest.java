package com.ticketon.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.policy.ProposedPolicy;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MultipartConfiguration.class)
@DisplayName("파일 올리기 요청의 크기 울타리")
class MultipartConfigurationTest {

    @Autowired
    private MultipartConfigElement multipartConfig;

    @Test
    @DisplayName("파일 하나와 요청 전체 모두 정책 숫자 모음의 바깥 울타리(제안 10MB)까지 받는다 — 확인 필요 #29")
    void limitComesFromPolicy() {
        long limit = ProposedPolicy.UPLOAD_REQUEST_MAXIMUM_SIZE.bytes();

        assertThat(limit).isEqualTo(10L * 1024 * 1024);
        assertThat(multipartConfig.getMaxFileSize()).isEqualTo(limit);
        assertThat(multipartConfig.getMaxRequestSize()).isEqualTo(limit);
    }

    @Test
    @DisplayName("울타리는 로고 용량 상한보다 커서, 상한을 넘은 로고도 끝까지 받아 칸 옆에 까닭을 알려 줄 수 있다")
    void limitIsAboveLogoLimit() {
        assertThat(multipartConfig.getMaxFileSize())
                .isGreaterThan(ProposedPolicy.ORGANIZER_LOGO_MAXIMUM_SIZE.bytes());
    }
}
