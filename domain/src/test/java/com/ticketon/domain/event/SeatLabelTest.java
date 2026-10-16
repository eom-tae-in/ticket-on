package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("좌석 표기 — 블록이 하나면 '12열 7번', 여러 개면 '1층 12열 7번' (docs/02 \"좌석 번호 표기\", docs/07 \"티켓 표기\")")
class SeatLabelTest {

    @Test
    @DisplayName("블록 1개 — 12열 7번")
    void singleBlock() {
        assertThat(SeatLabel.of(BlockName.of("1층"), SeatNumber.of(12, 7), 1).value()).isEqualTo("12열 7번");
    }

    @Test
    @DisplayName("블록 여러 개 — 1층 12열 7번")
    void severalBlocks() {
        assertThat(SeatLabel.of(BlockName.of("1층"), SeatNumber.of(12, 7), 2).value()).isEqualTo("1층 12열 7번");
        assertThat(SeatLabel.of(BlockName.of("1층"), SeatNumber.of(12, 7), 3).value()).isEqualTo("1층 12열 7번");
    }

    @Test
    @DisplayName("블록이 하나면 자동 이름도 빠진다 — '블록 1 12열 7번'은 정보가 없다")
    void automaticNameOmittedWhenSingle() {
        assertThat(SeatLabel.of(BlockName.automatic(1), SeatNumber.of(12, 7), 1).value()).isEqualTo("12열 7번");
    }

    @Test
    @DisplayName("좌석이 있는 도면의 블록 수는 1 이상이다")
    void blockCountAtLeastOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> SeatLabel.of(BlockName.of("1층"), SeatNumber.of(12, 7), 0));
    }
}
