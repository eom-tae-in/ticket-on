package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주최자 상태 — 공개되는지")
class OrganizerStatusTest {

    @Test
    @DisplayName("활동 · 닫힘은 공개된다 — 닫힌 주최자는 \"활동 종료\" 배지로 페이지가 남는다 (docs/01 \"탈퇴와 주최자 닫기\")")
    void activeAndClosedArePublic() {
        assertThat(OrganizerStatus.ACTIVE.isPublic()).isTrue();
        assertThat(OrganizerStatus.CLOSED.isPublic()).isTrue();
    }

    @Test
    @DisplayName("영구 정지는 페이지가 내려가고, 심사 중(반려돼 남은 것 포함)은 승인 전이라 공개되지 않는다")
    void suspendedAndUnderReviewAreNotPublic() {
        assertThat(OrganizerStatus.PERMANENTLY_SUSPENDED.isPublic()).isFalse();
        assertThat(OrganizerStatus.UNDER_REVIEW.isPublic()).isFalse();
    }
}
