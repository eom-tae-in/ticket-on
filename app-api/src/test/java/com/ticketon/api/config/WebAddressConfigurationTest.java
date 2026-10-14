package com.ticketon.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.WebAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@DisplayName("API 서버의 화면 기준 주소 빈")
class WebAddressConfigurationTest {

    @Nested
    @SpringBootTest(classes = WebAddressConfiguration.class, webEnvironment = WebEnvironment.NONE)
    @DisplayName("설정하지 않으면")
    class DefaultValue {

        @Autowired
        private WebAddress webAddress;

        @Test
        @DisplayName("개발 기본값인 로컬 화면 주소다")
        void defaultsToLocalWeb() {
            assertThat(webAddress.link("/verify-email", "token", "abc"))
                    .isEqualTo("http://localhost:3000/verify-email?token=abc");
        }
    }

    @Nested
    @SpringBootTest(classes = WebAddressConfiguration.class, webEnvironment = WebEnvironment.NONE,
            properties = "ticketon.web.base-url=https://ticket-on.example/")
    @DisplayName("설정하면")
    class ConfiguredValue {

        @Autowired
        private WebAddress webAddress;

        @Test
        @DisplayName("그 주소로 링크를 만든다")
        void usesConfiguredBase() {
            assertThat(webAddress.link("/verify-email", "token", "abc"))
                    .isEqualTo("https://ticket-on.example/verify-email?token=abc");
        }
    }
}
