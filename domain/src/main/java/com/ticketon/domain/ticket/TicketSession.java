package com.ticketon.domain.ticket;

import com.ticketon.domain.pricing.SessionPrice;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 티켓이 가리키는 회차 하나 — 회차 id, 산 때의 그 회차 가격, 회차별 포기 표시(docs/07 "티켓이 직접 갖는 것과 가리키기만 하는 것").
 * <ul>
 *   <li><b>회차는 가리킨다</b> — 연기하면 티켓 날짜도 따라 바뀌어야 하므로 날짜를 복사하지 않고 회차 id만 갖는다.</li>
 *   <li><b>산 때의 회차 가격은 직접 갖는다</b>(예매 티켓만) — 묶음권의 그날 몫은 결제 금액 × 그날 가격 ÷ 날짜별 가격 합계이고, 그날 가격은
 *       티켓이 가리키는 그 날 회차의 가격이다(docs/08 "묶음권 중 하루가 취소되면"). 주최자는 판매 중에도 가격을 바꿀 수 있지만 신규 예매부터
 *       적용된다(docs/04 "예매가 열린 뒤 수정 범위") — 지금 가격으로 나누면 이미 산 표의 몫이 바뀌므로, "바뀌어도 티켓은 그대로여야 하는 것"으로
 *       산 때의 가격을 담는다. 일반 티켓은 그 회차 가격이 곧 기본 가격이다.</li>
 *   <li><b>포기 표시는 직접 갖는다</b> — 묶음권에서 출연자가 바뀐 날을 관객이 포기하면 그 회차를 계속 가리키되 포기 표시와 시각이 붙는다. 회차를
 *       빼면 검표가 "환불받은 날짜"와 "처음부터 다른 날짜 표"를 가를 수 없다(docs/07 "출연자가 바뀐 날을 포기하면", docs/99 D "묶음권 포기한
 *       날").</li>
 * </ul>
 *
 * @param sessionId      회차 id, 1 이상
 * @param purchasedPrice 산 때의 그 회차 가격 — 예매 티켓만 있고 초대권 티켓은 비어 있다
 * @param forfeitedAt    포기한 시각 — 포기하지 않았으면 비어 있다
 */
public record TicketSession(long sessionId, Optional<SessionPrice> purchasedPrice, Optional<Instant> forfeitedAt) {

    public TicketSession {
        if (sessionId < 1) {
            throw new IllegalArgumentException("회차 id는 1 이상이어야 합니다: " + sessionId);
        }
        Objects.requireNonNull(purchasedPrice, "purchasedPrice");
        Objects.requireNonNull(forfeitedAt, "forfeitedAt");
    }

    /** 예매 티켓이 가리키는 회차 — 산 때의 그 회차 가격과 함께. 포기하지 않았다. */
    public static TicketSession purchased(long sessionId, SessionPrice purchasedPrice) {
        return new TicketSession(sessionId, Optional.of(Objects.requireNonNull(purchasedPrice, "purchasedPrice")),
                Optional.empty());
    }

    /** 초대권 티켓이 가리키는 회차 — 가격이 없다. */
    public static TicketSession invited(long sessionId) {
        return new TicketSession(sessionId, Optional.empty(), Optional.empty());
    }

    /**
     * 저장소에서 읽은 값으로 되살린다.
     *
     * @param purchasedPrice 산 때의 그 회차 가격. 초대권 티켓이면 null
     * @param forfeitedAt    포기한 시각. 포기하지 않았으면 null
     */
    public static TicketSession restore(long sessionId, SessionPrice purchasedPrice, Instant forfeitedAt) {
        return new TicketSession(sessionId, Optional.ofNullable(purchasedPrice), Optional.ofNullable(forfeitedAt));
    }

    /** 그 회차를 포기했는지. */
    public boolean isForfeited() {
        return forfeitedAt.isPresent();
    }

    /** 이 회차를 지금 포기한 것으로. */
    TicketSession forfeit(Instant now) {
        Objects.requireNonNull(now, "now");
        return new TicketSession(sessionId, purchasedPrice, Optional.of(now));
    }
}
