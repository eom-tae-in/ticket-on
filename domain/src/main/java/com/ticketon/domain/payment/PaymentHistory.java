package com.ticketon.domain.payment;

import com.ticketon.domain.payment.PaymentHistoryCause.BookingPayment;
import com.ticketon.domain.payment.PaymentHistoryCause.BundleDayShare;
import com.ticketon.domain.payment.PaymentHistoryCause.Cancellation;
import com.ticketon.domain.payment.PaymentHistoryCause.ChangePayment;
import com.ticketon.domain.payment.PaymentHistoryCause.PerformanceDelay;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.ticket.Ticket;
import com.ticketon.domain.ticket.TicketCancellation;
import com.ticketon.domain.ticket.TicketCancellationReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 예매 건의 결제·환불 내역 — <b>예매 건은 결제 단위</b>이고, 돈이 오간 이력은 이 내역이 갖는다(docs/14 "예매 건은 결제 단위입니다", docs/07
 * "예매 건과 티켓은 다릅니다"). 예매 건은 결제 이력을, 티켓은 입장 권리를 책임진다.
 * <pre>
 * 예매번호 ...0091
 * 3/1  결제    165,000원
 * 3/5  환불    −35,000원   2매 취소
 * 3/8  환불    −50,000원   12열 10번 변경
 * 3/8  결제     30,000원   15열 3번
 * ───────────────────────────────
 * 실 결제액 110,000원
 * </pre>
 * <b>실 결제액 = 결제 합 − 환불 합</b>({@link #netPaidAmount}). 지연 환급은 입장료 일부를 돌려준 것이라 환불과 같이 빼고, 배상은 입장료 환급이
 * 아니므로 따로 한 줄로만 두고 빼지 않는다({@link PaymentHistoryEntryKind}). 예매 카드에는 이 실 결제액만 두고 자세한 돈 흐름은 이 내역에서 본다
 * (docs/07 "취소된 티켓도 예매 카드에 남습니다").
 * <p>
 * <b>변경은 환불 한 줄 + 결제 한 줄</b>({@link #recordChange}) — 기존 티켓은 지금 취소하는 것과 같은 금액을 환불하고, 새 티켓은 가격 전부를 낸다.
 * 금액 결과는 차액 정산과 같지만, 티켓 금액이 곧 실제 받은 돈이 되고 어느 티켓이 어떻게 바뀌었는지 남는다(docs/08 "계산뿐 아니라 처리도 '취소 +
 * 재예매'입니다"). 티켓에는 "변경됨" 상태도 "이전 티켓"도 없다 — 이 두 줄이 그 이력이다(docs/99 B "티켓 변경 사슬"). 그래서 부분 취소와 부분 변경이
 * 여러 번 일어나도 같은 방식으로 읽힌다.
 * <p>
 * 줄은 쌓기만 하고 고치거나 지우지 않는다. 내역 안에서 늘 지키는 것 — 기록할 때도, 저장소에서 되살릴 때도 본다.
 * <ul>
 *   <li>첫 줄은 예매 결제이고 하나뿐이다. 그 뒤의 결제는 모두 변경 결제다.</li>
 *   <li>좌석 변경 환불 바로 뒤에는 같은 시각, 같은 장수의 변경 결제가 붙는다.</li>
 *   <li>환불 · 지연 환급 · 배상의 대상은 이 내역에서 결제한 티켓이다. 같은 돈을 두 번 돌려주지 않는다 — 티켓마다 결제 한 번, 취소 환불 한 번, 그날
 *       몫은 회차마다 한 번, 지연 환급은 회차마다 한 번이고, 취소 환불한 티켓에는 그 뒤 그날 몫 · 지연 환급이 없다. 그날 몫을 돌려준 회차에는 그
 *       회차의 지연 환급이 없다 — 그날 몫보다 많이 돌려주게 된다.</li>
 *   <li>배상은 같은 까닭의 환불을 받은 티켓에만, 까닭마다 한 번 붙는다. 불가항력 확인으로 보류됐던 배상은 환불보다 늦게 붙는다(docs/04 "불가항력이면
 *       배상이 없습니다" — 환불은 기다리지 않는다).</li>
 *   <li>돌려준 돈(환불 · 지연 환급)이 실 결제액을 넘지 않는다.</li>
 * </ul>
 * 줄의 순서는 기록한 순서다. 시각이 앞선 줄이 늦게 기록될 수도 있어(시각을 읽은 뒤 잠금을 기다린 흐름) 시각 순서는 강제하지 않는다.
 * <p>
 * 금액을 정하는 일은 각 흐름이 한다 — 취소 · 변경 환불액(공제율, #78 · #80), 지연 환급액 · 배상액(#96). 이 내역은 결제액(새 티켓 가격의 합)과
 * 그날 몫(티켓의 {@link Ticket#dayShare})만 스스로 계산하고, 나머지는 받은 금액이 티켓의 기준 금액을 넘지 않는지만 본다. 티켓의 부분 환불 기록
 * ({@link Ticket#recordPartialRefund})과 취소({@link Ticket#cancel})도 흐름이 따로 한다.
 */
public final class PaymentHistory {

    private final long bookingId;
    private final List<PaymentHistoryEntry> entries = new ArrayList<>();

    private PaymentHistory(long bookingId) {
        if (bookingId < 1) {
            throw new IllegalArgumentException("예매 건 id는 1 이상이어야 합니다: " + bookingId);
        }
        this.bookingId = bookingId;
    }

    /**
     * 결제가 승인되어 티켓을 발급하면 내역을 시작한다 — 첫 줄은 예매 결제, 금액은 발급한 티켓 결제 금액의 합이다(티켓 금액이 곧 실제 받은 돈,
     * docs/14 "변경은 티켓을 바꿔치웁니다"). 받은 금액을 예매 건 금액과 대조하는 일은 결제 승인 흐름(#74)이 한다.
     *
     * @param issuedTickets 발급해 저장한 그 예매 건의 티켓들 — 결제 금액이 있는 예매 티켓
     * @throws IllegalArgumentException 티켓이 없거나, 한 예매 건의 티켓이 아니거나, 저장 전이거나, 취소됐거나, 결제 금액 합이 0원이면
     */
    public static PaymentHistory startWithBookingPayment(List<Ticket> issuedTickets, Instant now) {
        Objects.requireNonNull(issuedTickets, "issuedTickets");
        Objects.requireNonNull(now, "now");
        if (issuedTickets.isEmpty()) {
            throw new IllegalArgumentException("예매 결제에는 발급한 티켓이 하나 이상 있습니다");
        }
        Ticket firstTicket = Objects.requireNonNull(issuedTickets.getFirst(), "ticket");
        if (!firstTicket.parent().isBooking()) {
            throw new IllegalArgumentException("초대권 티켓에는 결제가 없습니다: 티켓 " + firstTicket.number());
        }
        PaymentHistory history = new PaymentHistory(firstTicket.parent().id());
        List<Ticket> tickets = history.ownTickets(issuedTickets);
        requireLive(tickets, "결제");
        history.add(PaymentHistoryEntry.record(PaymentHistoryEntryKind.PAYMENT, PaymentHistoryCause.bookingPayment(),
                paidTotalOf(tickets), now, idsOf(tickets)));
        return history;
    }

    /**
     * 저장소에서 읽은 줄들로 내역을 되살린다. 줄을 기록한 순서대로 다시 쌓으며 내역 안의 규칙(첫 줄 · 변경의 두 줄 · 같은 돈을 두 번 돌려주지 않음 ·
     * 실 결제액)을 다시 본다. 티켓의 상태와 맞는지는 기록할 때만 본다.
     *
     * @param entries 저장한 줄들, 기록한 순서대로
     * @throws IllegalArgumentException 줄이 없거나, 저장 전 줄이 있거나, 내역 안의 규칙에 맞지 않으면
     */
    public static PaymentHistory restore(long bookingId, List<PaymentHistoryEntry> entries) {
        PaymentHistory history = new PaymentHistory(bookingId);
        List<PaymentHistoryEntry> restoredEntries = List.copyOf(entries);
        if (restoredEntries.isEmpty()) {
            throw new IllegalArgumentException("결제·환불 내역은 예매 결제 한 줄로 시작합니다 — 줄이 없습니다: 예매 건 " + bookingId);
        }
        for (PaymentHistoryEntry entry : restoredEntries) {
            if (entry.id().isEmpty()) {
                throw new IllegalArgumentException("저장 전 줄은 되살리지 않습니다: " + entry);
            }
            history.add(entry);
        }
        PaymentHistoryEntry lastEntry = history.entries.getLast();
        if (isChangeRefund(lastEntry)) {
            throw new IllegalArgumentException("좌석 변경 환불 뒤에 변경 결제가 없습니다: " + lastEntry);
        }
        return history;
    }

    /**
     * 취소한 티켓의 환불 한 줄을 남긴다 — 관객 취소(부분 취소 포함) · 회차 취소 · 행사 취소 · 공연 중단 · 관리자 조치 · 결제 오류 환불. 까닭은 티켓에
     * 남은 취소 사유다. 여러 예매 건을 한 화면에서 취소해도 환불은 건별로 나가므로 예매 건마다 한 줄이다(docs/08 "여러 예매 건을 한 번에 취소").
     * 배상이 붙으면 {@link #recordCompensation}으로 따로 한 줄을 남긴다.
     * <p>
     * 환불액은 공제율을 적용한 금액이다(#78). 환불액이 0원이면 돈이 움직이지 않으므로 이 줄을 남기지 않는다 — 취소는 티켓에 남는다.
     * <p>
     * 이미 일부를 돌려받은 티켓(그날 몫 · 지연 환급)은 환불 기준액이 그만큼 줄어 있어야 한다 — 이 내역이 돌려준 부분 금액과 티켓에 남은 부분 환불을
     * 대조해, 흐름이 {@link Ticket#recordPartialRefund}를 빠뜨려 같은 돈을 두 번 돌려주는 일을 막는다.
     *
     * @param cancelledTickets 이번에 같은 사유로 취소한 이 예매 건의 티켓들
     * @param amount           돌려준 금액, 1원 이상이고 그 티켓들의 환불 기준액({@link Ticket#refundBaseAmount}) 합 이하
     * @return 새 줄 — 저장 전
     * @throws IllegalArgumentException 티켓이 이 예매 건의 저장한 티켓이 아니거나, 취소되지 않았거나, 사유가 서로 다르거나 좌석 변경이면(변경은
     *                                  {@link #recordChange}), 티켓에 남은 부분 환불이 이 내역과 맞지 않거나, 금액이 범위 밖이거나, 이미 환불한
     *                                  티켓이면
     */
    public PaymentHistoryEntry recordCancellationRefund(List<Ticket> cancelledTickets, Money amount, Instant now) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(now, "now");
        List<Ticket> tickets = ownTickets(cancelledTickets);
        TicketCancellationReason reason = commonCancellationReasonOf(tickets);
        if (reason == TicketCancellationReason.SEAT_CHANGE) {
            throw new IllegalArgumentException("좌석 변경은 환불 한 줄과 결제 한 줄을 함께 남깁니다(recordChange): 예매 건 " + bookingId);
        }
        requirePartialRefundsOnTickets(tickets);
        requireWithin(amount, refundBaseTotalOf(tickets), "환불 기준액");
        return add(PaymentHistoryEntry.record(PaymentHistoryEntryKind.REFUND, PaymentHistoryCause.cancellation(reason),
                amount, now, idsOf(tickets)));
    }

    /**
     * 변경을 남긴다 — <b>기존 티켓의 환불 한 줄과 새 티켓의 결제 한 줄</b>, 같은 시각(docs/08 "티켓에 '변경됨' 상태를 두지 않습니다").
     * <pre>
     * S석 50,000원 → 2일 전 A석 30,000원으로 변경 (공제율 30%)
     * 환불   −35,000원     50,000 × 70%
     * 결제    30,000원     A석 가격 전액
     * </pre>
     * 고른 만큼 한 번에 바꾸고(4매 중 2매면 2매 → 새 2매), 나머지 티켓은 건드리지 않는다(docs/08 "부분 변경과 여러 장 동시 변경"). 묶음권은 변경할
     * 수 없다. 기존 티켓을 '좌석 변경'으로 취소하고 새 티켓을 발급해 저장하는 일은 변경 흐름(#80)이 이 기록 전에 한다.
     *
     * @param replacedTickets 변경으로 '좌석 변경' 취소한 기존 티켓들
     * @param refundAmount    기존 티켓에 돌려준 금액 — 지금 취소하는 것과 같은 금액(기존 회차 기준 공제), 1원 이상이고 기존 티켓들의 환불 기준액 합
     *                        이하
     * @param newTickets      변경으로 발급해 저장한 새 티켓들 — 결제액은 그 결제 금액의 합이다(변경 시점의 현재 가격)
     * @return 환불 줄, 결제 줄 차례 — 저장 전
     * @throws IllegalArgumentException 티켓이 이 예매 건의 저장한 티켓이 아니거나, 기존 티켓이 '좌석 변경'으로 취소되지 않았거나, 새 티켓이
     *                                  취소됐거나, 장수가 다르거나, 묶음권이 있거나, 기존 티켓에 남은 부분 환불이 이 내역과 맞지 않거나, 금액이
     *                                  범위 밖이면
     */
    public List<PaymentHistoryEntry> recordChange(List<Ticket> replacedTickets, Money refundAmount, List<Ticket> newTickets,
            Instant now) {
        Objects.requireNonNull(refundAmount, "refundAmount");
        Objects.requireNonNull(now, "now");
        List<Ticket> replaced = ownTickets(replacedTickets);
        List<Ticket> issued = ownTickets(newTickets);
        if (replaced.size() != issued.size()) {
            throw new IllegalArgumentException("변경은 바꾼 장수만큼 새 티켓을 발급합니다: 기존 " + replaced.size() + "장, 새 " + issued.size() + "장");
        }
        if (replaced.stream().anyMatch(Ticket::isBundle) || issued.stream().anyMatch(Ticket::isBundle)) {
            throw new IllegalArgumentException("묶음권은 변경할 수 없습니다: 예매 건 " + bookingId);
        }
        if (commonCancellationReasonOf(replaced) != TicketCancellationReason.SEAT_CHANGE) {
            throw new IllegalArgumentException("변경한 기존 티켓은 '좌석 변경'으로 취소됩니다: 예매 건 " + bookingId);
        }
        requireLive(issued, "변경 결제");
        requirePartialRefundsOnTickets(replaced);
        requireWithin(refundAmount, refundBaseTotalOf(replaced), "환불 기준액");
        PaymentHistoryEntry refund = PaymentHistoryEntry.record(PaymentHistoryEntryKind.REFUND,
                PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE), refundAmount, now, idsOf(replaced));
        PaymentHistoryEntry payment = PaymentHistoryEntry.record(PaymentHistoryEntryKind.PAYMENT,
                PaymentHistoryCause.changePayment(), paidTotalOf(issued), now, idsOf(issued));
        List<PaymentHistoryEntry> withRefund = new ArrayList<>(entries);
        requireAppendable(entries, refund);
        withRefund.add(refund);
        requireAppendable(withRefund, payment);
        entries.add(refund);
        entries.add(payment);
        return List.of(refund, payment);
    }

    /**
     * 묶음권의 그날 몫 환불 한 줄을 남긴다 — 티켓은 그대로 두고 그 회차의 몫만 돌려준다. 주최자가 그날을 취소했거나 그날 공연이 중단됐을 때, 출연자가
     * 바뀐 날을 관객이 포기했을 때다(docs/08 "묶음권 중 하루가 취소되면", "공연 중단", docs/07 "출연자가 바뀐 날을 포기하면"). 금액은 티켓마다
     * 그날 몫({@link Ticket#dayShare} — 결제 금액 × 그날 가격 ÷ 날짜별 가격 합계, 원 미만 버림)의 합이다.
     * <pre>
     * 3일권 130,200원 — 토요일(5/4) 취소  환불 130,200 × 50,000 ÷ 140,000 = 46,500원
     * </pre>
     * 10% 배상은 {@link #recordCompensation}으로 따로 남긴다(불가항력이면 관리자 확인까지 보류). 돌려준 그날 몫을 티켓에 남기는 일
     * ({@link Ticket#recordPartialRefund})은 흐름이 한다 — 빠뜨리면 그 뒤 취소 · 변경 기록이 막힌다.
     * <p>
     * 확인 필요 (#223) — 그 회차의 지연 환급을 이미 받은 티켓에 나중에 그날 몫을 돌려줄 때(늦게 시작한 뒤 중단된 날 등) 그날 몫에서 지연 환급을
     * 빼는지 문서에 없다. 지금은 그날 몫 전액을 돌려준다. 반대 순서(그날 몫 뒤 그 회차 지연 환급)는 그날 몫보다 많이 돌려주게 되어 막는다
     * ({@link #recordDelayRefund}).
     *
     * @param bundleTickets 그 회차를 가리키는, 취소되지 않은 이 예매 건의 묶음권 티켓들
     * @return 새 줄 — 저장 전
     * @throws IllegalArgumentException 티켓이 이 예매 건의 저장한 티켓이 아니거나, 묶음권이 아니거나, 그 회차를 가리키지 않거나, 취소됐거나, 그
     *                                  회차의 몫을 이미 돌려줬으면
     */
    public PaymentHistoryEntry recordBundleDayRefund(List<Ticket> bundleTickets, long sessionId, Instant now) {
        Objects.requireNonNull(now, "now");
        List<Ticket> tickets = ownTickets(bundleTickets);
        requireBundlesPointingTo(tickets, sessionId);
        requireLive(tickets, "그날 몫 환불");
        Money dayShareTotal = Money.sum(tickets.stream().map(ticket -> ticket.dayShare(sessionId)).toList());
        return add(PaymentHistoryEntry.record(PaymentHistoryEntryKind.REFUND, PaymentHistoryCause.bundleDayShare(sessionId),
                dayShareTotal, now, idsOf(tickets)));
    }

    /**
     * 공연 지연 환급 한 줄을 남긴다 — 주최자 사정으로 30분 이상 늦게 시작한 회차에 입장한 티켓마다 결제 금액의 10%(묶음권은 그날 몫의 10%).
     * 티켓은 그대로 유효하다(docs/08 "공연 지연"). 입장 기록으로 대상을 고르고 금액을 계산하는 일은 지연 처리 흐름(#96)이 한다. 돌려준 금액을
     * 티켓마다 남기는 일({@link Ticket#recordPartialRefund})도 흐름이 한다.
     * <p>
     * 묶음권에서 그 회차의 그날 몫을 이미 돌려받은 티켓은 대상이 아니다 — 그날 입장료를 전부 돌려받았는데 10%를 더 주면 그날 몫보다 많이
     * 돌려준다(같은 돈을 두 번 돌려주지 않음, docs/08 "기준 금액"). 흐름이 그런 티켓을 넘기면 프로그램 오류다.
     *
     * @param enteredTickets 그 회차에 입장한, 취소되지 않은 이 예매 건의 티켓들
     * @param amount         돌려준 금액, 1원 이상이고 그 티켓들의 결제 금액 합 이하
     * @return 새 줄 — 저장 전
     * @throws IllegalArgumentException 티켓이 이 예매 건의 저장한 티켓이 아니거나, 그 회차를 가리키지 않거나, 취소됐거나, 금액이 범위 밖이거나,
     *                                  그 회차의 지연 환급을 이미 받았거나, 그 회차의 그날 몫을 이미 돌려받았으면
     */
    public PaymentHistoryEntry recordDelayRefund(List<Ticket> enteredTickets, long sessionId, Money amount, Instant now) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(now, "now");
        List<Ticket> tickets = ownTickets(enteredTickets);
        requirePointingTo(tickets, sessionId);
        requireLive(tickets, "지연 환급");
        requireWithin(amount, paidTotalOf(tickets), "결제 금액");
        return add(PaymentHistoryEntry.record(PaymentHistoryEntryKind.DELAY_REFUND,
                PaymentHistoryCause.performanceDelay(sessionId), amount, now, idsOf(tickets)));
    }

    /**
     * 배상 한 줄을 남긴다 — 입장료 환급이 아니므로 <b>실 결제액에서 빼지 않는</b> 별도 줄이다. 배상이 붙은 환불과 같은 까닭으로 남긴다.
     * <ul>
     *   <li>티켓 취소 — 회차 취소 · 행사 취소 · 공연 중단, 그리고 출연자 변경으로 취소 시 배상 표시가 붙은 티켓의 관객 취소 · 관리자 조치.</li>
     *   <li>묶음권 그날 몫 — 주최자의 하루 취소 · 그날 공연 중단 · 출연자가 바뀐 날 포기.</li>
     * </ul>
     * 불가항력이면 배상은 관리자 확인까지 보류되고 환불은 기다리지 않는다 — 확인 뒤 지급하면 그때 이 줄을 남긴다(docs/04 "불가항력이면 배상이
     * 없습니다"). 배상액(입장료의 10%)은 #96이 계산한다.
     *
     * @param refundCause 배상이 붙은 환불의 까닭 — 그 까닭의 환불 줄이 이 티켓들에 먼저 있어야 한다
     * @param tickets     배상하는 이 예매 건의 티켓들
     * @param amount      배상액, 1원 이상이고 그 티켓들의 결제 금액 합 이하
     * @return 새 줄 — 저장 전
     * @throws IllegalArgumentException 배상이 붙지 않는 까닭이거나, 티켓이 그 까닭에 맞지 않거나(취소 사유가 다름, 배상 표시가 없음, 그 회차를
     *                                  가리키는 묶음권이 아님), 그 까닭의 환불을 받지 않았거나, 이미 배상했거나, 금액이 범위 밖이면
     */
    public PaymentHistoryEntry recordCompensation(PaymentHistoryCause refundCause, List<Ticket> tickets, Money amount,
            Instant now) {
        Objects.requireNonNull(refundCause, "refundCause");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(now, "now");
        if (!refundCause.allows(PaymentHistoryEntryKind.COMPENSATION)) {
            throw new IllegalArgumentException("배상이 붙지 않는 까닭입니다: " + refundCause);
        }
        List<Ticket> compensatedTickets = ownTickets(tickets);
        if (refundCause instanceof Cancellation cancellation) {
            requireCompensableCancellation(compensatedTickets, cancellation);
        } else if (refundCause instanceof BundleDayShare dayShare) {
            requireBundlesPointingTo(compensatedTickets, dayShare.sessionId());
        }
        requireWithin(amount, paidTotalOf(compensatedTickets), "결제 금액");
        return add(PaymentHistoryEntry.record(PaymentHistoryEntryKind.COMPENSATION, refundCause, amount, now,
                idsOf(compensatedTickets)));
    }

    /**
     * 실 결제액 = 결제 합 − (환불 합 + 지연 환급 합). 배상은 빼지 않는다(docs/08 "결제·환불 내역").
     * <pre>
     * 3/1 결제 165,000 · 3/5 환불 −35,000 · 3/8 환불 −50,000 · 3/8 결제 30,000 → 110,000원
     * 3일권 130,200 결제 · 5/4 그날 몫 환불 −46,500 · 배상 4,650       → 83,700원
     * </pre>
     */
    public Money netPaidAmount() {
        return netPaidAmountOf(entries);
    }

    /** 그 종류 줄의 금액 합. 줄이 없으면 0원이다. */
    public Money totalOf(PaymentHistoryEntryKind kind) {
        Objects.requireNonNull(kind, "kind");
        return Money.sum(entries.stream().filter(entry -> entry.kind() == kind).map(PaymentHistoryEntry::amount).toList());
    }

    /** 이 내역의 예매 건 id. */
    public long bookingId() {
        return bookingId;
    }

    /** 줄들, 기록한 순서대로. */
    public List<PaymentHistoryEntry> entries() {
        return List.copyOf(entries);
    }

    private PaymentHistoryEntry add(PaymentHistoryEntry entry) {
        requireAppendable(entries, entry);
        entries.add(entry);
        return entry;
    }

    /**
     * 이 내역이 그 티켓에 돌려준 부분 금액과 티켓에 남은 부분 환불({@link Ticket#partiallyRefundedAmount})이 맞는지 본다. 흐름이
     * {@link Ticket#recordPartialRefund}를 빠뜨리면 환불 기준액이 줄지 않아, 그날 몫을 받은 3일권을 전체 취소할 때 결제 금액 전부를 다시 돌려준다 —
     * 같은 돈을 두 번 돌려주지 않는다(docs/08 "기준 금액", "부분 취소").
     * <ul>
     *   <li>그날 몫은 티켓마다 금액을 안다({@link Ticket#dayShare}) — 정확히 맞아야 한다.</li>
     *   <li>지연 환급은 줄 금액이 대상 티켓 몫의 합이다 — 그 티켓 혼자인 줄은 정확히, 여럿인 줄은 그 줄 금액까지를 범위로 본다.</li>
     * </ul>
     */
    private void requirePartialRefundsOnTickets(List<Ticket> tickets) {
        for (Ticket ticket : tickets) {
            long ticketId = ticket.id().orElseThrow();
            Money least = Money.ZERO;
            Money most = Money.ZERO;
            for (PaymentHistoryEntry entry : entries) {
                if (!entry.ticketIds().contains(ticketId)) {
                    continue;
                }
                if (entry.kind() == PaymentHistoryEntryKind.REFUND && entry.cause() instanceof BundleDayShare dayShare) {
                    Money share = ticket.dayShare(dayShare.sessionId());
                    least = least.plus(share);
                    most = most.plus(share);
                } else if (entry.kind() == PaymentHistoryEntryKind.DELAY_REFUND) {
                    most = most.plus(entry.amount());
                    if (entry.ticketIds().size() == 1) {
                        least = least.plus(entry.amount());
                    }
                }
            }
            Money recorded = ticket.partiallyRefundedAmount();
            if (recorded.isLessThan(least) || recorded.isGreaterThan(most)) {
                throw new IllegalArgumentException("티켓에 남은 부분 환불이 내역과 맞지 않습니다 — 그날 몫 · 지연 환급을 티켓에 남겼는지 보세요: 티켓 "
                        + ticket.number() + ", 티켓 " + recorded + ", 내역 " + least + (least.equals(most) ? "" : "~" + most));
            }
        }
    }

    /** 이 예매 건의 저장한 티켓들인지 보고 티켓 순번 순서로 돌려준다. */
    private List<Ticket> ownTickets(List<Ticket> tickets) {
        Objects.requireNonNull(tickets, "tickets");
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("대상 티켓이 하나 이상 있어야 합니다: 예매 건 " + bookingId);
        }
        Set<Long> distinctTicketIds = new HashSet<>();
        for (Ticket ticket : tickets) {
            Objects.requireNonNull(ticket, "ticket");
            if (!ticket.parent().isBooking() || ticket.parent().id() != bookingId) {
                throw new IllegalArgumentException("이 예매 건의 티켓이 아닙니다: 예매 건 " + bookingId + ", 티켓 " + ticket.number());
            }
            long ticketId = ticket.id().orElseThrow(
                    () -> new IllegalArgumentException("저장 전 티켓은 내역에 남기지 않습니다: 티켓 " + ticket.number()));
            if (!distinctTicketIds.add(ticketId)) {
                throw new IllegalArgumentException("같은 티켓이 두 번 있습니다: 티켓 " + ticket.number());
            }
        }
        return tickets.stream().sorted(Comparator.comparingInt(Ticket::sequence)).toList();
    }

    private static void requireAppendable(List<PaymentHistoryEntry> previous, PaymentHistoryEntry entry) {
        requireOrder(previous, entry);
        requireTicketsOnce(previous, entry);
        if (entry.kind().returnsAdmissionFee() && entry.amount().isGreaterThan(netPaidAmountOf(previous))) {
            throw new IllegalArgumentException("실 결제액보다 많이 돌려줄 수 없습니다: 실 결제액 " + netPaidAmountOf(previous) + ", " + entry);
        }
    }

    /** 첫 줄은 예매 결제 하나뿐, 좌석 변경 환불 바로 뒤에는 같은 시각 · 같은 장수의 변경 결제. */
    private static void requireOrder(List<PaymentHistoryEntry> previous, PaymentHistoryEntry entry) {
        boolean bookingPayment = entry.cause() instanceof BookingPayment;
        if (previous.isEmpty()) {
            if (!bookingPayment) {
                throw new IllegalArgumentException("내역의 첫 줄은 예매 결제입니다: " + entry);
            }
            return;
        }
        if (bookingPayment) {
            throw new IllegalArgumentException("예매 결제는 내역의 첫 줄 하나뿐입니다 — 그 뒤의 결제는 변경 결제입니다: " + entry);
        }
        PaymentHistoryEntry lastEntry = previous.getLast();
        boolean changePayment = entry.cause() instanceof ChangePayment;
        if (isChangeRefund(lastEntry) != changePayment) {
            throw new IllegalArgumentException(changePayment
                    ? "변경 결제는 좌석 변경 환불 바로 뒤에 붙습니다: " + entry
                    : "좌석 변경 환불 바로 뒤에는 변경 결제가 붙습니다: " + lastEntry + " 다음 " + entry);
        }
        if (changePayment && !entry.occurredAt().equals(lastEntry.occurredAt())) {
            throw new IllegalArgumentException("변경의 환불과 결제는 같은 시각입니다: " + lastEntry + ", " + entry);
        }
        if (changePayment && entry.ticketIds().size() != lastEntry.ticketIds().size()) {
            throw new IllegalArgumentException("변경은 바꾼 장수만큼 새 티켓을 발급합니다: " + lastEntry + ", " + entry);
        }
    }

    /** 같은 돈을 두 번 주고받지 않는다 — 결제는 티켓마다 한 번, 돌려주는 줄은 결제한 티켓에 까닭마다 한 번, 배상은 같은 까닭의 환불을 받은 티켓에. */
    private static void requireTicketsOnce(List<PaymentHistoryEntry> previous, PaymentHistoryEntry entry) {
        Set<Long> paidTicketIds = ticketIdsOf(previous, earlier -> earlier.kind().isPayment());
        Set<Long> sameKindAndCause = ticketIdsOf(previous,
                earlier -> earlier.kind() == entry.kind() && earlier.cause().equals(entry.cause()));
        switch (entry.kind()) {
            case PAYMENT -> requireNoneIn(entry, paidTicketIds, "이미 결제한 티켓입니다");
            case REFUND, DELAY_REFUND -> {
                requireAllIn(entry, paidTicketIds, "이 예매 건에서 결제하지 않은 티켓입니다");
                requireNoneIn(entry, ticketIdsOf(previous, PaymentHistory::isCancellationRefund), "이미 취소해 환불한 티켓입니다");
                requireNoneIn(entry, sameKindAndCause, "같은 까닭으로 이미 돌려준 티켓입니다");
                if (entry.cause() instanceof PerformanceDelay delay) {
                    requireNoneIn(entry, ticketIdsOf(previous, earlier -> isBundleDayRefundOf(earlier, delay.sessionId())),
                            "그 회차의 그날 몫을 이미 돌려준 티켓입니다 — 지연 환급을 더 하면 그날 몫보다 많이 돌려줍니다");
                }
            }
            case COMPENSATION -> {
                requireAllIn(entry, ticketIdsOf(previous,
                        earlier -> earlier.kind() == PaymentHistoryEntryKind.REFUND && earlier.cause().equals(entry.cause())),
                        "같은 까닭의 환불을 받지 않은 티켓입니다 — 배상은 환불과 함께 또는 그 뒤에 나갑니다");
                requireNoneIn(entry, sameKindAndCause, "같은 까닭으로 이미 배상한 티켓입니다");
            }
        }
    }

    private static Money netPaidAmountOf(List<PaymentHistoryEntry> entries) {
        Money paid = Money.ZERO;
        Money returned = Money.ZERO;
        for (PaymentHistoryEntry entry : entries) {
            if (entry.kind().isPayment()) {
                paid = paid.plus(entry.amount());
            } else if (entry.kind().returnsAdmissionFee()) {
                returned = returned.plus(entry.amount());
            }
        }
        return paid.minus(returned);
    }

    private static boolean isChangeRefund(PaymentHistoryEntry entry) {
        return entry.kind() == PaymentHistoryEntryKind.REFUND
                && entry.cause() instanceof Cancellation cancellation && cancellation.isSeatChange();
    }

    private static boolean isCancellationRefund(PaymentHistoryEntry entry) {
        return entry.kind() == PaymentHistoryEntryKind.REFUND && entry.cause() instanceof Cancellation;
    }

    private static boolean isBundleDayRefundOf(PaymentHistoryEntry entry, long sessionId) {
        return entry.kind() == PaymentHistoryEntryKind.REFUND
                && entry.cause() instanceof BundleDayShare dayShare && dayShare.sessionId() == sessionId;
    }

    private static Set<Long> ticketIdsOf(List<PaymentHistoryEntry> entries, Predicate<PaymentHistoryEntry> condition) {
        Set<Long> ticketIds = new HashSet<>();
        entries.stream().filter(condition).forEach(entry -> ticketIds.addAll(entry.ticketIds()));
        return ticketIds;
    }

    private static void requireAllIn(PaymentHistoryEntry entry, Set<Long> allowedTicketIds, String problem) {
        List<Long> outside = entry.ticketIds().stream().filter(ticketId -> !allowedTicketIds.contains(ticketId)).toList();
        if (!outside.isEmpty()) {
            throw new IllegalArgumentException(problem + ": 티켓 " + outside + ", " + entry);
        }
    }

    private static void requireNoneIn(PaymentHistoryEntry entry, Set<Long> usedTicketIds, String problem) {
        List<Long> overlapping = entry.ticketIds().stream().filter(usedTicketIds::contains).toList();
        if (!overlapping.isEmpty()) {
            throw new IllegalArgumentException(problem + ": 티켓 " + overlapping + ", " + entry);
        }
    }

    private static void requireLive(List<Ticket> tickets, String what) {
        for (Ticket ticket : tickets) {
            if (ticket.isCancelled()) {
                throw new IllegalArgumentException("취소된 티켓에는 " + what + " 줄을 남기지 않습니다: 티켓 " + ticket.number());
            }
        }
    }

    /** 모두 취소됐고 사유가 하나인지 보고 그 사유를 돌려준다. */
    private static TicketCancellationReason commonCancellationReasonOf(List<Ticket> tickets) {
        Set<TicketCancellationReason> reasons = new HashSet<>();
        for (Ticket ticket : tickets) {
            TicketCancellation cancellation = ticket.cancellation().orElseThrow(
                    () -> new IllegalArgumentException("취소하지 않은 티켓은 환불하지 않습니다: 티켓 " + ticket.number()));
            reasons.add(cancellation.reason());
        }
        if (reasons.size() > 1) {
            throw new IllegalArgumentException("한 줄의 티켓은 같은 사유로 취소됐어야 합니다: " + reasons);
        }
        return reasons.iterator().next();
    }

    private static void requireCompensableCancellation(List<Ticket> tickets, Cancellation cancellation) {
        if (commonCancellationReasonOf(tickets) != cancellation.reason()) {
            throw new IllegalArgumentException("배상하는 티켓의 취소 사유가 까닭과 다릅니다: " + cancellation.reason());
        }
        if (cancellation.compensatesOnlyMarkedTickets()) {
            for (Ticket ticket : tickets) {
                if (!ticket.isCompensatedOnCancel()) {
                    throw new IllegalArgumentException("취소 시 배상 표시가 없는 티켓입니다: 티켓 " + ticket.number() + ", " + cancellation.reason());
                }
            }
        }
    }

    private static void requireBundlesPointingTo(List<Ticket> tickets, long sessionId) {
        for (Ticket ticket : tickets) {
            if (!ticket.isBundle()) {
                throw new IllegalArgumentException("그날 몫은 묶음권에만 있습니다: 티켓 " + ticket.number());
            }
        }
        requirePointingTo(tickets, sessionId);
    }

    private static void requirePointingTo(List<Ticket> tickets, long sessionId) {
        for (Ticket ticket : tickets) {
            if (!ticket.sessionIds().contains(sessionId)) {
                throw new IllegalArgumentException("그 회차를 가리키지 않는 티켓입니다: 티켓 " + ticket.number() + ", 회차 " + sessionId);
            }
        }
    }

    private static void requireWithin(Money amount, Money limit, String limitName) {
        if (amount.isGreaterThan(limit)) {
            throw new IllegalArgumentException("금액이 " + limitName + " 합보다 큽니다: " + amount + " > " + limit);
        }
    }

    private static Money paidTotalOf(List<Ticket> tickets) {
        return Money.sum(tickets.stream().map(Ticket::paidAmount).toList());
    }

    private static Money refundBaseTotalOf(List<Ticket> tickets) {
        return Money.sum(tickets.stream().map(Ticket::refundBaseAmount).toList());
    }

    private static List<Long> idsOf(List<Ticket> tickets) {
        return tickets.stream().map(ticket -> ticket.id().orElseThrow()).toList();
    }
}
