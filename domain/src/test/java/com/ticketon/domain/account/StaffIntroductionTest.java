package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("운영진 한 줄 소개")
class StaffIntroductionTest {

    @Test
    @DisplayName("적은 값에서 앞뒤 공백을 걷는다 — 가운데 공백은 그대로 둔다 (docs/01 예시 \"극단에서 연출하고 있습니다\")")
    void stripsSurroundingSpaces() {
        assertThat(StaffIntroduction.fromInput("  극단에서 연출하고 있습니다 "))
                .contains(new StaffIntroduction("극단에서 연출하고 있습니다"));
        assertThat(new StaffIntroduction(" 극단에서 연출하고 있습니다 ").value()).isEqualTo("극단에서 연출하고 있습니다");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "\t", "　"})
    @DisplayName("선택이다 — 적지 않았거나 공백뿐이면 소개가 없다")
    void blankMeansNoIntroduction(String input) {
        assertThat(StaffIntroduction.fromInput(input)).isEmpty();
        assertThat(StaffIntroduction.isAcceptable(input)).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> new StaffIntroduction(input));
    }

    @Test
    @DisplayName("적지 않은 값(null)도 소개가 없는 것이다")
    void nullMeansNoIntroduction() {
        assertThat(StaffIntroduction.fromInput(null)).isEmpty();
        assertThat(StaffIntroduction.isAcceptable(null)).isTrue();
    }

    @Test
    @DisplayName("최대 길이는 50자다 (확인 필요 #28) — 50자는 받고 51자는 받지 않는다")
    void maximumLengthIsFifty() {
        assertThat(ProposedPolicy.STAFF_INTRODUCTION_MAXIMUM_LENGTH).isEqualTo(50);
        String fifty = "가".repeat(50);

        assertThat(StaffIntroduction.fromInput(fifty)).contains(new StaffIntroduction(fifty));
        assertThat(StaffIntroduction.isAcceptable(fifty + "가")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> StaffIntroduction.fromInput(fifty + "가"));
    }

    @Test
    @DisplayName("앞뒤 공백은 길이에 넣지 않는다 — 걷은 뒤 센다")
    void surroundingSpacesDoNotCount() {
        assertThat(StaffIntroduction.isAcceptable("  " + "가".repeat(50) + "  ")).isTrue();
    }

    @Test
    @DisplayName("자모를 늘어놓은 꼴(NFD)로 적은 한글도 완성형(NFC)으로 맞춘 뒤 센다 — 같은 소개가 길이 상한에 걸렸다 안 걸렸다 하지 않게")
    void countsAfterComposing() {
        String decomposed = Normalizer.normalize("조".repeat(50), Normalizer.Form.NFD);
        assertThat(decomposed.length()).isEqualTo(100);

        assertThat(StaffIntroduction.isAcceptable(decomposed)).isTrue();
        assertThat(StaffIntroduction.fromInput(decomposed)).contains(new StaffIntroduction("조".repeat(50)));
    }

    @Test
    @DisplayName("글자 수는 사람이 세는 대로 센다 — 이모지 하나가 한 자다")
    void countsCodePoints() {
        String fiftyEmoji = "🎭".repeat(50);
        assertThat(fiftyEmoji.length()).isEqualTo(100);

        assertThat(StaffIntroduction.isAcceptable(fiftyEmoji)).isTrue();
        assertThat(StaffIntroduction.isAcceptable(fiftyEmoji + "🎭")).isFalse();
    }
}
