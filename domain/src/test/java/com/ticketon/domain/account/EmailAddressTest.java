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

    @Test
    @DisplayName("대소문자는 적은 그대로 둔다")
    void keepsCase() {
        assertThat(EmailAddress.of("Seoyun@Example.com").value()).isEqualTo("Seoyun@Example.com");
    }

    @Test
    @DisplayName("254자까지 받고 255자부터 거부한다 — 메일 전송 규약의 주소 길이 한도")
    void rejectsLongerThanMaxLength() {
        String domain = "@example.com";
        String longest = "a".repeat(EmailAddress.MAX_LENGTH - domain.length()) + domain;

        assertThat(EmailAddress.MAX_LENGTH).isEqualTo(254);
        assertThat(EmailAddress.of(longest).value()).hasSize(254);
        assertThat(EmailAddress.isValid(longest)).isTrue();
        assertThatThrownBy(() -> EmailAddress.of("a" + longest)).isInstanceOf(IllegalArgumentException.class);
        assertThat(EmailAddress.isValid("a" + longest)).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"seoyun@example.com", "  seoyun@example.com  "})
    @DisplayName("입력 검증은 만들 때와 같은 규칙이다 — 앞뒤 공백은 걷고 본다")
    void isValidAcceptsWhatOfAccepts(String input) {
        assertThat(EmailAddress.isValid(input)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "seoyun", "seoyun@", "@example.com", "seo yun@example.com", "seoyun@@example.com"})
    @DisplayName("만들 때 거부하는 값은 입력 검증에서도 거부한다")
    void isValidRejectsWhatOfRejects(String input) {
        assertThat(EmailAddress.isValid(input)).isFalse();
    }

    @Test
    @DisplayName("입력 검증은 값이 없으면 거부한다")
    void isValidRejectsNull() {
        assertThat(EmailAddress.isValid(null)).isFalse();
    }
}
