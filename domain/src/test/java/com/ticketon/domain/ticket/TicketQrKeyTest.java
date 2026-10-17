package com.ticketon.domain.ticket;

import static com.ticketon.domain.ticket.TicketFixtures.KEY;
import static com.ticketon.domain.ticket.TicketFixtures.NEW_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("QR 열쇠 재발행과 공유 링크의 수명 (docs/07 \"QR 재발행\", \"공유 링크의 수명\")")
class TicketQrKeyTest {

    private static final Instant NOW = Instant.parse("2026-03-10T03:00:00Z");

    @Nested
    @DisplayName("재발행 — 열쇠만 바뀌고 티켓은 그대로, 옛 열쇠를 돌려준다")
    class Reissue {

        @Test
        @DisplayName("재발행하면 새 열쇠가 지금 열쇠가 되고, 옛 열쇠와 바뀐 시각을 돌려준다")
        void returnsPreviousKey() {
            Ticket ticket = TicketFixtures.designatedTicket();

            PreviousQrKey previous = ticket.reissueKey(NEW_KEY, NOW);

            assertThat(previous).isEqualTo(new PreviousQrKey(KEY, NOW));
            assertThat(ticket.qrKey()).isEqualTo(NEW_KEY);
            assertThat(ticket.isCurrentKey(NEW_KEY)).isTrue();
            assertThat(ticket.isCurrentKey(KEY)).isFalse();
        }

        @Test
        @DisplayName("티켓은 그대로다 — 번호 · 좌석 · 회차가 바뀌지 않는다")
        void ticketStaysTheSame() {
            Ticket ticket = TicketFixtures.festivalPass();

            ticket.reissueKey(NEW_KEY, NOW);

            assertThat(ticket.number().value()).isEqualTo("0091-1");
            assertThat(ticket.sessionIds())
                    .containsExactly(TicketFixtures.SESSION_MAY_3, TicketFixtures.SESSION_MAY_4, TicketFixtures.SESSION_MAY_5);
            assertThat(ticket.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("두 번 재발행하면 그때마다 그 직전 열쇠를 돌려준다")
        void reissueTwice() {
            Ticket ticket = TicketFixtures.designatedTicket();
            QrKey third = TicketFixtures.qrKey('C');

            ticket.reissueKey(NEW_KEY, NOW);
            PreviousQrKey second = ticket.reissueKey(third, NOW.plusSeconds(3_600));

            assertThat(second).isEqualTo(new PreviousQrKey(NEW_KEY, NOW.plusSeconds(3_600)));
            assertThat(ticket.qrKey()).isEqualTo(third);
        }

        @Test
        @DisplayName("새 열쇠가 지금 열쇠와 같으면 받지 않는다")
        void newKeyMustDiffer() {
            Ticket ticket = TicketFixtures.designatedTicket();

            assertThatIllegalArgumentException().isThrownBy(() -> ticket.reissueKey(KEY, NOW));
        }

        @Test
        @DisplayName("취소된 티켓은 재발행하지 않는다")
        void cancelledTicket() {
            Ticket ticket = TicketFixtures.designatedTicket();
            ticket.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, NOW);

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> ticket.reissueKey(NEW_KEY, NOW.plusSeconds(60)))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(TicketErrorCode.TICKET_ALREADY_CANCELLED));
            assertThat(ticket.qrKey()).isEqualTo(KEY);
        }
    }

    @Nested
    @DisplayName("공유 링크가 무효인 이유")
    class ShareLink {

        @Test
        @DisplayName("유효한 티켓의 지금 열쇠로 만든 링크는 열린다")
        void validLink() {
            assertThat(TicketFixtures.designatedTicket().shareLinkInvalidReason(KEY, EventStatus.ON_SALE)).isEmpty();
        }

        @Test
        @DisplayName("행사가 끝나기 전(판매 종료)에는 열린다")
        void salesClosedStillOpens() {
            assertThat(TicketFixtures.designatedTicket().shareLinkInvalidReason(KEY, EventStatus.SALES_CLOSED)).isEmpty();
        }

        @Test
        @DisplayName("티켓 취소 · 변경 → '취소된 티켓입니다' · 예매하신 분께 확인해 주세요.")
        void cancelled() {
            Ticket ticket = TicketFixtures.designatedTicket();
            ticket.cancel(TicketCancellationReason.SEAT_CHANGE, NOW);

            assertThat(ticket.shareLinkInvalidReason(KEY, EventStatus.ON_SALE)).contains(ShareLinkInvalidReason.CANCELLED);
            assertThat(ShareLinkInvalidReason.CANCELLED.title()).isEqualTo("취소된 티켓입니다");
            assertThat(ShareLinkInvalidReason.CANCELLED.guidance()).contains("예매하신 분께 확인해 주세요.");
        }

        @Test
        @DisplayName("QR 재발행 → 옛 열쇠로 만든 링크는 '재발행으로 바뀐 티켓입니다' · 예매하신 분께 새 링크를 받아 주세요.")
        void reissued() {
            Ticket ticket = TicketFixtures.designatedTicket();
            ticket.reissueKey(NEW_KEY, NOW);

            assertThat(ticket.shareLinkInvalidReason(KEY, EventStatus.ON_SALE)).contains(ShareLinkInvalidReason.REISSUED);
            assertThat(ticket.shareLinkInvalidReason(NEW_KEY, EventStatus.ON_SALE)).isEmpty();
            assertThat(ShareLinkInvalidReason.REISSUED.title()).isEqualTo("재발행으로 바뀐 티켓입니다");
            assertThat(ShareLinkInvalidReason.REISSUED.guidance()).contains("예매하신 분께 새 링크를 받아 주세요.");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"ENDED", "SETTLED", "CANCELLED"})
        @DisplayName("행사 종료 → '종료된 행사입니다' — 안내 줄은 없다")
        void eventEnded(EventStatus finished) {
            assertThat(TicketFixtures.designatedTicket().shareLinkInvalidReason(KEY, finished))
                    .contains(ShareLinkInvalidReason.EVENT_ENDED);
            assertThat(ShareLinkInvalidReason.EVENT_ENDED.title()).isEqualTo("종료된 행사입니다");
            assertThat(ShareLinkInvalidReason.EVENT_ENDED.guidance()).isEmpty();
        }

        @Test
        @DisplayName("겹치면 취소 → 행사 종료 → 재발행 순이다 — 취소된 티켓은 새 링크를 받아도 소용없다")
        void overlappingReasons() {
            Ticket reissuedThenCancelled = TicketFixtures.designatedTicket();
            reissuedThenCancelled.reissueKey(NEW_KEY, NOW);
            reissuedThenCancelled.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, NOW.plusSeconds(60));
            Ticket reissued = TicketFixtures.designatedTicket();
            reissued.reissueKey(NEW_KEY, NOW);

            assertThat(reissuedThenCancelled.shareLinkInvalidReason(KEY, EventStatus.ENDED))
                    .contains(ShareLinkInvalidReason.CANCELLED);
            assertThat(reissued.shareLinkInvalidReason(KEY, EventStatus.ENDED)).contains(ShareLinkInvalidReason.EVENT_ENDED);
        }
    }
}
