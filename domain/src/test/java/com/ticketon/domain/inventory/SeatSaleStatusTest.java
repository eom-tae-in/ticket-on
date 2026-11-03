package com.ticketon.domain.inventory;

import static com.ticketon.domain.inventory.SeatSaleStatus.AVAILABLE;
import static com.ticketon.domain.inventory.SeatSaleStatus.EXCLUDED;
import static com.ticketon.domain.inventory.SeatSaleStatus.HELD;
import static com.ticketon.domain.inventory.SeatSaleStatus.INVITED;
import static com.ticketon.domain.inventory.SeatSaleStatus.SOLD;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("좌석 판매 상태 — 바뀌는 길의 표 (docs/14 \"재고는 회차에만 붙습니다\", docs/04 \"예매가 열린 뒤 수정 범위\")")
class SeatSaleStatusTest {

    @Test
    @DisplayName("상태는 빈자리 · 선점 중 · 팔림 · 판매 제외 · 초대 발권 다섯 가지다")
    void fiveStatuses() {
        assertThat(SeatSaleStatus.values()).containsExactly(AVAILABLE, HELD, SOLD, EXCLUDED, INVITED);
    }

    @Test
    @DisplayName("빈자리는 선점 · 판매 제외 · (예매 마감 뒤) 초대 발권으로 간다")
    void fromAvailable() {
        assertThat(AVAILABLE.nextStatuses()).containsExactlyInAnyOrder(HELD, EXCLUDED, INVITED);
    }

    @Test
    @DisplayName("선점 중은 풀려 빈자리로, 결제되어 팔림으로 간다")
    void fromHeld() {
        assertThat(HELD.nextStatuses()).containsExactlyInAnyOrder(AVAILABLE, SOLD);
    }

    @Test
    @DisplayName("팔린 좌석은 취소되어 빈자리로만 간다 — 판매 제외로 가지 못한다")
    void fromSold() {
        assertThat(SOLD.nextStatuses()).containsExactly(AVAILABLE);
        assertThat(SOLD.canChangeTo(EXCLUDED)).isFalse();
    }

    @Test
    @DisplayName("판매 제외는 풀려 빈자리로, 초대권이 걸려 초대 발권으로 간다")
    void fromExcluded() {
        assertThat(EXCLUDED.nextStatuses()).containsExactlyInAnyOrder(AVAILABLE, INVITED);
    }

    @Test
    @DisplayName("초대 발권은 회수되어 걸기 전의 상태(판매 제외 · 빈자리)로 간다")
    void fromInvited() {
        assertThat(INVITED.nextStatuses()).containsExactlyInAnyOrder(EXCLUDED, AVAILABLE);
    }

    @Test
    @DisplayName("자기 자신으로 가는 길은 없다")
    void noSelfTransition() {
        for (SeatSaleStatus status : SeatSaleStatus.values()) {
            assertThat(status.canChangeTo(status)).as(status.name()).isFalse();
        }
    }

    @Test
    @DisplayName("선점 · 티켓이 걸린 상태는 선점 중 · 팔림 · 초대 발권이다")
    void taken() {
        assertThat(HELD.isTaken()).isTrue();
        assertThat(SOLD.isTaken()).isTrue();
        assertThat(INVITED.isTaken()).isTrue();
        assertThat(AVAILABLE.isTaken()).isFalse();
        assertThat(EXCLUDED.isTaken()).isFalse();
    }
}
