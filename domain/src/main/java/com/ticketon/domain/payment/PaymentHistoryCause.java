package com.ticketon.domain.payment;

import com.ticketon.domain.ticket.TicketCancellationReason;
import com.ticketon.domain.ticket.TicketParentKind;
import java.util.Objects;

/**
 * 내역 한 줄이 생긴 까닭 — 어떤 일로 돈이 움직였나. 종류({@link PaymentHistoryEntryKind})가 "돈이 어느 쪽으로 움직였나"라면 까닭은 "왜"다.
 * <table>
 *   <caption>까닭과 남는 줄</caption>
 *   <tr><th>까닭</th><th>남는 줄</th><th></th></tr>
 *   <tr><td>{@link BookingPayment 예매 결제}</td><td>결제</td><td>내역의 첫 줄, 하나뿐</td></tr>
 *   <tr><td>{@link Cancellation 티켓 취소}(취소 사유)</td><td>환불, 배상</td><td>좌석 변경이면 바로 뒤에 변경 결제가 붙는다</td></tr>
 *   <tr><td>{@link ChangePayment 변경 결제}</td><td>결제</td><td>좌석 변경 환불 바로 뒤</td></tr>
 *   <tr><td>{@link BundleDayShare 묶음권 그날 몫}(회차)</td><td>환불, 배상</td><td>주최자의 하루 취소 · 공연 중단, 출연자가 바뀐 날 포기</td></tr>
 *   <tr><td>{@link PerformanceDelay 공연 지연}(회차)</td><td>지연 환급</td><td></td></tr>
 * </table>
 * <b>변경은 차액 한 줄이 아니라 환불 한 줄 + 결제 한 줄</b>이다(docs/08 "결제·환불 내역") — 기존 티켓은 '좌석 변경'으로 취소되어 환불 줄이,
 * 새 티켓은 변경 결제 줄이 남는다. 차액 한 줄로 적으면 어느 티켓이 어떻게 바뀌었는지 사라진다. 티켓에는 "변경됨" 상태나 "이전 티켓"을 두지 않고 이
 * 두 줄이 그 이력을 가진다(docs/08 "티켓에 '변경됨' 상태를 두지 않습니다", docs/99 B "티켓 변경 사슬").
 * <p>
 * 취소 환불의 까닭은 티켓에 남은 취소 사유와 같다 — 티켓이 사유를 직접 갖고(초대권 회수처럼 환불 기록이 없는 취소가 있어서, docs/07), 내역은 그
 * 취소로 돈이 움직였을 때 같은 사유를 남긴다. 초대권 회수는 돈이 오가지 않아 내역에 없다.
 */
public sealed interface PaymentHistoryCause {

    /** 예매 결제 — 결제 승인으로 티켓을 처음 발급할 때. */
    static PaymentHistoryCause bookingPayment() {
        return new BookingPayment();
    }

    /** 변경 결제 — 변경으로 발급한 새 티켓의 가격 전액. */
    static PaymentHistoryCause changePayment() {
        return new ChangePayment();
    }

    /**
     * 티켓 취소 — 그 취소 사유로.
     *
     * @throws IllegalArgumentException 예매 티켓에 붙을 수 없는 사유(초대권 회수)면
     */
    static PaymentHistoryCause cancellation(TicketCancellationReason reason) {
        return new Cancellation(reason);
    }

    /** 묶음권 그날 몫 — 그 회차의 몫. */
    static PaymentHistoryCause bundleDayShare(long sessionId) {
        return new BundleDayShare(sessionId);
    }

    /** 공연 지연 — 그 회차가 30분 이상 늦게 시작했다. */
    static PaymentHistoryCause performanceDelay(long sessionId) {
        return new PerformanceDelay(sessionId);
    }

    /** 이 까닭으로 남을 수 있는 종류의 줄인지. */
    boolean allows(PaymentHistoryEntryKind kind);

    /** 예매 결제 — 내역의 첫 줄. 예매 건에 결제는 처음 한 번이고, 그 뒤의 결제는 모두 변경 결제다. */
    record BookingPayment() implements PaymentHistoryCause {

        @Override
        public boolean allows(PaymentHistoryEntryKind kind) {
            return kind == PaymentHistoryEntryKind.PAYMENT;
        }
    }

    /** 변경 결제 — 새 티켓은 가격 전부를 낸다(docs/08 "계산뿐 아니라 처리도 '취소 + 재예매'입니다"). 좌석 변경 환불 줄 바로 뒤에 붙는다. */
    record ChangePayment() implements PaymentHistoryCause {

        @Override
        public boolean allows(PaymentHistoryEntryKind kind) {
            return kind == PaymentHistoryEntryKind.PAYMENT;
        }
    }

    /**
     * 티켓 취소 — 취소한 티켓의 환불, 그리고 그 취소에 붙는 배상.
     * <p>
     * <b>배상이 붙는 취소</b> — 회차 취소 · 행사 취소 · 공연 중단은 주최자 사정이면 10% 배상이다(불가항력이면 관리자 확인까지 보류하고, 인정되면 없다,
     * docs/04 "불가항력이면 배상이 없습니다"). 관객 취소는 출연자 변경으로 취소 시 배상 표시가 붙은 티켓만이다(docs/08 "출연자 변경"). 관리자 조치는
     * 관객이 기한 안에 하려던 취소를 대신한 것이라 관객 취소와 같게 본다(확인 필요 #223 — docs/08 "관리자 대신 취소"는 "규정대로"라고만 한다). 좌석
     * 변경은 다른 회차를 보러 가는 것이라 배상이 없고(docs/08 "공제 면제가 붙은 티켓"), 결제 오류 환불은 낸 돈을 돌려줄 뿐이다.
     *
     * @param reason 티켓의 취소 사유 — 예매 티켓에 붙는 사유
     */
    record Cancellation(TicketCancellationReason reason) implements PaymentHistoryCause {

        public Cancellation {
            Objects.requireNonNull(reason, "reason");
            if (!reason.appliesTo(TicketParentKind.BOOKING)) {
                throw new IllegalArgumentException("돈이 오가지 않는 취소는 내역에 남지 않습니다: " + reason);
            }
        }

        @Override
        public boolean allows(PaymentHistoryEntryKind kind) {
            return switch (kind) {
                case REFUND -> true;
                case COMPENSATION -> isCompensable();
                case PAYMENT, DELAY_REFUND -> false;
            };
        }

        /** 좌석 변경인지 — 환불 줄 바로 뒤에 변경 결제 줄이 붙는다. */
        public boolean isSeatChange() {
            return reason == TicketCancellationReason.SEAT_CHANGE;
        }

        /** 취소 시 배상 표시가 붙은 티켓에만 배상하는지 — 관객 취소 · 관리자 조치. 나머지 배상은 그 취소 자체가 배상 사유다. */
        public boolean compensatesOnlyMarkedTickets() {
            return reason == TicketCancellationReason.AUDIENCE_CANCELLATION || reason == TicketCancellationReason.ADMIN_ACTION;
        }

        private boolean isCompensable() {
            return switch (reason) {
                case AUDIENCE_CANCELLATION, ADMIN_ACTION, SESSION_CANCELLED, EVENT_CANCELLED, PERFORMANCE_STOPPED -> true;
                case SEAT_CHANGE, PAYMENT_ERROR_REFUND, INVITATION_RECALLED -> false;
            };
        }
    }

    /**
     * 묶음권 그날 몫 — 티켓은 그대로 두고 그 회차의 몫만 돌려준다(docs/08 "묶음권 중 하루가 취소되면"). 주최자가 그날을 취소했거나 공연이
     * 중단됐으면, 또는 출연자가 바뀐 날을 관객이 포기했으면 10% 배상이 함께 붙는다(불가항력이면 보류).
     *
     * @param sessionId 몫을 돌려준 회차 id, 1 이상
     */
    record BundleDayShare(long sessionId) implements PaymentHistoryCause {

        public BundleDayShare {
            if (sessionId < 1) {
                throw new IllegalArgumentException("회차 id는 1 이상이어야 합니다: " + sessionId);
            }
        }

        @Override
        public boolean allows(PaymentHistoryEntryKind kind) {
            return kind == PaymentHistoryEntryKind.REFUND || kind == PaymentHistoryEntryKind.COMPENSATION;
        }
    }

    /**
     * 공연 지연 — 주최자 사정으로 30분 이상 늦게 시작한 회차에 입장한 티켓마다 결제 금액의 10%(묶음권은 그날 몫의 10%)를 돌려준다. 티켓은 그대로
     * 유효하다(docs/08 "공연 지연").
     *
     * @param sessionId 늦게 시작한 회차 id, 1 이상
     */
    record PerformanceDelay(long sessionId) implements PaymentHistoryCause {

        public PerformanceDelay {
            if (sessionId < 1) {
                throw new IllegalArgumentException("회차 id는 1 이상이어야 합니다: " + sessionId);
            }
        }

        @Override
        public boolean allows(PaymentHistoryEntryKind kind) {
            return kind == PaymentHistoryEntryKind.DELAY_REFUND;
        }
    }
}
