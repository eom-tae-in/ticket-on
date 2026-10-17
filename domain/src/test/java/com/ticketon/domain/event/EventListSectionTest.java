package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("내 행사 목록의 세 구역 (docs/13 \"내 행사 목록\")")
class EventListSectionTest {

    @Test
    @DisplayName("작성 중 / 진행 중(판매 대기 · 판매 중 · 판매 중단 · 판매 종료) / 지난 행사(행사 종료 · 정산 완료 · 취소됨)")
    void sectionOfEachStatus() {
        assertThat(EventListSection.of(EventStatus.DRAFT)).isEqualTo(EventListSection.DRAFT);
        assertThat(EventListSection.of(EventStatus.AWAITING_OPEN)).isEqualTo(EventListSection.IN_PROGRESS);
        assertThat(EventListSection.of(EventStatus.ON_SALE)).isEqualTo(EventListSection.IN_PROGRESS);
        assertThat(EventListSection.of(EventStatus.SALES_PAUSED)).isEqualTo(EventListSection.IN_PROGRESS);
        assertThat(EventListSection.of(EventStatus.SALES_CLOSED)).isEqualTo(EventListSection.IN_PROGRESS);
        assertThat(EventListSection.of(EventStatus.ENDED)).isEqualTo(EventListSection.PAST);
        assertThat(EventListSection.of(EventStatus.SETTLED)).isEqualTo(EventListSection.PAST);
        assertThat(EventListSection.of(EventStatus.CANCELLED)).isEqualTo(EventListSection.PAST);
    }

    @Test
    @DisplayName("지난 행사 구역은 상태의 '지난 행사'와 같다")
    void pastMatchesFinishedStatuses() {
        for (EventStatus status : EventStatus.values()) {
            assertThat(EventListSection.of(status) == EventListSection.PAST).as(status.name()).isEqualTo(status.isFinished());
        }
    }
}
