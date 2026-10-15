package com.ticketon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("화면 기준 주소와 링크")
class WebAddressTest {

    @Test
    @DisplayName("기준 주소 뒤에 경로와 쿼리를 붙인다")
    void buildsLink() {
        WebAddress webAddress = WebAddress.of("https://ticket-on.example");

        assertThat(webAddress.link("/verify-email", "token", "abc-_123"))
                .isEqualTo("https://ticket-on.example/verify-email?token=abc-_123");
    }

    @Test
    @DisplayName("쿼리 없는 화면 주소는 기준 주소 뒤에 경로만 붙인다 — 경로는 '/'로 시작해야 한다")
    void buildsPage() {
        WebAddress webAddress = WebAddress.of("http://localhost:3000/");

        assertThat(webAddress.page("/oauth/result")).isEqualTo("http://localhost:3000/oauth/result");
        assertThatThrownBy(() -> webAddress.page("oauth/result")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기준 주소 끝의 '/'는 떼어 내 '//'가 생기지 않는다")
    void stripsTrailingSlash() {
        assertThat(WebAddress.of("http://localhost:3000/").link("/verify-email", "token", "abc"))
                .isEqualTo("http://localhost:3000/verify-email?token=abc");
    }

    @Test
    @DisplayName("기준 주소에 경로가 있으면 그 뒤에 붙인다")
    void keepsBasePath() {
        assertThat(WebAddress.of("https://example.com/ticket-on").link("/verify-email", "token", "abc"))
                .isEqualTo("https://example.com/ticket-on/verify-email?token=abc");
    }

    @Test
    @DisplayName("쿼리 값은 URL 인코딩한다")
    void encodesQueryValue() {
        assertThat(WebAddress.of("https://ticket-on.example").link("/search", "q", "봄 공연&1"))
                .isEqualTo("https://ticket-on.example/search?q=%EB%B4%84+%EA%B3%B5%EC%97%B0%261");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"ticket-on.example", "/verify", "ftp://ticket-on.example", "https://ticket-on.example?a=1",
            "https://ticket-on.example#top"})
    @DisplayName("http(s) 절대 주소가 아니거나 쿼리·조각이 있으면 거부한다")
    void rejectsInvalidBase(String baseUrl) {
        assertThatThrownBy(() -> WebAddress.of(baseUrl)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("경로가 '/'로 시작하지 않으면 거부한다")
    void rejectsRelativePath() {
        WebAddress webAddress = WebAddress.of("https://ticket-on.example");

        assertThatThrownBy(() -> webAddress.link("verify-email", "token", "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
