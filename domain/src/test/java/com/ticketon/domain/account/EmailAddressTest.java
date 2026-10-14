package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("이메일 주소")
class EmailAddressTest {

    @Test
    @DisplayName("앞뒤 공백을 걷는다")
    void stripsSurroundingWhitespace() {
        assertThat(EmailAddress.of("  seoyun@example.com ").value()).isEqualTo("seoyun@example.com");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "seoyun", "seoyun@", "@example.com", "seo yun@example.com", "seoyun@@example.com",
            "seoyun@example@com"})
    @DisplayName("비었거나 '아이디@도메인' 모양이 아니면 거부한다")
    void rejectsMalformedAddress(String input) {
        assertThatThrownBy(() -> EmailAddress.of(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주소가 없으면 거부한다")
    void rejectsNull() {
        assertThatThrownBy(() -> EmailAddress.of(null)).isInstanceOf(NullPointerException.class);
    }
}
