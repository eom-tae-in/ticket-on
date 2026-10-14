package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("운영진 닉네임")
class NicknameTest {

    @Test
    @DisplayName("적은 값에서 앞뒤 공백을 걷는다 — 가운데 공백은 그대로 둔다")
    void stripsSurroundingSpaces() {
        assertThat(Nickname.of("  조용한고래 ").value()).isEqualTo("조용한고래");
        assertThat(Nickname.of(" 조용한 고래 ").value()).isEqualTo("조용한 고래");
    }

    @Test
    @DisplayName("자모를 늘어놓은 꼴(NFD)로 적은 한글도 완성형(NFC)으로 맞춰 같은 닉네임이 된다 — 화면에서 똑같이 보이므로")
    void normalizesToComposedForm() {
        String decomposed = Normalizer.normalize("푸른바람", Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo("푸른바람");

        assertThat(Nickname.of(decomposed)).isEqualTo(Nickname.of("푸른바람"));
        assertThat(Nickname.of(decomposed).value()).isEqualTo("푸른바람");
    }

    @Test
    @DisplayName("대소문자는 가린다 — 눈으로 구분되는 다른 닉네임이다")
    void caseSensitive() {
        assertThat(Nickname.of("Whale")).isNotEqualTo(Nickname.of("whale"));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "\t", " "})
    @DisplayName("필수다 — 공백을 걷으면 비는 값은 받지 않는다")
    void rejectsBlank(String input) {
        assertThat(Nickname.isValid(input)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> Nickname.of(input));
    }

    @Test
    @DisplayName("값이 없으면 만들 수 없다")
    void rejectsNull() {
        assertThat(Nickname.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("공백이 아닌 글자가 하나라도 있으면 받는다")
    void acceptsNonBlank() {
        assertThat(Nickname.isValid(" 푸른바람 ")).isTrue();
        assertThat(Nickname.isValid("a")).isTrue();
    }

    @Test
    @DisplayName("최대 길이는 20자다 (확인 필요 #15) — 20자는 받고 21자는 받지 않는다")
    void maximumLengthIsTwenty() {
        assertThat(ProposedPolicy.NICKNAME_MAXIMUM_LENGTH).isEqualTo(20);
        String twenty = "가".repeat(20);

        assertThat(Nickname.of(twenty).value()).isEqualTo(twenty);
        assertThat(Nickname.isValid(twenty + "가")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> Nickname.of(twenty + "가"));
    }

    @Test
    @DisplayName("앞뒤 공백은 길이에 넣지 않는다 — 걷은 뒤 센다")
    void surroundingSpacesDoNotCount() {
        assertThat(Nickname.isValid("  " + "가".repeat(20) + "  ")).isTrue();
    }

    @Test
    @DisplayName("자모를 늘어놓은 꼴(NFD)로 적은 한글 20자도 완성형으로 맞춘 뒤 세므로 받는다")
    void countsAfterComposing() {
        String decomposed = Normalizer.normalize("조".repeat(20), Normalizer.Form.NFD);
        assertThat(decomposed.length()).isEqualTo(40);

        assertThat(Nickname.of(decomposed).value()).isEqualTo("조".repeat(20));
    }

    @Test
    @DisplayName("글자 수는 사람이 세는 대로 센다 — 이모지 하나가 한 자다")
    void countsCodePoints() {
        String twentyEmoji = "\uD83D\uDC33".repeat(20);
        assertThat(twentyEmoji.length()).isEqualTo(40);

        assertThat(Nickname.isValid(twentyEmoji)).isTrue();
        assertThat(Nickname.isValid(twentyEmoji + "\uD83D\uDC33")).isFalse();
    }
}
