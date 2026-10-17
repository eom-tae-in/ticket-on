package com.ticketon.domain.ticket;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.SeatLabel;
import com.ticketon.domain.event.SessionTimes;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.pricing.BundleDayPrices;
import com.ticketon.domain.pricing.DiscountKind;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.SessionPrice;
import com.ticketon.domain.pricing.TicketPrice;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * 티켓이 저장하는 상태는 "취소됐나"(취소 시각 · 사유)뿐이다. "입장 완료"와 "만료(공연 종료)"는 저장하지 않고 회차의 시각과 입장 기록 여부로 그때마다
 * 계산한다({@link #display}, docs/14 "저장하는 것과 계산하는 것").
 * <p>
 * 새 티켓은 {@link #issueForBooking} · {@link #issueForInvitation}으로 발급하고, 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전
 * 티켓은 id가 없다. 발급하는 흐름은 #74(예매) · #86(초대권)이, 저장은 #57이 맡는다.
 */
public final class Ticket {

    private final Long id;
    private final TicketParent parent;
    private final int sequence;
    private List<TicketSession> sessions;
    private final long zoneId;
    private final Long seatId;
    private final TicketPrice price;
    private Money partiallyRefundedAmount;
    private final Set<DeductionExemptionCause> deductionExemptionCauses;
    private boolean compensatedOnCancel;
    private TicketCancellation cancellation;
    private QrKey qrKey;

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

    /**
     * 티켓을 취소한다 — 취소 시각과 사유를 남긴다(docs/07 "티켓은 '취소됐나'만 기록합니다"). "유효 / 취소됨" 칸을 고르는 setter는 없다. 취소된
     * 티켓도 지우지 않고 예매 카드에 회색으로 남는다 — 관객이 직접 하지 않은 취소(회차 취소 · 관리자 정지)를 예매 내역만 열어도 알 수 있게 한다
     * (docs/07 "취소된 티켓도 예매 카드에 남습니다"). 변경도 기존 티켓을 '좌석 변경'으로 취소하고 새 티켓을 발급한다 — "변경됨" 상태는 없다
     * (docs/14 "변경은 티켓을 바꿔치웁니다").
     * <p>
     * 취소할 수 있는지(취소 마감 · 입장 기록 · 묶음권 전체 취소)와 환불액은 환불 계산(#78)이, 좌석 · 쿠폰을 돌려놓는 일은 각 흐름이 한다.
     *
     * @param reason 취소 사유 — 이 티켓의 부모에 붙을 수 있는 사유
     * @throws RuleViolationException   이미 취소된 티켓이면({@link TicketErrorCode#TICKET_ALREADY_CANCELLED})
     * @throws IllegalArgumentException 이 티켓의 부모에 붙을 수 없는 사유면 — 예매 티켓에 '초대권 회수', 초대권 티켓에 '관객 취소'
     */
    public void cancel(TicketCancellationReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(now, "now");
        if (!reason.appliesTo(parent.kind())) {
            throw new IllegalArgumentException(parent.kind() + " 티켓에 붙을 수 없는 취소 사유입니다: " + reason);
        }
        requireNotCancelled();
        cancellation = new TicketCancellation(now, reason);
    }

    /**
     * QR 열쇠를 새로 바꾼다 — <b>열쇠만 바뀌고 티켓은 그대로</b>다(docs/07 "QR 재발행"). 밖에 돌아다니는 옛 QR과 옛 열쇠로 만든 공유 링크가 즉시
     * 무효가 된다. 관객이 직접 재발행하지 않는다 — 신청은 관객, 처리는 관리자다(docs/99 B "관객의 QR 직접 재발행", 흐름은 #128).
     *
     * @param newKey 추측할 수 없는 새 열쇠 — 서비스가 TokenGenerator로 만들어 넘긴다
     * @return 바뀌기 전 열쇠와 바뀐 시각 — 재발행 이력에 보관한다. 옛 캡처를 위조로 오해하지 않게 하려는 것이다
     * @throws RuleViolationException   이미 취소된 티켓이면({@link TicketErrorCode#TICKET_ALREADY_CANCELLED}) — 취소된 티켓의 QR은 이미 무효다
     * @throws IllegalArgumentException 새 열쇠가 지금 열쇠와 같으면
     */
    public PreviousQrKey reissueKey(QrKey newKey, Instant now) {
        Objects.requireNonNull(newKey, "newKey");
        Objects.requireNonNull(now, "now");
        requireNotCancelled();
        if (newKey.equals(qrKey)) {
            throw new IllegalArgumentException("새 QR 열쇠가 지금 열쇠와 같습니다: 티켓 " + describe());
        }
        PreviousQrKey previous = new PreviousQrKey(qrKey, now);
        qrKey = newKey;
        return previous;
    }

    /** 그 열쇠가 이 티켓의 지금 열쇠인지 — 아니면 재발행 전 열쇠다(검표 · 공유 링크가 옛 열쇠 이력으로 이 티켓을 찾았을 때). */
    public boolean isCurrentKey(QrKey key) {
        Objects.requireNonNull(key, "key");
        return qrKey.equals(key);
    }

    /**
     * 그 열쇠로 만든 공유 링크가 무효인 이유 — 유효하면 비어 있다(docs/07 "공유 링크의 수명"). 링크는 로그인 없이 열리고 그 티켓의 QR만 보여 준다.
     * <p>
     * 이유가 겹치면 <b>취소 → 행사 종료 → 재발행</b> 순으로 하나를 고른다. 취소된 티켓은 새 링크를 받아도 들어갈 수 없으므로 "취소된 티켓입니다"가,
     * 끝난 행사는 새 링크가 소용없으므로 "종료된 행사입니다"가 재발행 안내보다 앞선다. 확인 필요 (#56) — 겹칠 때의 순서는 문서에 없다.
     *
     * @param linkKey     링크에 담긴 열쇠 — 지금 열쇠이거나, 옛 열쇠 이력으로 이 티켓을 찾은 재발행 전 열쇠
     * @param eventStatus 그 행사의 지금 상태. 행사 종료 · 정산 완료 · 취소됨이면 행사가 끝난 것이다({@link EventStatus#isFinished})
     */
    public Optional<ShareLinkInvalidReason> shareLinkInvalidReason(QrKey linkKey, EventStatus eventStatus) {
        Objects.requireNonNull(linkKey, "linkKey");
        Objects.requireNonNull(eventStatus, "eventStatus");
        if (isCancelled()) {
            return Optional.of(ShareLinkInvalidReason.CANCELLED);
        }
        if (eventStatus.isFinished()) {
            return Optional.of(ShareLinkInvalidReason.EVENT_ENDED);
        }
        if (!isCurrentKey(linkKey)) {
            return Optional.of(ShareLinkInvalidReason.REISSUED);
        }
        return Optional.empty();
    }

    /**
     * 공제 면제를 붙인다 — 주최자 쪽 사정으로 산 표의 내용이 바뀌었다(docs/04 "공제 면제"). 이 티켓은 이후 언제 취소해도 공제 0%다. <b>출연자
     * 변경</b>이면 취소 시 10% 배상 표시도 함께 붙는다. 여러 까닭이 겹쳐 붙을 수 있고, 이미 붙은 까닭이면 그대로다.
     * <p>
     * 붙는 대상(변경 시점에 이미 산, 바뀐 회차의 표)을 고르는 일은 각 흐름(연기 #106 · 장소 변경 · 회차 시간 수정 #105 · 출연자 변경)이 한다.
     * 변경으로 생긴 새 티켓에 면제를 옮길 때는 이 메서드가 아니라 {@link #inheritDeductionExemptionsFrom}을 쓴다 — 배상은 옮기지 않는다.
     *
     * @return 바뀌었는지 — 새 까닭이 붙었거나 배상 표시가 새로 붙었으면
     * @throws IllegalStateException 초대권 티켓이면(환불이 없다), 취소된 티켓이면, 묶음권에 출연자 변경이면 — 묶음권은 바뀐 날만 포기한다
     *                               ({@link #forfeitSession})
     */
    public boolean exemptFromDeduction(DeductionExemptionCause cause) {
        Objects.requireNonNull(cause, "cause");
        requireBookingTicket("공제 면제");
        requireNotCancelledState("공제 면제");
        if (cause == DeductionExemptionCause.PERFORMER_CHANGE && isBundle()) {
            throw new IllegalStateException("묶음권에는 출연자 변경 면제가 붙지 않습니다 — 바뀐 날만 포기합니다: 티켓 " + describe());
        }
        boolean added = deductionExemptionCauses.add(cause);
        if (cause == DeductionExemptionCause.PERFORMER_CHANGE && !compensatedOnCancel) {
            compensatedOnCancel = true;
            return true;
        }
        return added;
    }

    /**
     * 변경으로 바뀐 기존 티켓의 공제 면제를 이 새 티켓에 옮긴다(docs/08 "공제 면제가 붙은 티켓"). 연기 때문에 자리를 옮기는 경우가 많은데 옮기는 순간
     * 면제를 잃으면 연기 피해자가 두 번 손해를 본다. <b>출연자 변경의 10% 배상은 옮기지 않는다</b> — 배상은 그 회차를 포기하고 취소할 때만 붙고,
     * 다른 회차로 바꾼 사람은 원하던 공연을 보러 가는 것이다. 변경 흐름은 #80이 맡는다.
     *
     * @param replaced 변경으로 취소되는(또는 취소된) 기존 티켓 — 같은 예매 건의 티켓
     * @throws IllegalArgumentException 같은 예매 건의 티켓이 아니거나, 둘 중 하나가 묶음권 · 초대권이면 — 묶음권 · 초대권은 변경할 수 없다
     * @throws IllegalStateException    이 티켓이 취소됐으면
     */
    public void inheritDeductionExemptionsFrom(Ticket replaced) {
        Objects.requireNonNull(replaced, "replaced");
        if (!parent.isBooking() || !replaced.parent.isBooking() || isBundle() || replaced.isBundle()) {
            throw new IllegalArgumentException("묶음권 · 초대권은 변경할 수 없어 면제를 옮기지 않습니다: 티켓 " + describe() + " ← " + replaced.describe());
        }
        if (parent.id() != replaced.parent.id()) {
            throw new IllegalArgumentException("다른 예매 건의 티켓에서 면제를 옮기지 않습니다: 티켓 " + describe() + " ← " + replaced.describe());
        }
        requireNotCancelledState("공제 면제");
        deductionExemptionCauses.addAll(replaced.deductionExemptionCauses);
    }

    /**
     * 묶음권에서 출연자가 바뀐 날을 포기한다(docs/04 "묶음권은 바뀐 날만 포기합니다", docs/07 "출연자가 바뀐 날을 포기하면"). 티켓은 그 회차를
     * <b>계속 가리키되 포기 표시</b>가 붙는다 — 빼면 검표가 "환불받은 날짜"와 "처음부터 다른 날짜 표"를 가를 수 없고, 예매 카드에 "5/4 포기"를
     * 보여 줄 근거도 사라진다. 나머지 날은 그대로 들어간다.
     * <p>
     * 그날 몫({@link #dayShare}) 환불과 10% 배상은 출연자 변경 흐름이 계산해 돌려주고, 돌려준 그날 몫은 {@link #recordPartialRefund}로 남긴다.
     * 포기할 수 있는지(그 회차에 출연자 변경 알림이 있었는지, 그날 입장 전인지)도 그 흐름이 본다. 첫날 입장했어도 포기할 수 있다.
     *
     * @throws RuleViolationException   이미 취소된 티켓이면({@link TicketErrorCode#TICKET_ALREADY_CANCELLED}), 이미 포기한 날이면
     *                                  ({@link TicketErrorCode#SESSION_ALREADY_FORFEITED})
     * @throws IllegalArgumentException 이 티켓이 가리키지 않는 회차면
     * @throws IllegalStateException    초대권 티켓이거나 묶음권이 아니면 — 포기는 예매한 묶음권의 권리다
     */
    public void forfeitSession(long sessionId, Instant now) {
        Objects.requireNonNull(now, "now");
        requireBookingTicket("회차별 포기");
        if (!isBundle()) {
            throw new IllegalStateException("회차별 포기는 묶음권만 합니다: 티켓 " + describe());
        }
        requireNotCancelled();
        TicketSession session = sessionOf(sessionId);
        if (session.isForfeited()) {
            throw new RuleViolationException(TicketErrorCode.SESSION_ALREADY_FORFEITED,
                    "티켓 " + describe() + ", 회차 " + sessionId + ", 포기 " + session.forfeitedAt().orElseThrow());
        }
        sessions = sessions.stream()
                .map(pointed -> pointed.sessionId() == sessionId ? pointed.forfeit(now) : pointed)
                .toList();
    }

    /**
     * 그 회차를 포기했는지 — 포기한 날에 QR을 찍으면 "환불받은 날짜입니다"로 막힌다(docs/07, 검표 #90).
     *
     * @throws IllegalArgumentException 이 티켓이 가리키지 않는 회차면
     */
    public boolean isForfeited(long sessionId) {
        return sessionOf(sessionId).isForfeited();
    }

    /**
     * 그 회차의 그날 몫 = 결제 금액 × 그날 가격 ÷ 날짜별 가격 합계, 원 미만 버림(docs/08 "묶음권 중 하루가 취소되면", docs/99 C "묶음권 그날 몫").
     * 그날 가격은 티켓이 가리키는 그 날 회차의 <b>산 때의 가격</b>이다 — 주최자가 그 뒤 가격을 바꿔도 몫은 그대로다.
     * <pre>
     * 3일권 130,200원 (40,000 + 50,000 + 50,000 = 140,000원, 7% 할인) — 토요일 몫  130,200 × 50,000 ÷ 140,000 = 46,500원
     * 3일권 94,500원 (35,000 × 3, 10% 할인) − 쿠폰 5,000원 = 89,500원 — 둘째 날 몫  89,500 × 35,000 ÷ 105,000 = 29,833원
     * </pre>
     * 주최자가 하루를 취소할 때, 출연자가 바뀐 날을 포기할 때, 묶음권의 공연 지연 환급(그날 몫의 10%)에 쓴다. 일반 티켓은 회차가 하나라 결제 금액
     * 전부다. 몫은 늘 처음 결제 금액으로 나눈다 — 이미 다른 날 몫을 돌려줬어도 줄지 않는다. 배상 · 환급액 계산은 #96이 한다.
     *
     * @throws IllegalArgumentException 이 티켓이 가리키지 않는 회차면
     * @throws IllegalStateException    초대권 티켓이면 — 결제가 없다
     */
    public Money dayShare(long sessionId) {
        requireBookingTicket("그날 몫");
        SessionPrice dayPrice = sessionOf(sessionId).purchasedPrice().orElseThrow();
        return BundleDayPrices.of(sessions.stream().map(session -> session.purchasedPrice().orElseThrow()).toList())
                .dayShare(paidAmount(), dayPrice);
    }

    /**
     * 일부를 돌려준 금액을 남긴다 — 묶음권 그날 몫(주최자의 하루 취소 · 출연자 변경 포기)이나 공연 지연 환급(docs/08 "기준 금액"). 티켓은 그대로
     * 유효하다. 남긴 금액만큼 환불 기준액이 줄어 같은 돈을 두 번 돌려주지 않는다({@link #refundBaseAmount}). 배상은 돌려준 입장료가 아니라 여기
     * 넣지 않는다 — 이미 받은 배상은 그대로 둔다(docs/08 "부분 취소").
     *
     * @param amount 돌려준 금액, 1원 이상
     * @throws IllegalArgumentException 0원이거나 환불 기준액보다 크면
     * @throws IllegalStateException    초대권 티켓이면(결제가 없다), 취소된 티켓이면
     */
    public void recordPartialRefund(Money amount) {
        Objects.requireNonNull(amount, "amount");
        requireBookingTicket("부분 환불");
        requireNotCancelledState("부분 환불");
        if (amount.isZero()) {
            throw new IllegalArgumentException("돌려준 금액은 1원 이상이어야 합니다");
        }
        if (amount.isGreaterThan(refundBaseAmount())) {
            throw new IllegalArgumentException("돌려준 금액이 환불 기준액보다 큽니다: " + amount + " > " + refundBaseAmount() + ", 티켓 " + describe());
        }
        partiallyRefundedAmount = partiallyRefundedAmount.plus(amount);
    }

    /**
     * 환불 기준액 = 결제 금액 − 이미 돌려준 부분 금액(docs/08 "기준 금액", docs/99 D "일부 환불된 묶음권의 전체 취소"). 항상 실제 결제액이 기준이고,
     * 일부를 돌려받았으면 그만큼 뺀다. 3일권 130,200원에서 토요일 몫 46,500원을 돌려줬으면 83,700원이다. 공제율 · 공제 기준일은 환불 계산(#78)이
     * 본다. 초대권 티켓은 0원이다.
     */
    public Money refundBaseAmount() {
        return paidAmount().minus(partiallyRefundedAmount);
    }

    /**
     * 관객 화면에 그릴 모습을 계산한다 — 저장하지 않는다(docs/07 "관객 화면의 표시는 계산됩니다"). 차례대로 본다.
     * <ol>
     *   <li>취소 시각이 있으면 <b>취소됨 · 사유</b>.</li>
     *   <li>가리키는 회차가 모두 끝났으면 <b>공연 종료</b> — 입장 기록이 있어도 그렇다. 1년 전 공연에 "입장 완료"가 붙어 있을 필요가 관객에게 없다
     *       (docs/07 "입장 기록은 30일 후 소멸합니다"). 확인 필요 (#56) — 입장 기록이 남은 30일 동안 어느 쪽을 보일지는 문서에 없다.</li>
     *   <li>입장 기록이 있으면 <b>입장 완료</b>.</li>
     *   <li>그 밖에는 <b>유효</b>.</li>
     * </ol>
     * QR · 공유 버튼은 취소됨 · 공연 종료에서 빠진다({@link TicketDisplay#showsQrAndShareButtons}).
     *
     * @param sessionTimes   가리키는 회차마다 그 회차의 시각 — 회차 id로 찾는다. 다른 회차가 더 있어도 된다
     * @param hasEntryRecord 이 티켓에 입장 기록이 하나라도 있는지 — 입장 기록은 검표(checkin)가 갖고, 티켓에 "입장했음"을 남기지 않는다(docs/99 B
     *                       "티켓의 '입장했음' 요약")
     * @throws IllegalArgumentException 가리키는 회차의 시각이 빠졌으면
     */
    public TicketDisplay display(Map<Long, SessionTimes> sessionTimes, boolean hasEntryRecord, Instant now) {
        boolean allSessionsEnded = hasAllSessionsEnded(sessionTimes, now);
        if (cancellation != null) {
            return TicketDisplay.cancelled(cancellation.reason());
        }
        if (allSessionsEnded) {
            return TicketDisplay.of(TicketDisplayStatus.PERFORMANCE_ENDED);
        }
        return TicketDisplay.of(hasEntryRecord ? TicketDisplayStatus.ENTERED : TicketDisplayStatus.VALID);
    }

    /**
     * 가리키는 회차가 모두 끝났는지 — 그러면 QR · 공유 버튼을 그리지 않는다(docs/07 "화면에서는 버튼만 빠집니다"). <b>판단은 티켓마다</b>다. 8회차
     * 행사에서 3/14 표는 3/14 공연이 끝나면 버튼이 사라지고, 3일권은 마지막 날이 끝나야 사라진다.
     * <p>
     * 회차가 끝난 때는 회차 상태의 '종료'와 같다 — 회차형은 종료 시각, 종일형은 운영 종료, 그 순간부터 끝난 것이다({@link SessionTimes#endsAt}).
     * 회차형에서 종료 시각을 비워 두었으면 끝났는지 알 수 없어 끝나지 않은 것으로 본다 — 행사 종료도 그때는 판정하지 않는다(docs/04 "회차 종료
     * 시각이 없으면 행사 종료를 판정할 수 없습니다"). 취소된 회차 · 포기한 회차도 그 회차의 시각이 지나야 끝난 것으로 센다. 확인 필요 (#56) —
     * 문서는 "가리키는 회차가 모두 끝났으면"만 적는다.
     *
     * @param sessionTimes 가리키는 회차마다 그 회차의 시각 — 회차 id로 찾는다. 다른 회차가 더 있어도 된다
     * @throws IllegalArgumentException 가리키는 회차의 시각이 빠졌으면
     */
    public boolean hasAllSessionsEnded(Map<Long, SessionTimes> sessionTimes, Instant now) {
        Objects.requireNonNull(sessionTimes, "sessionTimes");
        Objects.requireNonNull(now, "now");
        boolean allEnded = true;
        for (TicketSession session : sessions) {
            SessionTimes times = sessionTimes.get(session.sessionId());
            if (times == null) {
                throw new IllegalArgumentException("가리키는 회차의 시각이 없습니다: 티켓 " + describe() + ", 회차 " + session.sessionId());
            }
            boolean ended = times.endsAt().map(endsAt -> !now.isBefore(endsAt)).orElse(false);
            allEnded = allEnded && ended;
        }
        return allEnded;
    }

    /**
     * 티켓 표기 — 지정석은 "R석 12열 7번", 수량형은 "스탠딩A · 0091-2"(docs/07 "티켓 표기"). 이름은 티켓에 복사하지 않고 그릴 때마다 구역 ·
     * 좌석 · 블록에서 지금 이름을 읽어 넘긴다 — 블록 이름 오타를 고치면 이미 팔린 표의 표기도 따라 바뀐다.
     *
     * @param zoneName  가리키는 구역의 지금 이름(지운 구역이면 그 기록에 남은 이름)
     * @param seatLabel 가리키는 좌석의 표기({@link PointedSeat#label}) — 지정석만 있다
     * @throws IllegalArgumentException 지정석인데 좌석 표기가 없거나, 수량형인데 좌석 표기가 있으면
     */
    public TicketLabel label(ZoneName zoneName, Optional<SeatLabel> seatLabel) {
        Objects.requireNonNull(zoneName, "zoneName");
        Objects.requireNonNull(seatLabel, "seatLabel");
        if ((seatId != null) != seatLabel.isPresent()) {
            throw new IllegalArgumentException("지정석에만 좌석 표기가 있습니다: 티켓 " + describe() + ", 좌석 " + seatId + ", 표기 " + seatLabel);
        }
        return seatLabel
                .map(seat -> TicketLabel.designated(zoneName, seat))
                .orElseGet(() -> TicketLabel.quantity(zoneName, number()));
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

    private void requireNotCancelled() {
        if (cancellation != null) {
            throw new RuleViolationException(TicketErrorCode.TICKET_ALREADY_CANCELLED,
                    "티켓 " + describe() + ", " + cancellation.cancelledAt() + " " + cancellation.reason());
        }
    }

    /** 취소된 티켓에 붙이지 않는 것(면제 · 부분 환불) — 흐름이 살아 있는 티켓만 골라 부르므로 여기 오면 프로그램 오류다. */
    private void requireNotCancelledState(String what) {
        if (cancellation != null) {
            throw new IllegalStateException("취소된 티켓에는 " + what + "을(를) 남기지 않습니다: 티켓 " + describe());
        }
    }

    private void requireBookingTicket(String what) {
        if (!parent.isBooking()) {
            throw new IllegalStateException("초대권 티켓에는 " + what + "이(가) 없습니다 — 결제 · 환불이 없습니다: 티켓 " + describe());
        }
    }

    private TicketSession sessionOf(long sessionId) {
        return sessions.stream()
                .filter(session -> session.sessionId() == sessionId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이 티켓이 가리키지 않는 회차입니다: 티켓 " + describe() + ", 회차 " + sessionId));
    }

    private String describe() {
        return (id == null ? "(저장 전)" : id) + " " + number();
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
