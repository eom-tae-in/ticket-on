package com.ticketon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("테스트용 고정 생성기")
class FixedTokenGeneratorTest {

    @Test
    @DisplayName("넣어 둔 값을 부른 순서대로 돌려준다")
    void returnsPreparedValuesInOrder() {
        TokenGenerator generator = new FixedTokenGenerator("qrKey-01", "0091");

        assertThat(generator.urlSafe(8)).isEqualTo("qrKey-01");
        assertThat(generator.digits(4)).isEqualTo("0091");
    }

    @Test
    @DisplayName("값을 다 쓰면 실패한다")
    void failsWhenExhausted() {
        TokenGenerator generator = new FixedTokenGenerator("0091");
        generator.digits(4);

        assertThatThrownBy(() -> generator.digits(4)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("요청한 길이와 다른 값이면 실패한다")
    void failsOnLengthMismatch() {
        TokenGenerator generator = new FixedTokenGenerator("0091");

        assertThatThrownBy(() -> generator.digits(6)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("요청한 문자 집합에 없는 문자가 있으면 실패한다")
    void failsOnAlphabetMismatch() {
        TokenGenerator digitsRequested = new FixedTokenGenerator("00a1");
        TokenGenerator urlSafeRequested = new FixedTokenGenerator("qr/key");

        assertThatThrownBy(() -> digitsRequested.digits(4)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> urlSafeRequested.urlSafe(6)).isInstanceOf(IllegalStateException.class);
    }
}
