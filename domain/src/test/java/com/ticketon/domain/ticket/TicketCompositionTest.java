package com.ticketon.domain.ticket;

import static com.ticketon.domain.ticket.TicketFixtures.BOOKING_ID;
import static com.ticketon.domain.ticket.TicketFixtures.BOOKING_NUMBER;
import static com.ticketon.domain.ticket.TicketFixtures.KEY;
import static com.ticketon.domain.ticket.TicketFixtures.SEAT_12_7;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MARCH_14;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_3;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_4;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_5;
import static com.ticketon.domain.ticket.TicketFixtures.ZONE_R;
import static com.ticketon.domain.ticket.TicketFixtures.ZONE_STANDING_A;
import static com.ticketon.domain.ticket.TicketFixtures.sessionPrice;
import static com.ticketon.domain.ticket.TicketFixtures.undiscounted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.pricing.DiscountKind;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.TicketPrice;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("티켓 구성 — 회차 1개 이상 · 구역 · 좌석 0~1개, 금액 · 할인 · 열쇠는 직접 가짐 (docs/07, docs/14 \"3층 — 티켓\")")
class TicketCompositionTest {

    @Nested
    @DisplayName("구조 하나로 셋이 설명된다 (docs/14)")
    class ThreeShapes {

        @Test
        @DisplayName("일반 지정석 — 회차 1개 · 좌석 1개")
        void designated() {
            Ticket ticket = TicketFixtures.designatedTicket();

            assertThat(ticket.sessionIds()).containsExactly(SESSION_MARCH_14);
            assertThat(ticket.seatId()).hasValue(SEAT_12_7);
            assertThat(ticket.zoneId()).isEqualTo(ZONE_R);
            assertThat(ticket.isBundle()).isFalse();
        }

        @Test
        @DisplayName("일반 수량형 — 회차 1개 · 좌석 없음, 번호는 0091-2")
        void quantity() {
            Ticket ticket = TicketFixtures.quantityTicket();

            assertThat(ticket.sessionIds()).containsExactly(SESSION_MARCH_14);
            assertThat(ticket.seatId()).isEmpty();
            assertThat(ticket.number().value()).isEqualTo("0091-2");
            assertThat(ticket.isBundle()).isFalse();
        }

        @Test
        @DisplayName("묶음권 — 회차 3개 · 좌석 없음, QR 열쇠는 하나 (docs/07 \"3일권 1장 = QR 1개\")")
        void bundle() {
            Ticket ticket = TicketFixtures.festivalPass();

            assertThat(ticket.sessionIds()).containsExactly(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_5);
            assertThat(ticket.seatId()).isEmpty();
            assertThat(ticket.isBundle()).isTrue();
            assertThat(ticket.qrKey()).isEqualTo(KEY);
        }
    }

    @Nested
    @DisplayName("발급")
    class Issue {

        @Test
        @DisplayName("예매 티켓 — 결제 금액 · 적용 할인을 직접 갖고, 처음에는 유효 · 면제 없음 · 돌려준 돈 없음")
        void bookingTicket() {
            Ticket ticket = TicketFixtures.couponPass();

            assertThat(ticket.id()).isEmpty();
            assertThat(ticket.parent()).isEqualTo(TicketParent.booking(BOOKING_ID, BOOKING_NUMBER));
            assertThat(ticket.paidAmount()).isEqualTo(Money.of(89_500));
            assertThat(ticket.appliedDiscountKind()).isEqualTo(DiscountKind.COUPON);
            assertThat(ticket.appliedDiscountAmount()).isEqualTo(Money.of(5_000));
            assertThat(ticket.partiallyRefundedAmount()).isEqualTo(Money.ZERO);
            assertThat(ticket.isExemptFromDeduction()).isFalse();
            assertThat(ticket.isCompensatedOnCancel()).isFalse();
            assertThat(ticket.isCancelled()).isFalse();
            assertThat(ticket.cancellation()).isEmpty();
        }

        @Test
        @DisplayName("묶음권은 날짜마다 산 때의 회차 가격을 갖는다 — 주최자가 가격을 바꿔도 그날 몫이 그대로다 (docs/04 \"가격 — 신규 예매부터\")")
        void bundleKeepsDayPrices() {
            Ticket ticket = TicketFixtures.festivalPass();

            assertThat(ticket.sessions()).extracting(session -> session.purchasedPrice().orElseThrow())
                    .containsExactly(sessionPrice(40_000), sessionPrice(50_000), sessionPrice(50_000));
        }

        @Test
        @DisplayName("초대권 티켓 — 초대권 번호 + 순번, 결제가 없어 금액 · 할인이 비어 있다")
        void invitationTicket() {
            Ticket ticket = TicketFixtures.invitationTicket();

            assertThat(ticket.parent().kind()).isEqualTo(TicketParentKind.INVITATION);
            assertThat(ticket.number().value()).isEqualTo("5521-1");
            assertThat(ticket.price()).isEmpty();
            assertThat(ticket.paidAmount()).isEqualTo(Money.ZERO);
            assertThat(ticket.appliedDiscountKind()).isEqualTo(DiscountKind.NONE);
            assertThat(ticket.sessions()).allSatisfy(session -> assertThat(session.purchasedPrice()).isEmpty());
        }

        @Test
        @DisplayName("회차가 없거나 같은 회차를 두 번 가리킬 수 없다")
        void requiresDistinctSessions() {
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.issueForInvitation(TicketFixtures.INVITATION_ID,
                    TicketFixtures.INVITATION_NUMBER, 1, ZONE_STANDING_A, OptionalLong.empty(), List.of(), KEY));
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.issueForInvitation(TicketFixtures.INVITATION_ID,
                    TicketFixtures.INVITATION_NUMBER, 1, ZONE_STANDING_A, OptionalLong.empty(),
                    List.of(SESSION_MAY_3, SESSION_MAY_3), KEY));
        }

        @Test
        @DisplayName("좌석이 있으면 회차는 하나다 — 지정형은 묶음권이 불가하다 (docs/99 B \"지정석 묶음권\")")
        void seatMeansOneSession() {
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 1, ZONE_R,
                    OptionalLong.of(SEAT_12_7),
                    List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(50_000)),
                            TicketSession.purchased(SESSION_MAY_4, sessionPrice(50_000))),
                    undiscounted(93_000), KEY));
        }

        @Test
        @DisplayName("일반 티켓의 기본 가격은 그 회차 가격이다")
        void singleBasePriceIsSessionPrice() {
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 1, ZONE_R,
                    OptionalLong.of(SEAT_12_7), List.of(TicketSession.purchased(SESSION_MARCH_14, sessionPrice(50_000))),
                    undiscounted(60_000), KEY));
        }

        @Test
        @DisplayName("묶음권 가격은 날짜별 가격 합계를 넘지 않는다")
        void bundlePriceWithinDayTotal() {
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 1,
                    ZONE_STANDING_A, OptionalLong.empty(),
                    List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(40_000)),
                            TicketSession.purchased(SESSION_MAY_4, sessionPrice(50_000))),
                    undiscounted(90_100), KEY));
        }

        @Test
        @DisplayName("예매 티켓의 회차는 산 때의 가격이 있어야 하고, 포기 표시를 달고 발급되지 않는다")
        void bookingSessionsHavePricesAndNoForfeit() {
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 1,
                    ZONE_STANDING_A, OptionalLong.empty(), List.of(TicketSession.invited(SESSION_MARCH_14)),
                    undiscounted(30_000), KEY));
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 1,
                    ZONE_STANDING_A, OptionalLong.empty(),
                    List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(40_000)),
                            TicketSession.restore(SESSION_MAY_4, sessionPrice(50_000), Instant.parse("2026-05-01T00:00:00Z"))),
                    undiscounted(87_300), KEY));
        }
    }

    @Nested
    @DisplayName("되살리기 — 구성 규칙을 다시 본다")
    class Restore {

        private final Instant cancelledAt = Instant.parse("2026-03-05T05:20:00Z");

        @Test
        @DisplayName("저장한 칸을 그대로 되살린다")
        void restoresEverything() {
            TicketCancellation cancellation = new TicketCancellation(cancelledAt, TicketCancellationReason.AUDIENCE_CANCELLATION);

            Ticket ticket = Ticket.restore(7L, TicketParent.booking(BOOKING_ID, BOOKING_NUMBER), 3, List.of(
                    TicketSession.purchased(SESSION_MARCH_14, sessionPrice(50_000))), ZONE_R, SEAT_12_7, undiscounted(50_000),
                    Money.ZERO, Set.of(DeductionExemptionCause.PERFORMER_CHANGE, DeductionExemptionCause.POSTPONEMENT), true,
                    cancellation, KEY);

            assertThat(ticket.id()).hasValue(7L);
            assertThat(ticket.number().value()).isEqualTo("0091-3");
            assertThat(ticket.deductionExemptionCauses())
                    .containsExactlyInAnyOrder(DeductionExemptionCause.PERFORMER_CHANGE, DeductionExemptionCause.POSTPONEMENT);
            assertThat(ticket.isCompensatedOnCancel()).isTrue();
            assertThat(ticket.cancellation()).contains(cancellation);
        }

        @Test
        @DisplayName("초대권 티켓에는 금액 · 돌려준 돈 · 면제 · 배상이 없다")
        void invitationHasNoMoney() {
            TicketParent invitation = TicketParent.invitation(TicketFixtures.INVITATION_ID, TicketFixtures.INVITATION_NUMBER);
            List<TicketSession> sessions = List.of(TicketSession.invited(SESSION_MARCH_14));

            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L, invitation, 1, sessions, ZONE_R, SEAT_12_7,
                    undiscounted(50_000), Money.ZERO, Set.of(), false, null, KEY));
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L, invitation, 1, sessions, ZONE_R, SEAT_12_7,
                    null, Money.of(1_000), Set.of(), false, null, KEY));
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L, invitation, 1, sessions, ZONE_R, SEAT_12_7,
                    null, Money.ZERO, Set.of(DeductionExemptionCause.POSTPONEMENT), false, null, KEY));
        }

        @Test
        @DisplayName("부모에 붙을 수 없는 취소 사유는 받지 않는다 — 예매 티켓에 '초대권 회수', 초대권 티켓에 '관객 취소'")
        void reasonMatchesParent() {
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L, TicketParent.booking(BOOKING_ID, BOOKING_NUMBER),
                    1, List.of(TicketSession.purchased(SESSION_MARCH_14, sessionPrice(50_000))), ZONE_R, SEAT_12_7,
                    undiscounted(50_000), Money.ZERO, Set.of(), false,
                    new TicketCancellation(cancelledAt, TicketCancellationReason.INVITATION_RECALLED), KEY));
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L,
                    TicketParent.invitation(TicketFixtures.INVITATION_ID, TicketFixtures.INVITATION_NUMBER), 1,
                    List.of(TicketSession.invited(SESSION_MARCH_14)), ZONE_R, SEAT_12_7, null, Money.ZERO, Set.of(), false,
                    new TicketCancellation(cancelledAt, TicketCancellationReason.AUDIENCE_CANCELLATION), KEY));
        }

        @Test
        @DisplayName("포기 표시는 묶음권에만, 묶음권에는 출연자 변경 면제가 없고, 배상은 출연자 변경으로만 붙는다 (docs/04)")
        void refundRightsFitTogether() {
            TicketParent booking = TicketParent.booking(BOOKING_ID, BOOKING_NUMBER);
            Instant forfeitedAt = Instant.parse("2026-05-02T00:00:00Z");
            List<TicketSession> pass = List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(40_000)),
                    TicketSession.purchased(SESSION_MAY_4, sessionPrice(50_000)));

            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L, booking, 1,
                    List.of(TicketSession.restore(SESSION_MARCH_14, sessionPrice(50_000), forfeitedAt)), ZONE_R, SEAT_12_7,
                    undiscounted(50_000), Money.ZERO, Set.of(), false, null, KEY));
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L, booking, 1, pass, ZONE_STANDING_A, null,
                    undiscounted(87_300), Money.ZERO, Set.of(DeductionExemptionCause.PERFORMER_CHANGE), false, null, KEY));
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L, booking, 1,
                    List.of(TicketSession.purchased(SESSION_MARCH_14, sessionPrice(50_000))), ZONE_R, SEAT_12_7,
                    undiscounted(50_000), Money.ZERO, Set.of(DeductionExemptionCause.POSTPONEMENT), true, null, KEY));
        }

        @Test
        @DisplayName("이미 돌려준 부분 금액은 결제 금액을 넘지 않는다")
        void partialRefundWithinPaid() {
            assertThatIllegalArgumentException().isThrownBy(() -> Ticket.restore(7L, TicketParent.booking(BOOKING_ID, BOOKING_NUMBER),
                    1, List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(40_000)),
                            TicketSession.purchased(SESSION_MAY_4, sessionPrice(50_000))),
                    ZONE_STANDING_A, null, new TicketPrice(Money.of(87_300), DiscountKind.NONE, Money.ZERO), Money.of(87_301),
                    Set.of(), false, null, KEY));
        }
    }
}
