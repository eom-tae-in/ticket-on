package com.ticketon.domain.payment;

import com.ticketon.domain.pricing.Money;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 결제·환불 내역의 한 줄 — <b>돈이 움직일 때마다 한 줄</b>이다(docs/08 "돈이 움직일 때마다 영수증이 하나씩"). 화면에서 줄을 열면 "결제 상세"다.
 * <b>"영수증"이라 부르지 않는다</b> — 세금계산서 · 현금영수증은 만들지 않으므로 그렇게 쓰면 그걸 기대하게 된다(docs/08 "'영수증'이라 부르지
 * 않습니다", docs/99 B "세금계산서·현금영수증·자동 송금").
 * <pre>
 * 예매번호 ...0091
 * 3/1  결제    165,000원   [상세]
 * 3/5  환불    −35,000원   [상세]  2매 취소
 * 3/8  환불    −50,000원   [상세]  12열 10번 변경
 * 3/8  결제     30,000원   [상세]  15열 3번
 * </pre>
 * 한 줄은 종류 · 까닭 · 금액 · 시각 · 대상 티켓들을 갖는다. 대상 티켓은 id로 가리키고 좌석 표기를 글자로 복사하지 않는다 — 블록 이름 오타를 고치면
 * 내역의 표기도 따라 바뀌어야 한다(docs/99 B "티켓에 좌석 표기를 글자로 복사"). 금액은 대상 티켓 몫을 모두 더한 값이다.
 * <p>
 * <b>0원 줄은 없다</b> — 돈이 움직이지 않았으면 줄을 남기지 않는다. 새 줄은 내역({@link PaymentHistory})의 기록 메서드로만 만들고, 저장소에서 다시
 * 읽을 때는 {@link #restore}를 쓴다. 저장 전 줄은 id가 없다.
 */
public final class PaymentHistoryEntry {

    private final Long id;
    private final PaymentHistoryEntryKind kind;
    private final PaymentHistoryCause cause;
    private final Money amount;
    private final Instant occurredAt;
    private final List<Long> ticketIds;

    private PaymentHistoryEntry(Long id, PaymentHistoryEntryKind kind, PaymentHistoryCause cause, Money amount,
            Instant occurredAt, List<Long> ticketIds) {
        if (id != null && id < 1) {
            throw new IllegalArgumentException("내역 줄 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!cause.allows(kind)) {
            throw new IllegalArgumentException("이 까닭으로는 " + kind.label() + " 줄이 남지 않습니다: " + cause);
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("돈이 움직이지 않은 줄은 남기지 않습니다: " + kind.label() + " 0원, " + cause);
        }
        List<Long> targetTicketIds = List.copyOf(ticketIds);
        requireTicketIds(targetTicketIds);
        this.id = id;
        this.kind = kind;
        this.cause = cause;
        this.amount = amount;
        this.occurredAt = occurredAt;
        this.ticketIds = targetTicketIds;
    }

    /** 새 줄 — 내역의 기록 메서드가 만든다. */
    static PaymentHistoryEntry record(PaymentHistoryEntryKind kind, PaymentHistoryCause cause, Money amount,
            Instant occurredAt, List<Long> ticketIds) {
        return new PaymentHistoryEntry(null, kind, cause, amount, occurredAt, ticketIds);
    }

    /**
     * 저장소에서 읽은 값으로 줄을 되살린다. 종류와 까닭이 맞는지, 금액이 1원 이상인지, 대상 티켓이 있는지는 다시 본다. 내역 안에서 맞물리는 규칙은
     * {@link PaymentHistory#restore}가 본다.
     *
     * @param ticketIds 대상 티켓 id, 티켓 순번 순서대로
     * @throws IllegalArgumentException 규칙에 맞지 않으면
     */
    public static PaymentHistoryEntry restore(long id, PaymentHistoryEntryKind kind, PaymentHistoryCause cause,
            Money amount, Instant occurredAt, List<Long> ticketIds) {
        return new PaymentHistoryEntry(id, kind, cause, amount, occurredAt, ticketIds);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /** 결제 / 환불 / 배상 / 지연 환급. */
    public PaymentHistoryEntryKind kind() {
        return kind;
    }

    /** 이 줄이 생긴 까닭. */
    public PaymentHistoryCause cause() {
        return cause;
    }

    /** 움직인 금액, 1원 이상. 부호 없이 담고 방향은 종류가 말한다. */
    public Money amount() {
        return amount;
    }

    /** 돈이 움직인 시각. */
    public Instant occurredAt() {
        return occurredAt;
    }

    /** 대상 티켓 id들, 티켓 순번 순서대로. */
    public List<Long> ticketIds() {
        return ticketIds;
    }

    private static void requireTicketIds(List<Long> ticketIds) {
        if (ticketIds.isEmpty()) {
            throw new IllegalArgumentException("내역 줄에는 대상 티켓이 하나 이상 있습니다");
        }
        Set<Long> distinctTicketIds = new HashSet<>();
        for (long ticketId : ticketIds) {
            if (ticketId < 1) {
                throw new IllegalArgumentException("티켓 id는 1 이상이어야 합니다: " + ticketId);
            }
            if (!distinctTicketIds.add(ticketId)) {
                throw new IllegalArgumentException("같은 티켓이 한 줄에 두 번 있습니다: " + ticketId);
            }
        }
    }

    @Override
    public String toString() {
        return "PaymentHistoryEntry[" + (id == null ? "(저장 전)" : id) + " " + kind.label() + " " + amount + " " + cause
                + " 티켓 " + ticketIds + " " + occurredAt + "]";
    }
}
