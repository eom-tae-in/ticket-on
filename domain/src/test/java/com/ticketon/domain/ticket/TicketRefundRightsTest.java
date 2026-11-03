package com.ticketon.domain.ticket;

import static com.ticketon.domain.ticket.TicketFixtures.BOOKING_ID;
import static com.ticketon.domain.ticket.TicketFixtures.BOOKING_NUMBER;
import static com.ticketon.domain.ticket.TicketFixtures.KEY;
import static com.ticketon.domain.ticket.TicketFixtures.NEW_KEY;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MARCH_14;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_3;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_4;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_5;
import static com.ticketon.domain.ticket.TicketFixtures.ZONE_R;
import static com.ticketon.domain.ticket.TicketFixtures.sessionPrice;
import static com.ticketon.domain.ticket.TicketFixtures.undiscounted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.pricing.Money;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("티켓에만 붙는 권리 — 공제 면제 · 취소 시 배상 · 회차별 포기 · 그날 몫 · 기준 금액 (docs/04, docs/08)")
class TicketRefundRightsTest {

    private static final Instant NOW = Instant.parse("2026-05-01T03:00:00Z");

    @Nested
    @DisplayName("공제 면제 — 연기 · 장소 변경 · 회차 시간 수정 · 출연자 변경 (docs/04 \"공제 면제\")")
    class DeductionExemption {

        @Test
        @DisplayName("연기가 승인되면 면제가 붙고 배상은 없다")
        void postponement() {
            Ticket ticket = TicketFixtures.designatedTicket();

            boolean changed = ticket.exemptFromDeduction(DeductionExemptionCause.POSTPONEMENT);

            assertThat(changed).isTrue();
            assertThat(ticket.isExemptFromDeduction()).isTrue();
            assertThat(ticket.deductionExemptionCauses()).containsExactly(DeductionExemptionCause.POSTPONEMENT);
            assertThat(ticket.isCompensatedOnCancel()).isFalse();
        }

        @Test
        @DisplayName("출연자 변경이면 면제와 함께 취소 시 10% 배상 표시가 붙는다")
        void performerChange() {
            Ticket ticket = TicketFixtures.designatedTicket();

            ticket.exemptFromDeduction(DeductionExemptionCause.PERFORMER_CHANGE);

            assertThat(ticket.deductionExemptionCauses()).containsExactly(DeductionExemptionCause.PERFORMER_CHANGE);
            assertThat(ticket.isCompensatedOnCancel()).isTrue();
        }

        @Test
        @DisplayName("여러 번 연기해도 면제는 유지되고, 까닭이 겹쳐 붙는다 — 같은 까닭을 다시 붙이면 바뀌지 않는다")
        void causesAccumulate() {
            Ticket ticket = TicketFixtures.designatedTicket();
            ticket.exemptFromDeduction(DeductionExemptionCause.POSTPONEMENT);

            assertThat(ticket.exemptFromDeduction(DeductionExemptionCause.POSTPONEMENT)).isFalse();
            assertThat(ticket.exemptFromDeduction(DeductionExemptionCause.VENUE_CHANGE)).isTrue();
            assertThat(ticket.deductionExemptionCauses())
                    .containsExactlyInAnyOrder(DeductionExemptionCause.POSTPONEMENT, DeductionExemptionCause.VENUE_CHANGE);
        }

        @Test
        @DisplayName("연기 · 장소 변경 · 회차 시간 수정은 묶음권에도 붙는다")
        void bundleGetsNonPerformerExemptions() {
            Ticket pass = TicketFixtures.festivalPass();

            pass.exemptFromDeduction(DeductionExemptionCause.SESSION_TIME_CHANGE);

            assertThat(pass.deductionExemptionCauses()).containsExactly(DeductionExemptionCause.SESSION_TIME_CHANGE);
        }

        @Test
        @DisplayName("묶음권에는 출연자 변경 면제가 붙지 않는다 — 하루 바뀐 것으로 3일권 전체를 공제 없이 취소하게 된다")
        void bundleRejectsPerformerChange() {
            Ticket pass = TicketFixtures.festivalPass();

            assertThatIllegalStateException().isThrownBy(() -> pass.exemptFromDeduction(DeductionExemptionCause.PERFORMER_CHANGE));
            assertThat(pass.isExemptFromDeduction()).isFalse();
            assertThat(pass.isCompensatedOnCancel()).isFalse();
        }

        @Test
        @DisplayName("초대권 티켓 · 취소된 티켓에는 붙이지 않는다")
        void invitationOrCancelled() {
            Ticket invitation = TicketFixtures.invitationTicket();
            Ticket cancelled = TicketFixtures.designatedTicket();
            cancelled.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, NOW);

            assertThatIllegalStateException().isThrownBy(() -> invitation.exemptFromDeduction(DeductionExemptionCause.POSTPONEMENT));
            assertThatIllegalStateException().isThrownBy(() -> cancelled.exemptFromDeduction(DeductionExemptionCause.POSTPONEMENT));
        }
    }

    @Nested
    @DisplayName("변경으로 생긴 새 티켓에 면제가 승계된다 — 배상은 승계되지 않는다 (docs/08 \"공제 면제가 붙은 티켓\")")
    class Inheritance {

        @Test
        @DisplayName("연기된 공연 S석 50,000원(면제) → VIP 80,000원으로 변경 — 새 티켓에 면제가 따라간다")
        void exemptionFollowsChange() {
            Ticket replaced = TicketFixtures.designatedTicket();
            replaced.exemptFromDeduction(DeductionExemptionCause.POSTPONEMENT);
            replaced.exemptFromDeduction(DeductionExemptionCause.PERFORMER_CHANGE);
            replaced.cancel(TicketCancellationReason.SEAT_CHANGE, NOW);
            Ticket vip = vipTicketOfSameBooking();

            vip.inheritDeductionExemptionsFrom(replaced);

            assertThat(vip.deductionExemptionCauses())
                    .containsExactlyInAnyOrder(DeductionExemptionCause.POSTPONEMENT, DeductionExemptionCause.PERFORMER_CHANGE);
            assertThat(vip.isCompensatedOnCancel()).isFalse();
        }

        @Test
        @DisplayName("다른 예매 건의 티켓, 묶음권 · 초대권에서는 옮기지 않는다")
        void onlyWithinSameBookingSingleTickets() {
            Ticket otherBooking = Ticket.issueForBooking(134L, TicketParentNumber.of("0134"), 1, ZONE_R, OptionalLong.of(200L),
                    List.of(TicketSession.purchased(SESSION_MARCH_14, sessionPrice(50_000))), undiscounted(50_000), KEY);
            Ticket vip = vipTicketOfSameBooking();

            assertThatIllegalArgumentException().isThrownBy(() -> vip.inheritDeductionExemptionsFrom(otherBooking));
            assertThatIllegalArgumentException().isThrownBy(() -> vip.inheritDeductionExemptionsFrom(TicketFixtures.festivalPass()));
            assertThatIllegalArgumentException().isThrownBy(() -> vip.inheritDeductionExemptionsFrom(TicketFixtures.invitationTicket()));
        }

        private Ticket vipTicketOfSameBooking() {
            return Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 5, 9L, OptionalLong.of(300L),
                    List.of(TicketSession.purchased(SESSION_MARCH_14, sessionPrice(80_000))), undiscounted(80_000), NEW_KEY);
        }
    }

    @Nested
    @DisplayName("회차별 포기 — 묶음권에서 출연자가 바뀐 날만 (docs/04, docs/07)")
    class Forfeit {

        @Test
        @DisplayName("5/4를 포기하면 티켓은 그 회차를 계속 가리키고 포기 표시와 시각이 붙는다")
        void keepsPointingWithMark() {
            Ticket pass = TicketFixtures.festivalPass();

            pass.forfeitSession(SESSION_MAY_4, NOW);

            assertThat(pass.sessionIds()).containsExactly(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_5);
            assertThat(pass.isForfeited(SESSION_MAY_4)).isTrue();
            assertThat(pass.isForfeited(SESSION_MAY_3)).isFalse();
            assertThat(pass.sessions().get(1).forfeitedAt()).contains(NOW);
            assertThat(pass.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("같은 날을 두 번 포기하지 않는다")
        void cannotForfeitTwice() {
            Ticket pass = TicketFixtures.festivalPass();
            pass.forfeitSession(SESSION_MAY_4, NOW);

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> pass.forfeitSession(SESSION_MAY_4, NOW.plusSeconds(60)))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(TicketErrorCode.SESSION_ALREADY_FORFEITED));
            assertThat(pass.sessions().get(1).forfeitedAt()).contains(NOW);
        }

        @Test
        @DisplayName("취소된 묶음권은 포기하지 않는다")
        void cancelledPass() {
            Ticket pass = TicketFixtures.festivalPass();
            pass.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, NOW);

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> pass.forfeitSession(SESSION_MAY_4, NOW))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(TicketErrorCode.TICKET_ALREADY_CANCELLED));
        }

        @Test
        @DisplayName("일반 티켓 · 초대권 티켓은 포기하지 않고, 가리키지 않는 회차는 포기할 수 없다")
        void onlyBookedBundles() {
            Ticket single = TicketFixtures.quantityTicket();
            Ticket invitationPass = TicketFixtures.invitationPass();
            Ticket pass = TicketFixtures.festivalPass();

            assertThatIllegalStateException().isThrownBy(() -> single.forfeitSession(SESSION_MARCH_14, NOW));
            assertThatIllegalStateException().isThrownBy(() -> invitationPass.forfeitSession(SESSION_MAY_4, NOW));
            assertThatIllegalArgumentException().isThrownBy(() -> pass.forfeitSession(SESSION_MARCH_14, NOW));
        }
    }

    @Nested
    @DisplayName("그날 몫 = 결제 금액 × 그날 가격 ÷ 날짜별 가격 합계, 원 미만 버림 (docs/08 \"묶음권 중 하루가 취소되면\")")
    class DayShare {

        @Test
        @DisplayName("3일권 130,200원 — 토요일(5/4) 몫 = 130,200 × 50,000 ÷ 140,000 = 46,500원, 금요일 몫 = 37,200원")
        void festivalPass() {
            Ticket pass = TicketFixtures.festivalPass();

            assertThat(pass.dayShare(SESSION_MAY_4)).isEqualTo(Money.of(46_500));
            assertThat(pass.dayShare(SESSION_MAY_3)).isEqualTo(Money.of(37_200));
        }

        @Test
        @DisplayName("쿠폰을 쓴 3일권 89,500원 — 둘째 날 몫 = 89,500 × 35,000 ÷ 105,000 = 29,833원 (실제로 낸 돈 기준)")
        void couponPass() {
            assertThat(TicketFixtures.couponPass().dayShare(SESSION_MAY_4)).isEqualTo(Money.of(29_833));
        }

        @Test
        @DisplayName("하루를 이미 돌려줬어도 다른 날 몫은 처음 결제 금액으로 나눈다")
        void usesOriginalPaidAmount() {
            Ticket pass = TicketFixtures.festivalPass();
            pass.recordPartialRefund(pass.dayShare(SESSION_MAY_4));

            assertThat(pass.dayShare(SESSION_MAY_5)).isEqualTo(Money.of(46_500));
        }

        @Test
        @DisplayName("일반 티켓의 그날 몫은 결제 금액 전부다")
        void singleTicket() {
            assertThat(TicketFixtures.designatedTicket().dayShare(SESSION_MARCH_14)).isEqualTo(Money.of(50_000));
        }

        @Test
        @DisplayName("초대권 티켓은 그날 몫이 없고, 가리키지 않는 회차는 계산하지 않는다")
        void invitationOrUnknownSession() {
            assertThatIllegalStateException().isThrownBy(() -> TicketFixtures.invitationPass().dayShare(SESSION_MAY_4));
            assertThatIllegalArgumentException().isThrownBy(() -> TicketFixtures.festivalPass().dayShare(SESSION_MARCH_14));
        }
    }

    @Nested
    @DisplayName("기준 금액 = 결제 금액 − 이미 돌려준 부분 금액 (docs/08 \"기준 금액\", docs/99 D)")
    class RefundBase {

        @Test
        @DisplayName("처음에는 결제 금액이 기준이다 — 조기예매 · 쿠폰으로 줄어든 실제 결제액")
        void startsAtPaidAmount() {
            assertThat(TicketFixtures.couponPass().refundBaseAmount()).isEqualTo(Money.of(89_500));
        }

        @Test
        @DisplayName("3일권 130,200원에서 토요일 몫 46,500원을 돌려줬으면 기준은 83,700원이다 — 같은 돈을 두 번 돌려주지 않는다")
        void afterDayShare() {
            Ticket pass = TicketFixtures.festivalPass();

            pass.recordPartialRefund(Money.of(46_500));

            assertThat(pass.partiallyRefundedAmount()).isEqualTo(Money.of(46_500));
            assertThat(pass.refundBaseAmount()).isEqualTo(Money.of(83_700));
            assertThat(pass.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("공연 지연 환급(결제 금액의 10%)도 쌓인다 — 50,000원 티켓에 5,000원 → 기준 45,000원")
        void delayRefundAccumulates() {
            Ticket ticket = TicketFixtures.designatedTicket();

            ticket.recordPartialRefund(Money.of(5_000));

            assertThat(ticket.refundBaseAmount()).isEqualTo(Money.of(45_000));
        }

        @Test
        @DisplayName("0원이나 기준액보다 큰 금액은 남기지 않는다")
        void rejectsZeroOrOverBase() {
            Ticket pass = TicketFixtures.festivalPass();
            pass.recordPartialRefund(Money.of(46_500));

            assertThatIllegalArgumentException().isThrownBy(() -> pass.recordPartialRefund(Money.ZERO));
            assertThatIllegalArgumentException().isThrownBy(() -> pass.recordPartialRefund(Money.of(83_701)));
            assertThat(pass.refundBaseAmount()).isEqualTo(Money.of(83_700));
        }

        @Test
        @DisplayName("초대권 티켓 · 취소된 티켓에는 남기지 않는다 — 초대권의 기준액은 0원이다")
        void invitationOrCancelled() {
            Ticket invitation = TicketFixtures.invitationTicket();
            Ticket cancelled = TicketFixtures.designatedTicket();
            cancelled.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, NOW);

            assertThat(invitation.refundBaseAmount()).isEqualTo(Money.ZERO);
            assertThatIllegalStateException().isThrownBy(() -> invitation.recordPartialRefund(Money.of(1_000)));
            assertThatIllegalStateException().isThrownBy(() -> cancelled.recordPartialRefund(Money.of(1_000)));
        }
    }
}
