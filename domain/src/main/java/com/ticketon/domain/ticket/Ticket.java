package com.ticketon.domain.ticket;

import com.ticketon.domain.pricing.DiscountKind;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.SessionPrice;
import com.ticketon.domain.pricing.TicketPrice;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 티켓 — <b>티켓 1장 = QR 1개 = 입장 1명</b>(docs/07 "기본 원칙"). 4매를 예매하면 티켓 4장, 수량형 2매도 티켓 2장이다. QR 하나로 여럿을 처리하면
 * 일행이 따로 올 때 먼저 온 사람이 찍는 순간 전부 입장 처리된다.
 * <p>
 * <b>바뀌면 티켓도 따라 바뀌어야 하는 것은 가리키고, 바뀌어도 티켓은 그대로여야 하는 것은 직접 갖는다</b>(docs/07 "티켓이 직접 갖는 것과 가리키기만
 * 하는 것", docs/14 "3층 — 티켓").
 * <table>
 *   <caption>티켓이 가리키는 것과 직접 갖는 것</caption>
 *   <tr><th>가리킴</th><th>직접 가짐</th></tr>
 *   <tr><td>부모(예매 건 또는 초대권)</td><td>부모 번호 + 순번 = 티켓 번호({@link TicketNumber})</td></tr>
 *   <tr><td>회차 1개 이상 — 연기하면 날짜가 따라 바뀜</td><td>산 때의 회차 가격 · 회차별 포기 표시({@link TicketSession})</td></tr>
 *   <tr><td>구역 — "R석" 표기가 바뀌면 따라감</td><td>결제 금액 · 적용 할인({@link TicketPrice}) — 가격을 바꿔도 산 표는 그대로</td></tr>
 *   <tr><td>좌석 0~1개 — 블록 이름을 고치면 표기가 따라감</td><td>이미 돌려준 부분 금액 · 공제 면제({@link DeductionExemptionCause}) · 취소 시 배상 표시</td></tr>
 *   <tr><td></td><td>취소 시각 · 사유({@link TicketCancellation}) · QR 열쇠({@link QrKey})</td></tr>
 * </table>
 * 그래서 블록 이름을 고치거나 회차를 연기해도 티켓은 하나도 손대지 않는다. <b>좌석 표기를 글자로 복사하지 않는다</b>(docs/99 B "티켓에 좌석
 * 표기를 글자로 복사") — 화면을 그릴 때 티켓 → 좌석 → 블록으로 따라가 읽는다.
 * <p>
 * 티켓이 "회차 1개 이상"을 가리키는 구조 하나로 셋이 설명된다(docs/14).
 * <table>
 *   <caption>티켓 구성</caption>
 *   <tr><th></th><th>회차</th><th>좌석</th></tr>
 *   <tr><td>일반 지정석</td><td>1개</td><td>1개</td></tr>
 *   <tr><td>일반 수량형</td><td>1개</td><td>없음</td></tr>
 *   <tr><td>묶음권</td><td>N개</td><td>없음</td></tr>
 * </table>
 * 좌석이 있으면 회차는 하나다 — 지정형은 묶음권이 불가하다(docs/14, docs/99 B "지정석 묶음권"). 회차가 둘 이상이면 묶음권이다
 * ({@link #isBundle}).
 * <p>
 * <b>예매 티켓과 초대권 티켓</b> — 예매 티켓은 결제 금액 · 적용 할인과 회차마다 산 때의 가격을 갖는다. 초대권 티켓은 결제가 없어 그 칸이 비고,
 * 공제 면제 · 배상 · 회차별 포기 · 부분 환불도 없다(돈이 오가지 않는다, docs/08 "초대권").
 * <p>
 * 새 티켓은 {@link #issueForBooking} · {@link #issueForInvitation}으로 발급하고, 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전
 * 티켓은 id가 없다. 발급하는 흐름은 #74(예매) · #86(초대권)이, 저장은 #57이 맡는다.
 */
public final class Ticket {

    private final Long id;
    private final TicketParent parent;
    private final int sequence;
    private final List<TicketSession> sessions;
    private final long zoneId;
    private final Long seatId;
    private final TicketPrice price;
    private final Money partiallyRefundedAmount;
    private final Set<DeductionExemptionCause> deductionExemptionCauses;
    private final boolean compensatedOnCancel;
    private final TicketCancellation cancellation;
    private final QrKey qrKey;

    private Ticket(Long id, TicketParent parent, int sequence, List<TicketSession> sessions, long zoneId, Long seatId,
            TicketPrice price, Money partiallyRefundedAmount, Set<DeductionExemptionCause> deductionExemptionCauses,
            boolean compensatedOnCancel, TicketCancellation cancellation, QrKey qrKey) {
        if (id != null && id < 1) {
            throw new IllegalArgumentException("티켓 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(parent, "parent");
        if (sequence < 1) {
            throw new IllegalArgumentException("티켓 순번은 1 이상이어야 합니다: " + sequence);
        }
        if (zoneId < 1) {
            throw new IllegalArgumentException("구역 id는 1 이상이어야 합니다: " + zoneId);
        }
        if (seatId != null && seatId < 1) {
            throw new IllegalArgumentException("좌석 id는 1 이상이어야 합니다: " + seatId);
        }
        Objects.requireNonNull(partiallyRefundedAmount, "partiallyRefundedAmount");
        Objects.requireNonNull(deductionExemptionCauses, "deductionExemptionCauses");
        Objects.requireNonNull(qrKey, "qrKey");
        List<TicketSession> pointedSessions = List.copyOf(sessions);
        requireSessions(pointedSessions, seatId);
        Set<DeductionExemptionCause> exemptionCauses = deductionExemptionCauses.isEmpty()
                ? EnumSet.noneOf(DeductionExemptionCause.class)
                : EnumSet.copyOf(deductionExemptionCauses);
        if (parent.isBooking()) {
            requireBookingMoney(pointedSessions, price, partiallyRefundedAmount);
        } else {
            requireNoInvitationMoney(pointedSessions, price, partiallyRefundedAmount, exemptionCauses, compensatedOnCancel);
        }
        requireRefundRights(pointedSessions, exemptionCauses, compensatedOnCancel);
        if (cancellation != null && !cancellation.reason().appliesTo(parent.kind())) {
            throw new IllegalArgumentException(parent.kind() + " 티켓에 붙을 수 없는 취소 사유입니다: " + cancellation.reason());
        }
        this.id = id;
        this.parent = parent;
        this.sequence = sequence;
        this.sessions = pointedSessions;
        this.zoneId = zoneId;
        this.seatId = seatId;
        this.price = price;
        this.partiallyRefundedAmount = partiallyRefundedAmount;
        this.deductionExemptionCauses = exemptionCauses;
        this.compensatedOnCancel = compensatedOnCancel;
        this.cancellation = cancellation;
        this.qrKey = qrKey;
    }

    /**
     * 예매 건의 티켓 한 장을 발급한다 — 결제가 승인되면 사람 수만큼(docs/07 "기본 원칙", #74).
     *
     * @param bookingNumber 예매번호 — 티켓 번호의 앞머리
     * @param sequence      그 예매 건에서 몇 번째 장인지, 1 이상
     * @param seatId        지정석이면 좌석 id, 수량형이면 비어 있다
     * @param sessions      가리키는 회차와 산 때의 그 회차 가격({@link TicketSession#purchased}), 날짜 순서대로. 묶음권이면 날짜마다 고른 회차
     * @param price         장당 금액 · 적용 할인 — 가격 계산(pricing)이 낸 값
     * @param qrKey         추측할 수 없는 열쇠
     * @throws IllegalArgumentException 회차가 없거나 겹치거나, 좌석이 있는데 회차가 둘 이상이거나, 산 때의 가격이 빠졌거나 기본 가격과 맞지
     *                                  않거나, 포기 표시가 붙은 회차가 있으면
     */
    public static Ticket issueForBooking(long bookingId, TicketParentNumber bookingNumber, int sequence, long zoneId,
            OptionalLong seatId, List<TicketSession> sessions, TicketPrice price, QrKey qrKey) {
        Objects.requireNonNull(seatId, "seatId");
        Objects.requireNonNull(price, "price");
        requireNotForfeited(sessions);
        return new Ticket(null, TicketParent.booking(bookingId, bookingNumber), sequence, sessions, zoneId,
                seatId.isPresent() ? seatId.getAsLong() : null, price, Money.ZERO, Set.of(), false, null, qrKey);
    }

    /**
     * 초대권의 티켓 한 장을 발급한다 — 결제 없이(docs/09, #86).
     *
     * @param invitationNumber 초대권 번호 — 티켓 번호의 앞머리
     * @param sequence         그 초대권에서 몇 번째 장인지, 1 이상
     * @param seatId           지정석이면 좌석 id, 수량형이면 비어 있다
     * @param sessionIds       가리키는 회차, 날짜 순서대로. 묶음권 초대면 날짜마다 고른 회차
     * @param qrKey            추측할 수 없는 열쇠
     * @throws IllegalArgumentException 회차가 없거나 겹치거나, 좌석이 있는데 회차가 둘 이상이면
     */
    public static Ticket issueForInvitation(long invitationId, TicketParentNumber invitationNumber, int sequence,
            long zoneId, OptionalLong seatId, List<Long> sessionIds, QrKey qrKey) {
        Objects.requireNonNull(seatId, "seatId");
        List<TicketSession> sessions = sessionIds.stream().map(TicketSession::invited).toList();
        return new Ticket(null, TicketParent.invitation(invitationId, invitationNumber), sequence, sessions, zoneId,
                seatId.isPresent() ? seatId.getAsLong() : null, null, Money.ZERO, Set.of(), false, null, qrKey);
    }

    /**
     * 저장소에서 읽은 값으로 티켓을 되살린다. 구성 규칙(회차 · 좌석 · 금액 · 면제 · 포기 · 취소 사유가 부모와 맞는지)은 다시 본다.
     *
     * @param seatId                   좌석 id. 수량형이면 null
     * @param price                    장당 금액 · 적용 할인. 초대권 티켓이면 null
     * @param partiallyRefundedAmount  이미 돌려준 부분 금액. 초대권 티켓이면 0원
     * @param deductionExemptionCauses 공제 면제가 붙은 까닭. 없으면 빈 집합
     * @param compensatedOnCancel      취소 시 배상 표시
     * @param cancellation             취소 기록. 유효하면 null
     * @throws IllegalArgumentException 구성 규칙에 맞지 않으면
     */
    public static Ticket restore(long id, TicketParent parent, int sequence, List<TicketSession> sessions, long zoneId,
            Long seatId, TicketPrice price, Money partiallyRefundedAmount,
            Set<DeductionExemptionCause> deductionExemptionCauses, boolean compensatedOnCancel,
            TicketCancellation cancellation, QrKey qrKey) {
        return new Ticket(id, parent, sequence, sessions, zoneId, seatId, price, partiallyRefundedAmount,
                deductionExemptionCauses, compensatedOnCancel, cancellation, qrKey);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public TicketParent parent() {
        return parent;
    }

    /** 그 부모 안에서 몇 번째 장인지. */
    public int sequence() {
        return sequence;
    }

    /** 티켓 번호 — 부모 번호 + 순번("0091-2"). */
    public TicketNumber number() {
        return TicketNumber.of(parent.number(), sequence);
    }

    /** 가리키는 회차들, 날짜 순서대로. */
    public List<TicketSession> sessions() {
        return sessions;
    }

    /** 가리키는 회차 id들, 날짜 순서대로. */
    public List<Long> sessionIds() {
        return sessions.stream().map(TicketSession::sessionId).toList();
    }

    /** 묶음권인지 — 회차를 둘 이상 가리킨다. */
    public boolean isBundle() {
        return sessions.size() > 1;
    }

    /** 가리키는 구역 id. */
    public long zoneId() {
        return zoneId;
    }

    /** 가리키는 좌석 id. 수량형이면 비어 있다. */
    public OptionalLong seatId() {
        return seatId == null ? OptionalLong.empty() : OptionalLong.of(seatId);
    }

    /** 장당 금액 · 적용 할인. 초대권 티켓은 결제가 없어 비어 있다. */
    public Optional<TicketPrice> price() {
        return Optional.ofNullable(price);
    }

    /** 결제 금액 — 이 티켓에 실제로 받은 돈. 초대권 티켓은 0원이다. */
    public Money paidAmount() {
        return price == null ? Money.ZERO : price.price();
    }

    /** 적용된 할인의 종류 — 조기예매 / 쿠폰 / 없음. 초대권 티켓은 없음이다. */
    public DiscountKind appliedDiscountKind() {
        return price == null ? DiscountKind.NONE : price.discountKind();
    }

    /** 받은 할인 금액. 초대권 티켓 · 할인 없는 티켓은 0원이다. */
    public Money appliedDiscountAmount() {
        return price == null ? Money.ZERO : price.discountAmount();
    }

    /** 이미 돌려준 부분 금액 — 묶음권 그날 몫 · 공연 지연 환급의 합. */
    public Money partiallyRefundedAmount() {
        return partiallyRefundedAmount;
    }

    /** 공제 면제가 붙은 까닭들. 붙지 않았으면 비어 있다. */
    public Set<DeductionExemptionCause> deductionExemptionCauses() {
        return Set.copyOf(deductionExemptionCauses);
    }

    /** 공제 면제가 붙었는지 — 이후 언제 취소해도 공제 0%다(docs/04 "공제 면제"). */
    public boolean isExemptFromDeduction() {
        return !deductionExemptionCauses.isEmpty();
    }

    /** 취소 시 배상 표시가 붙었는지 — 출연자 변경으로 붙고, 취소하면 10% 배상한다(docs/04 "출연자 변경"). */
    public boolean isCompensatedOnCancel() {
        return compensatedOnCancel;
    }

    /** 취소 기록. 유효하면 비어 있다. */
    public Optional<TicketCancellation> cancellation() {
        return Optional.ofNullable(cancellation);
    }

    /** 취소됐는지 — 취소 시각이 있다. */
    public boolean isCancelled() {
        return cancellation != null;
    }

    /** 지금 QR 열쇠. */
    public QrKey qrKey() {
        return qrKey;
    }

    private static void requireSessions(List<TicketSession> sessions, Long seatId) {
        if (sessions.isEmpty()) {
            throw new IllegalArgumentException("티켓은 회차를 하나 이상 가리킵니다");
        }
        Set<Long> distinctSessionIds = new HashSet<>();
        for (TicketSession session : sessions) {
            if (!distinctSessionIds.add(session.sessionId())) {
                throw new IllegalArgumentException("같은 회차를 두 번 가리킵니다: " + session.sessionId());
            }
        }
        if (seatId != null && sessions.size() > 1) {
            throw new IllegalArgumentException("좌석이 있는 티켓은 회차를 하나만 가리킵니다 — 지정형은 묶음권이 불가합니다: 회차 " + sessions.size() + "개");
        }
    }

    private static void requireNotForfeited(List<TicketSession> sessions) {
        if (sessions.stream().anyMatch(TicketSession::isForfeited)) {
            throw new IllegalArgumentException("발급하는 티켓에 포기 표시가 붙은 회차가 있습니다");
        }
    }

    /**
     * 예매 티켓의 금액 — 회차마다 산 때의 가격이 있고, 일반 티켓은 그 회차 가격이 곧 기본 가격, 묶음권은 기본 가격(할인율을 적용한 묶음권 가격)이
     * 날짜별 가격 합계를 넘지 않는다(docs/05 "가격의 층위"). 이미 돌려준 부분 금액은 결제 금액을 넘지 않는다.
     */
    private static void requireBookingMoney(List<TicketSession> sessions, TicketPrice price, Money partiallyRefundedAmount) {
        if (price == null) {
            throw new IllegalArgumentException("예매 티켓은 장당 금액 · 적용 할인이 있습니다");
        }
        if (sessions.stream().anyMatch(session -> session.purchasedPrice().isEmpty())) {
            throw new IllegalArgumentException("예매 티켓은 회차마다 산 때의 가격이 있습니다");
        }
        Money dayPriceTotal = Money.sum(sessions.stream()
                .map(session -> session.purchasedPrice().map(SessionPrice::amount).orElseThrow())
                .toList());
        if (sessions.size() == 1 && !dayPriceTotal.equals(price.basePrice())) {
            throw new IllegalArgumentException("일반 티켓의 기본 가격은 그 회차 가격입니다: 기본 가격 " + price.basePrice() + ", 회차 가격 " + dayPriceTotal);
        }
        if (price.basePrice().isGreaterThan(dayPriceTotal)) {
            throw new IllegalArgumentException("묶음권 가격이 날짜별 가격 합계보다 큽니다: " + price.basePrice() + " > " + dayPriceTotal);
        }
        if (partiallyRefundedAmount.isGreaterThan(price.price())) {
            throw new IllegalArgumentException("이미 돌려준 부분 금액이 결제 금액보다 큽니다: " + partiallyRefundedAmount + " > " + price.price());
        }
    }

    /** 초대권 티켓에는 돈이 오가지 않는다 — 금액 · 산 때의 가격 · 부분 환불 · 공제 면제 · 배상이 없다(docs/08 "초대권"). */
    private static void requireNoInvitationMoney(List<TicketSession> sessions, TicketPrice price, Money partiallyRefundedAmount,
            Set<DeductionExemptionCause> exemptionCauses, boolean compensatedOnCancel) {
        if (price != null || sessions.stream().anyMatch(session -> session.purchasedPrice().isPresent())) {
            throw new IllegalArgumentException("초대권 티켓에는 금액이 없습니다");
        }
        if (!partiallyRefundedAmount.isZero()) {
            throw new IllegalArgumentException("초대권 티켓은 돌려준 금액이 없습니다: " + partiallyRefundedAmount);
        }
        if (!exemptionCauses.isEmpty() || compensatedOnCancel) {
            throw new IllegalArgumentException("초대권 티켓에는 공제 면제 · 배상이 붙지 않습니다 — 환불이 없습니다");
        }
    }

    /**
     * 공제 면제 · 배상 · 포기는 서로 맞물린다. 포기는 묶음권에만 붙고, 묶음권에는 출연자 변경 면제가 붙지 않으며(그날 포기가 그 권리다), 취소 시
     * 배상은 출연자 변경으로만 붙는다(docs/04 "공제 면제", "묶음권은 바뀐 날만 포기합니다").
     */
    private static void requireRefundRights(List<TicketSession> sessions, Set<DeductionExemptionCause> exemptionCauses,
            boolean compensatedOnCancel) {
        boolean bundle = sessions.size() > 1;
        if (!bundle && sessions.stream().anyMatch(TicketSession::isForfeited)) {
            throw new IllegalArgumentException("회차별 포기는 묶음권에만 붙습니다");
        }
        if (bundle && exemptionCauses.contains(DeductionExemptionCause.PERFORMER_CHANGE)) {
            throw new IllegalArgumentException("묶음권에는 출연자 변경 면제가 붙지 않습니다 — 바뀐 날만 포기합니다");
        }
        if (compensatedOnCancel && !exemptionCauses.contains(DeductionExemptionCause.PERFORMER_CHANGE)) {
            throw new IllegalArgumentException("취소 시 배상은 출연자 변경으로만 붙습니다");
        }
    }
}
