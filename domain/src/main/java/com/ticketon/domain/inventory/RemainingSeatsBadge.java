package com.ticketon.domain.inventory;

import java.util.Collection;
import java.util.Objects;

/**
 * 잔여 표시 — 남은 자리로 계산하는 표시(docs/13 "잔여 좌석 표시"). 상태가 아니라 계산되는 값이다 — 매진을 상태로 두면 취소표가 나올 때마다
 * 되돌려야 한다(docs/04 "'매진'은 상태가 아닙니다"). 목록 카드는 임박할 때만 배지를 단다 — "480석 남음"을 띄우면 모든 카드가 안 팔리는 공연처럼
 * 보인다.
 */
public enum RemainingSeatsBadge {

    /** 표시 없음 — 매진도 매진 임박도 아니다. */
    NONE,

    /** 매진 임박 — 잔여 10% 이하, 또는 10석 이하이면서 절반 이상 팔림(docs/99 C "잔여 배지", {@link RemainingSeats#isAlmostSoldOut}). */
    ALMOST_SOLD_OUT,

    /** 매진 — 잔여 0. */
    SOLD_OUT;

    /**
     * 목록 카드의 표시 — 아직 예매할 수 있는 회차마다의 표시로 정한다(docs/13 "잔여 좌석 표시", docs/99 D "목록 카드의 매진 임박").
     * <ul>
     *   <li>하나라도 임박하면 매진 임박 — 서윤은 원하는 회차를 확실하게 잡고 싶어 해서, 그 회차가 거의 찼다는 신호가 목록에서부터 보여야 한다.
     *       어느 회차인지는 상세의 회차 목록이 말해 준다.</li>
     *   <li>전부 0이면 매진.</li>
     *   <li>그 밖은 표시 없음 — 매진된 회차와 여유 있는 회차만 섞여 있으면 임박한 회차가 없어 표시하지 않는다(문서의 두 줄을 그대로 읽음,
     *       확인 필요 #223). 예매할 수 있는 회차가 없어도 표시하지 않는다.</li>
     * </ul>
     *
     * @param bookableSessionBadges 아직 예매할 수 있는 회차마다의 표시 — 회차의 표시는 그 회차 모든 구역의 잔여를 더해 본다({@link RemainingSeats#sum})
     */
    public static RemainingSeatsBadge forEventCard(Collection<RemainingSeatsBadge> bookableSessionBadges) {
        Objects.requireNonNull(bookableSessionBadges, "bookableSessionBadges");
        if (bookableSessionBadges.contains(ALMOST_SOLD_OUT)) {
            return ALMOST_SOLD_OUT;
        }
        if (!bookableSessionBadges.isEmpty() && bookableSessionBadges.stream().allMatch(badge -> badge == SOLD_OUT)) {
            return SOLD_OUT;
        }
        return NONE;
    }
}
