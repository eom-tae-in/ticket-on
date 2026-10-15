package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("첫 글자 표시 (docs/01 \"공개 페이지\")")
class InitialAvatarTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @DisplayName("이름의 첫 글자를 쓴다 — 주최자명이든 닉네임이든")
    @CsvSource({
            "극단 열두달, 극",
            "조용한고래, 조",
            "'  늦여름 ', 늦",
            "the Band, t"
    })
    void usesFirstLetter(String displayName, String expectedInitial) {
        assertThat(InitialAvatar.of(displayName, 1).initial()).isEqualTo(expectedInitial);
    }

    @Test
    @DisplayName("이모지처럼 두 칸을 쓰는 글자도 한 글자로 센다")
    void countsSupplementaryCharacterAsOneLetter() {
        assertThat(InitialAvatar.of("🎭 연극반", 1).initial()).isEqualTo("🎭");
    }

    @Test
    @DisplayName("색은 8가지 중 id로 정해진다 — 같은 id는 이름을 바꿔도 같은 색")
    void colorComesFromId() {
        assertThat(InitialAvatar.of("극단 열두달", 3).paletteIndex()).isEqualTo(3);
        assertThat(InitialAvatar.of("극단 열두달 프로젝트", 3).paletteIndex()).isEqualTo(3);
        assertThat(InitialAvatar.of("극단 열두달", 11).paletteIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("이어진 id 여덟 개는 팔레트의 8가지를 한 번씩 쓴다")
    void consecutiveIdsUseEveryColor() {
        assertThat(LongStream.rangeClosed(1, 8).mapToInt(InitialAvatar::paletteIndexOf))
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("이름이 비었거나 id가 1보다 작으면 만들 수 없다")
    void rejectsInvalidInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> InitialAvatar.of("  ", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> InitialAvatar.of("극단 열두달", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new InitialAvatar("극", 8));
        assertThatIllegalArgumentException().isThrownBy(() -> new InitialAvatar("극단", 0));
    }
}
