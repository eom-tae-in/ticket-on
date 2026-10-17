package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("쿠폰 코드 — SPRING20 같은 문자열 (docs/05 \"쿠폰\", 확인 필요 #49)")
class CouponCodeTest {

    @Test
    @DisplayName("앞뒤 공백을 걷고 영문을 대문자로 맞춘다 — spring20 · Spring20 · ' SPRING20 '은 같은 코드")
    void normalizesCase() {
        assertThat(CouponCode.of("spring20")).isEqualTo(CouponCode.of("SPRING20"));
        assertThat(CouponCode.of("Spring20")).isEqualTo(CouponCode.of("SPRING20"));
        assertThat(CouponCode.of("  SPRING20 ").value()).isEqualTo("SPRING20");
    }

    @Test
    @DisplayName("한글은 NFC로 맞춘다 — 자모로 적어도 완성형과 같다")
    void normalizesHangul() {
        assertThat(CouponCode.of("한글")).isEqualTo(CouponCode.of("한글"));
    }

    @Test
    @DisplayName("빈 값과 안에 공백이 있는 값은 코드가 아니다")
    void rejectsBlankAndInnerWhitespace() {
        assertThatIllegalArgumentException().isThrownBy(() -> CouponCode.of("   "));
        assertThatIllegalArgumentException().isThrownBy(() -> CouponCode.of("SPRING 20"));
        assertThat(CouponCode.isValid("SPRING 20")).isFalse();
        assertThat(CouponCode.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("20자까지 받는다 — 21자는 받지 않는다")
    void maximumLength() {
        assertThat(ProposedPolicy.COUPON_CODE_MAXIMUM_LENGTH).isEqualTo(20);
        assertThat(CouponCode.isValid("A".repeat(20))).isTrue();
        assertThat(CouponCode.isValid("A".repeat(21))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> CouponCode.of("A".repeat(21)));
    }

    @Test
    @DisplayName("관객이 적은 값이 코드가 될 수 없으면 비어 있다 — 형식 오류를 따로 알리지 않는다")
    void parseInput() {
        assertThat(CouponCode.parseInput("spring20")).contains(CouponCode.of("SPRING20"));
        assertThat(CouponCode.parseInput("")).isEmpty();
        assertThat(CouponCode.parseInput("A".repeat(21))).isEmpty();
        assertThat(CouponCode.parseInput(null)).isEmpty();
    }
}
