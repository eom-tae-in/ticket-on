package com.ticketon.domain.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.pricing.Money;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("티켓 취소 — 취소 시각과 사유만 남긴다 (docs/07 \"티켓은 '취소됐나'만 기록합니다\")")
class TicketCancelTest {

    /** 3/5 14:20 — 그 시각에 취소됨 (docs/07 취소 시각 표). */
    private static final Instant MARCH_5_14_20 = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 5, 14, 20));

    @Test
    @DisplayName("취소하면 취소 시각과 사유가 남고, 티켓은 지워지지 않는다 — 번호 · 금액 · 좌석이 그대로다")
    void keepsTimeAndReason() {
        Ticket ticket = TicketFixtures.designatedTicket();

        ticket.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5_14_20);

        assertThat(ticket.isCancelled()).isTrue();
        assertThat(ticket.cancellation())
                .contains(new TicketCancellation(MARCH_5_14_20, TicketCancellationReason.AUDIENCE_CANCELLATION));
        assertThat(ticket.number().value()).isEqualTo("0091-1");
        assertThat(ticket.paidAmount()).isEqualTo(Money.of(50_000));
        assertThat(ticket.seatId()).hasValue(TicketFixtures.SEAT_12_7);
    }

    @Test
    @DisplayName("변경은 기존 티켓을 '좌석 변경'으로 취소한다 — '변경됨' 상태는 없다 (docs/14)")
    void changeCancelsWithSeatChange() {
        Ticket ticket = TicketFixtures.designatedTicket();

        ticket.cancel(TicketCancellationReason.SEAT_CHANGE, MARCH_5_14_20);

        assertThat(ticket.cancellation()).map(TicketCancellation::reason).contains(TicketCancellationReason.SEAT_CHANGE);
    }

    @Test
    @DisplayName("초대권 회수는 돈이 오가지 않아도 사유가 티켓에 남는다")
    void invitationRecallKeepsReason() {
        Ticket ticket = TicketFixtures.invitationTicket();

        ticket.cancel(TicketCancellationReason.INVITATION_RECALLED, MARCH_5_14_20);

        assertThat(ticket.cancellation()).map(TicketCancellation::reason).contains(TicketCancellationReason.INVITATION_RECALLED);
    }

    @Test
    @DisplayName("회차 취소 · 행사 취소 · 공연 중단은 초대권 티켓에도 같은 사유로 붙는다 (docs/09)")
    void organizerReasonsApplyToInvitations() {
        Ticket ticket = TicketFixtures.invitationTicket();

        ticket.cancel(TicketCancellationReason.SESSION_CANCELLED, MARCH_5_14_20);

        assertThat(ticket.cancellation()).map(TicketCancellation::reason).contains(TicketCancellationReason.SESSION_CANCELLED);
    }

    @Test
    @DisplayName("이미 취소된 티켓은 다시 취소할 수 없고, 처음 사유가 그대로 남는다")
    void cannotCancelTwice() {
        Ticket ticket = TicketFixtures.designatedTicket();
        ticket.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5_14_20);

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> ticket.cancel(TicketCancellationReason.SESSION_CANCELLED, MARCH_5_14_20.plusSeconds(60)))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(TicketErrorCode.TICKET_ALREADY_CANCELLED));
        assertThat(ticket.cancellation()).map(TicketCancellation::reason).contains(TicketCancellationReason.AUDIENCE_CANCELLATION);
    }

    @Test
    @DisplayName("부모에 붙을 수 없는 사유로는 취소하지 않는다 — 예매 티켓에 '초대권 회수', 초대권 티켓에 '관객 취소'")
    void reasonMustFitParent() {
        Ticket booking = TicketFixtures.designatedTicket();
        Ticket invitation = TicketFixtures.invitationTicket();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> booking.cancel(TicketCancellationReason.INVITATION_RECALLED, MARCH_5_14_20));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> invitation.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5_14_20));
        assertThat(booking.isCancelled()).isFalse();
        assertThat(invitation.isCancelled()).isFalse();
    }
}
