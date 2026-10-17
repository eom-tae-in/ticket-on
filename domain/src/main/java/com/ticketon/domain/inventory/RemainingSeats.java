package com.ticketon.domain.inventory;

import com.ticketon.domain.policy.QuantityPolicy;
import com.ticketon.domain.policy.QuantityThreshold;
import java.util.Collection;
import java.util.Objects;

/**
 * 잔여 — 남은 자리와 전체 자리(docs/13 "잔여 좌석 표시"). 매진 · 매진 임박을 여기서 판정한다({@link #badge}).
 * <ul>
 *   <li><b>매진</b> — 잔여 0. 상태가 아니라 잔여가 0인 순간의 표시다(docs/04 "'매진'은 상태가 아닙니다").</li>
 *   <li><b>매진 임박</b> — 잔여 10% 이하 또는 10석 이하({@link QuantityPolicy#REMAINING_SEATS_BADGE}). 둘 중 하나만 맞아도 임박이다.
 *       확인 필요 (#53) — 10%는 반올림하기 전 비율로 본다: 200석에 20석 남으면(10%) 임박, 21석 남으면(10.5%) 아니다(1회 선택 매수
 *       경고선과 같은 방식, {@code domain.event.SeatsPerSelectionFeedback}).</li>
 * </ul>
 * 확인 필요 (#53) — 10%의 분모(전체)는 그 구역의 자리 전부다: 수량형은 정원, 지정형은 그 구역에 배정된 활성 좌석 수. 판매 제외 좌석 · 관계자
 * 확보분도 분모에 든다 — 문서는 "잔여 10% 이하"만 적었고, 관객이 보는 객석 크기는 정원이다. 그래서 정원이 10석 이하인 구역은 하나도 팔리지 않아도
 * '10석 이하'라 매진 임박이다(문서 그대로).
 * <p>
 * 여러 구역 · 회차의 잔여는 더해 본다({@link #plus}, {@link #sum}). 행사 카드에 어느 회차 · 구역을 더할지(끝난 회차 · 취소된 회차를 빼는지)는
 * 보여 주는 쪽이 정한다.
 *
 * @param remaining 남은 자리 (0 이상, 전체 이하)
 * @param total     전체 자리 (0 이상)
 */
public record RemainingSeats(int remaining, int total) {

    private static final long PERCENT = 100;

    /**
     * @throws IllegalArgumentException 음수이거나 남은 자리가 전체보다 많으면
     */
    public RemainingSeats {
        if (remaining < 0 || total < 0) {
            throw new IllegalArgumentException("잔여와 전체는 0 이상이어야 합니다: 잔여 " + remaining + ", 전체 " + total);
        }
        if (remaining > total) {
            throw new IllegalArgumentException("잔여가 전체보다 많을 수 없습니다: 잔여 " + remaining + ", 전체 " + total);
        }
    }

    public static RemainingSeats of(int remaining, int total) {
        return new RemainingSeats(remaining, total);
    }

    /** 여러 잔여를 더한다. 비어 있으면 잔여 0 / 전체 0이다. */
    public static RemainingSeats sum(Collection<RemainingSeats> parts) {
        Objects.requireNonNull(parts, "parts");
        return parts.stream().reduce(new RemainingSeats(0, 0), RemainingSeats::plus);
    }

    /** 남은 자리끼리, 전체끼리 더한다. */
    public RemainingSeats plus(RemainingSeats other) {
        Objects.requireNonNull(other, "other");
        return new RemainingSeats(Math.addExact(remaining, other.remaining), Math.addExact(total, other.total));
    }

    /** 매진인지 — 잔여 0. 팔 자리가 처음부터 없어도(전체 0) 매진으로 본다. */
    public boolean isSoldOut() {
        return remaining == 0;
    }

    /** 매진 임박인지 — 매진이 아니고, 잔여 10석 이하 또는 전체의 10% 이하. */
    public boolean isAlmostSoldOut() {
        if (isSoldOut()) {
            return false;
        }
        QuantityThreshold line = QuantityPolicy.REMAINING_SEATS_BADGE;
        return remaining <= line.count() || remaining * PERCENT <= (long) total * line.percentOfTotal().value();
    }

    /** 잔여 표시 — 매진 · 매진 임박 · 표시 없음. */
    public RemainingSeatsBadge badge() {
        if (isSoldOut()) {
            return RemainingSeatsBadge.SOLD_OUT;
        }
        return isAlmostSoldOut() ? RemainingSeatsBadge.ALMOST_SOLD_OUT : RemainingSeatsBadge.NONE;
    }
}
