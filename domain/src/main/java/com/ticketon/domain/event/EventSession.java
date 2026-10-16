package com.ticketon.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 회차 — 날짜 아래에서 재고가 붙는 유일한 곳(docs/02 "날짜와 회차", docs/14 "2층 — 재고"). 좌석별 판매 상태 · 정원 · 판매 제외는 재고가 회차를
 * 가리켜 갖고, 티켓도 회차를 가리킨다.
 * <ul>
 *   <li><b>시간</b> — 회차형만 회차가 시간을 갖는다({@link TimedSessionTime} — 시작 필수, 종료 선택). 종일형 회차는 시간을 갖지 않고 그
 *       날짜의 운영 시간을 쓴다({@link EventSchedule#operatingHoursOn}).</li>
 *   <li><b>출연</b>(선택, {@link SessionPerformers}) — 회차마다 누가 나오는지.</li>
 *   <li><b>가격 조정</b>({@link SessionPriceAdjustment}) — 회차당 하나, 모든 구역에 같이 적용. 처음에는 조정 없음.</li>
 *   <li><b>취소 시각</b> — 비어 있으면 취소되지 않은 회차다. 취소된 회차는 '마지막 회차'에서 빠진다({@link EventSchedule#lastSession}).
 *       회차를 취소하는 일(환불 · 배상 · 알림)은 회차 취소(#98)가 한다.</li>
 * </ul>
 * 회차의 시각(예매 종료 · 검표 시작 · 회차 상태)은 날짜와 운영 시간을 알아야 계산되므로 일정이 읽어 준다({@link EventSchedule#timesOf}).
 * 시간 · 출연 · 가격 조정을 바꾸는 일도 같은 날짜의 다른 회차와 맞춰 봐야 해서 일정을 거친다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 회차는 id가 없다.
 */
public final class EventSession {

    private final Long id;
    private TimedSessionTime time;
    private SessionPerformers performers;
    private SessionPriceAdjustment priceAdjustment;
    private final Instant cancelledAt;

    private EventSession(Long id, TimedSessionTime time, SessionPerformers performers, SessionPriceAdjustment priceAdjustment,
            Instant cancelledAt) {
        this.id = id;
        this.time = time;
        this.performers = performers;
        this.priceAdjustment = Objects.requireNonNull(priceAdjustment, "priceAdjustment");
        this.cancelledAt = cancelledAt;
    }

    /** 회차형의 새 회차 — 출연 없음, 가격 조정 없음. */
    static EventSession newTimed(TimedSessionTime time) {
        return new EventSession(null, Objects.requireNonNull(time, "time"), null, SessionPriceAdjustment.none(), null);
    }

    /** 종일형의 새 회차 — 시간은 그 날짜의 운영 시간을 쓴다. 출연 없음, 가격 조정 없음. */
    static EventSession newAllDay() {
        return new EventSession(null, null, null, SessionPriceAdjustment.none(), null);
    }

    /**
     * 저장소에서 읽은 값으로 회차를 되살린다. 회차 방식과 맞는지(회차형만 시간이 있다)는 일정이 되살릴 때 맞춰 본다({@link EventSchedule#restore}).
     *
     * @param time        회차형 회차의 시간. 종일형이면 null
     * @param performers  출연. 없으면 null
     * @param cancelledAt 취소 시각. 취소되지 않았으면 null
     */
    public static EventSession restore(long id, TimedSessionTime time, SessionPerformers performers,
            SessionPriceAdjustment priceAdjustment, Instant cancelledAt) {
        if (id < 1) {
            throw new IllegalArgumentException("회차 id는 1 이상이어야 합니다: " + id);
        }
        return new EventSession(id, time, performers, priceAdjustment, cancelledAt);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /** 회차형 회차의 시간. 종일형 회차는 비어 있다 — 그 날짜의 운영 시간을 쓴다. */
    public Optional<TimedSessionTime> time() {
        return Optional.ofNullable(time);
    }

    /** 출연. 적지 않았으면 비어 있다. */
    public Optional<SessionPerformers> performers() {
        return Optional.ofNullable(performers);
    }

    public SessionPriceAdjustment priceAdjustment() {
        return priceAdjustment;
    }

    /** 취소된 회차인지. */
    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** 취소 시각. 취소되지 않았으면 비어 있다. */
    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    boolean hasId(long sessionId) {
        return id != null && id == sessionId;
    }

    /** @return 바뀌었는지 */
    boolean changeTime(TimedSessionTime newTime) {
        Objects.requireNonNull(newTime, "newTime");
        if (time == null) {
            throw new IllegalStateException("종일형 회차는 시간을 갖지 않습니다 — 그 날짜의 운영 시간을 바꿉니다");
        }
        if (newTime.equals(time)) {
            return false;
        }
        time = newTime;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean changePerformers(SessionPerformers newPerformers) {
        if (Objects.equals(newPerformers, performers)) {
            return false;
        }
        performers = newPerformers;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean changePriceAdjustment(SessionPriceAdjustment newPriceAdjustment) {
        Objects.requireNonNull(newPriceAdjustment, "newPriceAdjustment");
        if (newPriceAdjustment.equals(priceAdjustment)) {
            return false;
        }
        priceAdjustment = newPriceAdjustment;
        return true;
    }
}
