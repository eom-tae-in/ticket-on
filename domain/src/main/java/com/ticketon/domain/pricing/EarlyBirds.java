package com.ticketon.domain.pricing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 한 행사의 조기예매들 — 행사당 여러 개(1차 · 2차 얼리버드)를 둘 수 있다(docs/05 "종료 조건").
 *
 * <h2>기간이 겹치면 저장할 때 경고한다</h2>
 * 막지 않고 경고 정보만 준다({@link #overlapping}, {@link #overlappingOthers}). 확인 필요 (#49) — 문서대로 기간만 본다. 적용 범위가 겹치지 않아도
 * (1일권만 켠 것과 3일권만 켠 것) 기간이 겹치면 경고한다 — docs/15 이도현의 "얼리버드로 1일권 30,000원, 3일권 85,000원"처럼 같은 기간에 상품마다
 * 다른 조기예매를 두면 경고가 뜬다.
 *
 * <h2>한 티켓에 닿는 조기예매가 여럿이면</h2>
 * 몫을 잡을 때({@link #claim}) 그 구역 · 상품에 닿고 기간 안인 조기예매를 아래 순서로 보고, 앞의 것에서 몫이 모자라면 다음 것에서 잡는다. 티켓 한
 * 장에는 조기예매가 하나만 붙는다.
 * <ol>
 *   <li>관객에게 유리한 쪽 — 그 기본 가격에 적용한 가격이 낮은 것(docs/05 "가장 유리한 하나만 자동 적용"의 뜻을 따름)</li>
 *   <li>같으면 기간이 먼저 끝나는 것</li>
 *   <li>같으면 목록 순서(만든 순서)</li>
 * </ol>
 * 확인 필요 (#49) — 겹친 조기예매 중 고르는 순서와, 앞의 것에서 모자란 몫을 다음 것에서 채우는 것은 문서에 없다. 겹치지 않으면 기간 안인 조기예매는
 * 하나뿐이라 순서가 필요 없다.
 */
public final class EarlyBirds {

    private final List<EarlyBird> earlyBirds;

    private EarlyBirds(List<EarlyBird> earlyBirds) {
        this.earlyBirds = List.copyOf(earlyBirds);
        long distinctEvents = this.earlyBirds.stream().mapToLong(EarlyBird::eventId).distinct().count();
        if (distinctEvents > 1) {
            throw new IllegalArgumentException("한 행사의 조기예매만 묶을 수 있습니다");
        }
    }

    /** 한 행사의 조기예매들, 만든 순서대로. 비어 있어도 된다. */
    public static EarlyBirds of(List<EarlyBird> earlyBirds) {
        return new EarlyBirds(earlyBirds);
    }

    public List<EarlyBird> all() {
        return earlyBirds;
    }

    /** 새로 만들 조기예매의 기간과 겹치는 조기예매 — 저장할 때 경고 정보. 겹치지 않으면 비어 있다. */
    public List<EarlyBird> overlapping(DiscountPeriod period) {
        Objects.requireNonNull(period, "period");
        return earlyBirds.stream().filter(earlyBird -> earlyBird.terms().period().overlaps(period)).toList();
    }

    /** 그 조기예매의 기간을 바꿀 때 겹치는 다른 조기예매 — 자기 자신은 빼고 본다. */
    public List<EarlyBird> overlappingOthers(long earlyBirdId, DiscountPeriod newPeriod) {
        Objects.requireNonNull(newPeriod, "newPeriod");
        return earlyBirds.stream()
                .filter(earlyBird -> earlyBird.id().isEmpty() || earlyBird.id().getAsLong() != earlyBirdId)
                .filter(earlyBird -> earlyBird.terms().period().overlaps(newPeriod))
                .toList();
    }

    /**
     * 그 구역의 그 상품 티켓 N장에 조기예매 몫을 잡는다 — 좌석을 선점하는 순간 부른다(docs/05 "좌석을 선점하는 그 순간 조기예매 자리도 함께
     * 잡힙니다"). 잡은 만큼 그 조기예매의 사용 수가 늘고 줄지 않는다. 묶음권 1장은 몫 1개다 — 3일권 4장은 4개.
     *
     * @param zoneId      티켓의 구역 id
     * @param product     티켓을 산 상품
     * @param basePrice   티켓 한 장의 기본 가격(회차 가격 또는 묶음권 가격) — 유리한 조기예매를 고르고 할인 전 티켓을 만드는 데 쓴다
     * @param ticketCount 티켓 수, 1 이상
     * @throws IllegalStateException 몫을 잡아야 할 조기예매가 저장 전이면
     */
    public EarlyBirdAllocation claim(long zoneId, TicketProduct product, Money basePrice, int ticketCount, Instant now) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(now, "now");
        List<EarlyBird> candidates = earlyBirds.stream()
                .filter(earlyBird -> earlyBird.covers(zoneId, product) && earlyBird.terms().period().contains(now))
                .sorted(favorableFirst(basePrice))
                .toList();
        if (candidates.isEmpty()) {
            return EarlyBirdAllocation.notApplicable(basePrice, ticketCount);
        }
        if (candidates.stream().anyMatch(candidate -> candidate.id().isEmpty())) {
            throw new IllegalStateException("저장 전 조기예매에서는 몫을 잡을 수 없습니다");
        }
        List<EarlyBirdShare> shares = new ArrayList<>();
        int needed = ticketCount;
        for (EarlyBird candidate : candidates) {
            if (needed == 0) {
                break;
            }
            int granted = candidate.claim(needed, now);
            if (granted > 0) {
                shares.add(new EarlyBirdShare(candidate.id().getAsLong(), candidate.terms().discount(), granted));
                needed -= granted;
            }
        }
        return new EarlyBirdAllocation(basePrice, ticketCount, true, shares);
    }

    /** 유리한 쪽(적용한 가격이 낮은 것) → 기간이 먼저 끝나는 것. 같으면 목록 순서 그대로(정렬이 안정적이다). */
    private static Comparator<EarlyBird> favorableFirst(Money basePrice) {
        return Comparator.<EarlyBird, Money>comparing(earlyBird -> earlyBird.terms().discount().applyTo(basePrice))
                .thenComparing(earlyBird -> earlyBird.terms().period().endsAt());
    }
}
