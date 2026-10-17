package com.ticketon.domain.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.SeatLabel;
import com.ticketon.domain.event.SeatNumber;
import com.ticketon.domain.event.ZoneName;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("티켓 표기 — 지정석은 구역 + 좌석 표기, 수량형은 구역 · 티켓 번호 (docs/07 \"티켓 표기\")")
class TicketLabelTest {

    private static final ZoneName R = ZoneName.of("R석");
    private static final ZoneName STANDING_A = ZoneName.of("스탠딩A");
    private static final SeatNumber ROW_12_SEAT_7 = SeatNumber.of(12, 7);

    @Nested
    @DisplayName("티켓 한 줄")
    class OneLine {

        @Test
        @DisplayName("블록 1개 — R석 12열 7번")
        void designatedSingleBlock() {
            SeatLabel seat = new PointedSeat(BlockName.of("1층"), ROW_12_SEAT_7, false).label(1);

            assertThat(TicketFixtures.designatedTicket().label(R, Optional.of(seat)).value()).isEqualTo("R석 12열 7번");
        }

        @Test
        @DisplayName("블록 여러 개 — R석 1층 12열 7번")
        void designatedManyBlocks() {
            SeatLabel seat = new PointedSeat(BlockName.of("1층"), ROW_12_SEAT_7, false).label(2);

            assertThat(TicketFixtures.designatedTicket().label(R, Optional.of(seat)).value()).isEqualTo("R석 1층 12열 7번");
        }

        @Test
        @DisplayName("수량형 — 스탠딩A · 0091-2")
        void quantity() {
            assertThat(TicketFixtures.quantityTicket().label(STANDING_A, Optional.empty()).value()).isEqualTo("스탠딩A · 0091-2");
        }

        @Test
        @DisplayName("묶음권도 수량형이라 구역 · 티켓 번호다 — 스탠딩A · 0091-1")
        void bundle() {
            assertThat(TicketFixtures.festivalPass().label(STANDING_A, Optional.empty()).value()).isEqualTo("스탠딩A · 0091-1");
        }

        @Test
        @DisplayName("초대권 수량형은 초대권 번호 + 순번이다")
        void invitationQuantity() {
            assertThat(TicketFixtures.invitationPass().label(STANDING_A, Optional.empty()).value()).isEqualTo("스탠딩A · 5521-1");
        }

        @Test
        @DisplayName("블록 이름 오타를 고치면 이미 팔린 표의 표기도 따라 바뀐다 — 티켓은 손대지 않는다")
        void followsBlockRename() {
            Ticket ticket = TicketFixtures.designatedTicket();
            SeatLabel before = new PointedSeat(BlockName.of("1칭"), ROW_12_SEAT_7, false).label(2);
            SeatLabel after = new PointedSeat(BlockName.of("1층"), ROW_12_SEAT_7, false).label(2);

            assertThat(ticket.label(R, Optional.of(before)).value()).isEqualTo("R석 1칭 12열 7번");
            assertThat(ticket.label(R, Optional.of(after)).value()).isEqualTo("R석 1층 12열 7번");
        }

        @Test
        @DisplayName("지정석은 좌석 표기가 있어야 하고, 수량형은 좌석 표기가 없어야 한다")
        void seatLabelMatchesSeat() {
            SeatLabel seat = new PointedSeat(BlockName.of("1층"), ROW_12_SEAT_7, false).label(1);

            assertThatIllegalArgumentException().isThrownBy(() -> TicketFixtures.designatedTicket().label(R, Optional.empty()));
            assertThatIllegalArgumentException().isThrownBy(() -> TicketFixtures.quantityTicket().label(STANDING_A, Optional.of(seat)));
        }
    }

    @Nested
    @DisplayName("지운 블록의 좌석 — 취소 · 회수된 티켓만 남은 블록 (#37 인계)")
    class RemovedBlock {

        @Test
        @DisplayName("지운 블록의 좌석은 남은 블록이 하나여도 블록 이름을 붙인다 — 남은 블록의 같은 번호 좌석과 섞이지 않게")
        void alwaysNamesRemovedBlock() {
            PointedSeat removed = new PointedSeat(BlockName.of("2층"), ROW_12_SEAT_7, true);

            assertThat(removed.label(1).value()).isEqualTo("2층 12열 7번");
            assertThat(removed.label(0).value()).isEqualTo("2층 12열 7번");
            assertThat(removed.label(3).value()).isEqualTo("2층 12열 7번");
        }

        @Test
        @DisplayName("살아 있는 블록의 좌석은 지금 블록 수를 따른다 — 다른 블록을 지워 하나만 남으면 이름을 뺀다")
        void liveBlockFollowsCurrentCount() {
            PointedSeat live = new PointedSeat(BlockName.of("1층"), ROW_12_SEAT_7, false);

            assertThat(live.label(1).value()).isEqualTo("12열 7번");
            assertThat(live.label(2).value()).isEqualTo("1층 12열 7번");
            assertThatIllegalArgumentException().isThrownBy(() -> live.label(0));
        }
    }
}
