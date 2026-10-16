package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("행사 상태 8종 (docs/04 \"행사 상태\")")
class EventStatusTest {

    @Test
    @DisplayName("작성 중 · 판매 대기 · 판매 중 · 판매 중단 · 판매 종료 · 행사 종료 · 정산 완료 · 취소됨 — '매진'은 상태가 아니다")
    void eightStatusesWithoutSoldOut() {
        assertThat(EventStatus.values()).containsExactly(
                EventStatus.DRAFT, EventStatus.AWAITING_OPEN, EventStatus.ON_SALE, EventStatus.SALES_PAUSED,
                EventStatus.SALES_CLOSED, EventStatus.ENDED, EventStatus.SETTLED, EventStatus.CANCELLED);
        assertThat(Arrays.stream(EventStatus.values()).map(Enum::name)).noneMatch(name -> name.contains("SOLD_OUT"));
    }

    @Test
    @DisplayName("작성 중만 판매를 시작하기 전이다")
    void onlyDraftHasNotStartedSales() {
        assertThat(EnumSet.allOf(EventStatus.class).stream().filter(status -> !status.hasStartedSales()))
                .containsExactly(EventStatus.DRAFT);
    }

    @Test
    @DisplayName("시간이 움직이는 상태는 판매 대기 · 판매 중 · 판매 중단 · 판매 종료 — 나머지는 사람이 눌러야 움직인다")
    void statusesMovingWithTime() {
        assertThat(EnumSet.allOf(EventStatus.class).stream().filter(EventStatus::movesWithTime))
                .containsExactly(EventStatus.AWAITING_OPEN, EventStatus.ON_SALE, EventStatus.SALES_PAUSED, EventStatus.SALES_CLOSED);
    }

    @Test
    @DisplayName("지난 행사는 행사 종료 · 정산 완료 · 취소됨 (docs/13 \"내 행사 목록\")")
    void finishedStatuses() {
        assertThat(EnumSet.allOf(EventStatus.class).stream().filter(EventStatus::isFinished))
                .containsExactly(EventStatus.ENDED, EventStatus.SETTLED, EventStatus.CANCELLED);
    }

    @Test
    @DisplayName("수정 범위 — 작성 중 전부, 판매 대기 전부(초대권 예외), 판매 중 열린 뒤 범위, 판매 종료 현장만, 지난 행사 없음")
    void editScopes() {
        assertThat(EventEditScope.of(EventStatus.DRAFT, false)).isEqualTo(EventEditScope.EVERYTHING);
        assertThat(EventEditScope.of(EventStatus.AWAITING_OPEN, false)).isEqualTo(EventEditScope.EVERYTHING_EXCEPT_INVITED);
        assertThat(EventEditScope.of(EventStatus.ON_SALE, true)).isEqualTo(EventEditScope.AFTER_BOOKING_OPENED);
        assertThat(EventEditScope.of(EventStatus.SALES_CLOSED, true)).isEqualTo(EventEditScope.ON_SITE_ONLY);
        assertThat(EventEditScope.of(EventStatus.ENDED, true)).isEqualTo(EventEditScope.NOTHING);
        assertThat(EventEditScope.of(EventStatus.SETTLED, true)).isEqualTo(EventEditScope.NOTHING);
        assertThat(EventEditScope.of(EventStatus.CANCELLED, true)).isEqualTo(EventEditScope.NOTHING);
    }

    @Test
    @DisplayName("판매 중단은 예매가 한 번이라도 열렸는지로 갈린다 — 열린 적이 없으면 판매 대기와 같다")
    void pausedScopeDependsOnBookingOpened() {
        assertThat(EventEditScope.of(EventStatus.SALES_PAUSED, true)).isEqualTo(EventEditScope.AFTER_BOOKING_OPENED);
        assertThat(EventEditScope.of(EventStatus.SALES_PAUSED, false)).isEqualTo(EventEditScope.EVERYTHING_EXCEPT_INVITED);
    }

    @Test
    @DisplayName("판매가 끝난 뒤는 판매 종료와 지난 행사 — 재개 버튼이 사라진다")
    void salesOverStatuses() {
        assertThat(EnumSet.allOf(EventStatus.class).stream().filter(EventStatus::isSalesOver))
                .containsExactly(EventStatus.SALES_CLOSED, EventStatus.ENDED, EventStatus.SETTLED, EventStatus.CANCELLED);
    }
}
