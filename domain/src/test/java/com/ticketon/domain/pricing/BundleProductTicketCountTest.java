package com.ticketon.domain.pricing;

import static com.ticketon.domain.pricing.BundleProductFixtures.FESTIVAL_EVENT_ID;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_4;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_5;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalThreeDayPass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.SeatsPerSelectionLimit;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("묶음권 매수 세는 법 — 1인 총 매수는 날짜마다 1매, 1회 선택 매수는 티켓 장수 (docs/05 \"매수 제한 계산\")")
class BundleProductTicketCountTest {

    private static final BundleProduct THREE_DAY_PASS = BundleProduct.restore(11L, FESTIVAL_EVENT_ID, festivalThreeDayPass());

    @Nested
    @DisplayName("1인 총 매수")
    class TicketsPerPerson {

        @Test
        @DisplayName("날짜별 — 3일권 1장이 5/3에 1매, 5/4에 1매, 5/5에 1매를 차지한다")
        void onePerDate() {
            assertThat(THREE_DAY_PASS.ticketsPerPersonCountByDate(1)).containsExactlyInAnyOrderEntriesOf(Map.of(MAY_3, 1, MAY_4, 1, MAY_5, 1));
        }

        @Test
        @DisplayName("날짜별 — 3일권 4장은 날짜마다 4매, 기본 한도 4매 · 날짜별에 꼭 맞는다")
        void fourPassesFillDefaultLimit() {
            Map<Long, Integer> counts = THREE_DAY_PASS.ticketsPerPersonCountByDate(4);

            assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(MAY_3, 4, MAY_4, 4, MAY_5, 4));
            TicketsPerPersonLimit.Limited defaultLimit = (TicketsPerPersonLimit.Limited) TicketsPerPersonLimit.defaultLimit();
            assertThat(counts.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(defaultLimit.maxTickets()));
        }

        @Test
        @DisplayName("행사 전체 — 날짜마다 1매를 더해 3일권 1장은 3매, 2장은 6매")
        void sumOfDatesInEvent() {
            assertThat(THREE_DAY_PASS.ticketsPerPersonCountInEvent(1)).isEqualTo(3);
            assertThat(THREE_DAY_PASS.ticketsPerPersonCountInEvent(2)).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("1회 선택 매수 (docs/99 D \"1회 선택 매수의 묶음권\")")
    class SeatsPerSelection {

        @Test
        @DisplayName("티켓 장수로 센다 — 3일권 4장은 12매가 아니라 4매")
        void ticketCount() {
            assertThat(THREE_DAY_PASS.seatsPerSelectionCount(4)).isEqualTo(4);
        }

        @Test
        @DisplayName("기본 4석이면 3일권 4장을 한 번에 담을 수 있다 — 친구 몫까지 대표로 예매")
        void fitsDefaultLimit() {
            assertThat(THREE_DAY_PASS.seatsPerSelectionCount(4)).isLessThanOrEqualTo(SeatsPerSelectionLimit.defaultLimit().maxSeats());
            assertThat(THREE_DAY_PASS.seatsPerSelectionCount(5)).isGreaterThan(SeatsPerSelectionLimit.defaultLimit().maxSeats());
        }
    }

    @Test
    @DisplayName("장수는 1 이상이다")
    void positiveTicketCount() {
        assertThatIllegalArgumentException().isThrownBy(() -> THREE_DAY_PASS.ticketsPerPersonCountByDate(0));
        assertThatIllegalArgumentException().isThrownBy(() -> THREE_DAY_PASS.ticketsPerPersonCountInEvent(0));
        assertThatIllegalArgumentException().isThrownBy(() -> THREE_DAY_PASS.seatsPerSelectionCount(0));
    }
}
