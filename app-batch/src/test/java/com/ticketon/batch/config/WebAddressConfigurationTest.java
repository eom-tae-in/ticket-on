package com.ticketon.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.WebAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(classes = WebAddressConfiguration.class, webEnvironment = WebEnvironment.NONE)
@DisplayName("배치 서버의 화면 기준 주소 빈")
class WebAddressConfigurationTest {

    @Autowired
    private WebAddress webAddress;

    @Test
    @DisplayName("설정하지 않으면 API 서버와 같은 개발 기본값(로컬 화면 주소)이다")
    void defaultsToLocalWeb() {
        assertThat(webAddress.link("/verify-email", "token", "abc"))
                .isEqualTo("http://localhost:3000/verify-email?token=abc");
    }
}
