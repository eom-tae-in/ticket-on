package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("행사 유형과 화면별 이름 (docs/03 \"기본 정보\", docs/00 \"용어\")")
class EventTypeTest {

    @Test
    @DisplayName("유형은 공연 · 전시 · 페스티벌 셋이다")
    void threeTypes() {
        assertThat(Arrays.stream(EventType.values()).map(EventType::audienceScreenName))
                .containsExactly("공연", "전시", "페스티벌");
    }

    @ParameterizedTest(name = "{0} — 관객 화면 \"{1}\"")
    @DisplayName("관객 화면에서는 유형 이름으로 부른다")
    @CsvSource({
            "PERFORMANCE, 공연",
            "EXHIBITION, 전시",
            "FESTIVAL, 페스티벌"
    })
    void audienceScreenUsesTypeName(EventType type, String expectedName) {
        assertThat(type.nameOn(ScreenSide.AUDIENCE)).isEqualTo(expectedName);
    }

    @ParameterizedTest(name = "{0} — 주최자 화면 \"행사\"")
    @DisplayName("주최자 화면에서는 유형과 상관없이 전부 '행사'로 부른다")
    @EnumSource(EventType.class)
    void organizerScreenAlwaysSaysEvent(EventType type) {
        assertThat(type.nameOn(ScreenSide.ORGANIZER)).isEqualTo("행사");
    }
}
