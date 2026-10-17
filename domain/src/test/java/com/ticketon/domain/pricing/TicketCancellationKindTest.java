package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("티켓 취소의 경우 — 전체 취소일 때만 쿠폰을 돌려준다 (docs/08 \"취소 직후\")")
class TicketCancellationKindTest {

    @Test
    @DisplayName("4매 중 2매는 부분 취소, 4매 모두는 전체 취소")
    void ofCancellation() {
        assertThat(TicketCancellationKind.ofCancellation(4, 2)).isEqualTo(TicketCancellationKind.PARTIAL);
        assertThat(TicketCancellationKind.ofCancellation(4, 4)).isEqualTo(TicketCancellationKind.WHOLE_BOOKING);
    }

    @Test
    @DisplayName("남은 2매를 마저 취소하면 전체 취소 (확인 필요 #49)")
    void lastRemaining() {
        assertThat(TicketCancellationKind.ofCancellation(2, 2)).isEqualTo(TicketCancellationKind.WHOLE_BOOKING);
    }

    @Test
    @DisplayName("쿠폰은 전체 취소일 때만 돌려준다 — 부분 취소 · 변경은 아니다")
    void returnsCoupon() {
        assertThat(TicketCancellationKind.WHOLE_BOOKING.returnsCoupon()).isTrue();
        assertThat(TicketCancellationKind.PARTIAL.returnsCoupon()).isFalse();
        assertThat(TicketCancellationKind.CHANGE.returnsCoupon()).isFalse();
    }

    @Test
    @DisplayName("취소할 티켓 수는 1장 이상, 살아 있는 티켓 수 이하")
    void rejectsOutOfRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> TicketCancellationKind.ofCancellation(4, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> TicketCancellationKind.ofCancellation(4, 5));
    }
}
