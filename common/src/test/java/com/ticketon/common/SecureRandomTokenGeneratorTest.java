package com.ticketon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("추측 불가 값 생성 (SecureRandom)")
class SecureRandomTokenGeneratorTest {

    private static final int SAMPLE_SIZE = 10_000;

    private final TokenGenerator generator = new SecureRandomTokenGenerator();

    @Test
    @DisplayName("URL 안전 값은 요청한 길이이고 base64url 문자만 쓴다")
    void urlSafeLengthAndAlphabet() {
        String token = generator.urlSafe(22);

        assertThat(token).hasSize(22);
        assertThat(token).matches("[A-Za-z0-9_-]{22}");
    }

    @Test
    @DisplayName("숫자열은 요청한 길이이고 숫자만 쓴다")
    void digitsLengthAndAlphabet() {
        String token = generator.digits(12);

        assertThat(token).hasSize(12);
        assertThat(token).matches("[0-9]{12}");
    }

    @Test
    @DisplayName("길이가 1보다 작으면 거부한다")
    void nonPositiveLengthRejected() {
        assertThatThrownBy(() -> generator.urlSafe(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.digits(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("URL 안전 값 1만 개를 만들면 중복이 없다")
    void urlSafeTokensAreUnique() {
        Set<String> tokens = generate(() -> generator.urlSafe(22));

        assertThat(tokens).hasSize(SAMPLE_SIZE);
    }

    @Test
    @DisplayName("숫자열 1만 개를 만들면 중복이 없다")
    void digitTokensAreUnique() {
        Set<String> tokens = generate(() -> generator.digits(16));

        assertThat(tokens).hasSize(SAMPLE_SIZE);
    }

    @Test
    @DisplayName("1만 개를 만들면 알파벳의 모든 문자가 나온다 (특정 문자만 쓰지 않는다)")
    void everyCharacterOfAlphabetAppears() {
        Set<Character> urlSafeCharacters = charactersOf(generate(() -> generator.urlSafe(22)));
        Set<Character> digitCharacters = charactersOf(generate(() -> generator.digits(16)));

        assertThat(urlSafeCharacters).isEqualTo(charactersOf(Set.of(TokenGenerator.URL_SAFE_ALPHABET)));
        assertThat(digitCharacters).isEqualTo(charactersOf(Set.of(TokenGenerator.DIGITS)));
    }

    private static Set<String> generate(Supplier<String> tokenSupplier) {
        Set<String> tokens = new HashSet<>();
        IntStream.range(0, SAMPLE_SIZE).forEach(index -> tokens.add(tokenSupplier.get()));
        return tokens;
    }

    private static Set<Character> charactersOf(Set<String> tokens) {
        return tokens.stream()
                .flatMapToInt(String::chars)
                .mapToObj(character -> (char) character)
                .collect(Collectors.toSet());
    }
}
