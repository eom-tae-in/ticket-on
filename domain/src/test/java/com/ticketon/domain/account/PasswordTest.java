package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.domain.policy.ProposedPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("비밀번호")
class PasswordTest {

    @Nested
    @DisplayName("새 비밀번호 — 최소 규칙")
    class NewPasswordRule {

        @Test
        @DisplayName("최소 길이는 8자다 (확인 필요 #11)")
        void minimumLengthIsEight() {
            assertThat(ProposedPolicy.PASSWORD_MINIMUM_LENGTH).isEqualTo(8);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"abcd1234", "        ", "한글비밀번호입니다", "a1!b2@c3#d4$"})
        @DisplayName("8자 이상이면 조합과 상관없이 받는다 — 적은 그대로 쓴다")
        void acceptsEightOrMoreCharacters(String input) {
            assertThat(NewPassword.isValid(input)).isTrue();
            assertThat(new NewPassword(input).value()).isEqualTo(input);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "abc1234", "한글비밀번호"})
        @DisplayName("7자 이하면 거부한다")
        void rejectsShorterThanEight(String input) {
            assertThat(NewPassword.isValid(input)).isFalse();
            assertThatThrownBy(() -> new NewPassword(input)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("글자 수는 사람이 세는 대로 센다 — 이모지 7개는 7자다")
        void countsCharactersNotCodeUnits() {
            String sevenEmoji = "😀😀😀😀😀😀😀";

            assertThat(sevenEmoji.length()).isEqualTo(14);
            assertThat(NewPassword.isValid(sevenEmoji)).isFalse();
            assertThat(NewPassword.isValid(sevenEmoji + "😀")).isTrue();
        }

        @Test
        @DisplayName("값이 없으면 받지 않는다")
        void rejectsNull() {
            assertThat(NewPassword.isValid(null)).isFalse();
            assertThatThrownBy(() -> new NewPassword(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("문자열로 바꾸면 원문을 가린다 — 로그에 새지 않게")
        void toStringHidesValue() {
            assertThat(new NewPassword("abcd1234")).hasToString("NewPassword[****]");
        }
    }

    @Nested
    @DisplayName("비밀번호 해시")
    class Hash {

        @Test
        @DisplayName("비었으면 거부한다")
        void rejectsBlank() {
            assertThatThrownBy(() -> new PasswordHash(" ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PasswordHash(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("문자열로 바꾸면 값을 가린다")
        void toStringHidesValue() {
            assertThat(new PasswordHash("{pbkdf2}abc")).hasToString("PasswordHash[****]");
        }
    }
}
