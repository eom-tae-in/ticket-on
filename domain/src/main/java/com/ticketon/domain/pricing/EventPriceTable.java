package com.ticketon.domain.pricing;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Zone;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 한 행사의 가격표 — 구역 기본가에 회차 조정을 적용한 회차 가격(구역 × 회차)과 묶음권 상품의 가격(docs/05 "가격의 층위"). 위저드 4단계의 가격
 * 미리보기와, 조기예매 · 쿠폰을 정할 때와 가격을 바꾼 뒤에 0원이 되는 가격이 없는지 보는 데 쓴다.
 * <pre>
 * 구역 기본가               ← 가격이 붙는 곳 (도면)
 *   + 회차별 조정           ← 평일/주말, 프리뷰 (일정)
 *   (묶음권이면 날짜별 합계 × (1 − 묶음권 할인율), 100원 내림)
 *   − 조기예매 또는 쿠폰     ← 둘 중 유리한 하나만
 *   = 티켓 가격
 * </pre>
 * <b>고를 수 있는 회차만 본다</b> — 일정에서 취소되지 않은 회차. 그 회차의 예매가 마감됐는지는 보지 않는다 — 주최자가 위저드에서 보는 가격표라 지금
 * 살 수 있는지보다 정해 둔 가격을 보인다. 회차 방식을 아직 고르지 않았으면(일정 없음) 회차 가격도 묶음권 가격도 없다.
 *
 * <h2>할인이 닿는 가장 낮은 가격</h2>
 * 조기예매 · 쿠폰의 할인은 닿는 가격 가운데 가장 낮은 가격에 적용해도 1원 이상 남아야 한다(docs/99 C "할인 값 (조기예매·쿠폰)",
 * {@link Discount#requireLeavesPositivePrice}). 조기예매는 적용 범위가 닿는 가격({@link #lowestPriceReachedBy}) — 끄지 않은 구역의 1일권(회차
 * 가격)과 끄지 않은 묶음권 상품의 가장 싼 조합 가격. 쿠폰은 묶음권에도 쓸 수 있어 그 행사의 모든 가격({@link #lowestPrice})이다.
 *
 * <h2>가격을 바꾼 뒤의 점검</h2>
 * 할인을 정할 때만 보면, 그 뒤 구역 기본가나 회차 조정을 낮추거나 가격이 낮은 구역 · 회차를 더해 0원이 되는 가격이 생긴다. 그래서 가격 목록이
 * 바뀔 때마다 다시 본다({@link #requirePricesStayPositive}) — 막지 않으면 관객이 예매하는 순간 주최자용 문구로 막힌다.
 */
public final class EventPriceTable {

    private final FloorPlan floorPlan;
    private final EventSchedule schedule;
    private final List<BundleProduct> bundleProducts;

    private EventPriceTable(FloorPlan floorPlan, EventSchedule schedule, List<BundleProduct> bundleProducts) {
        this.floorPlan = floorPlan;
        this.schedule = schedule;
        this.bundleProducts = bundleProducts;
    }

    /**
     * @param floorPlan      그 행사의 도면 — 지워지지 않은 구역과 기본가
     * @param schedule       그 행사의 일정. 회차 방식을 아직 고르지 않았으면 비어 있다
     * @param bundleProducts 그 행사의 지우지 않은 묶음권 상품
     * @throws IllegalArgumentException 일정 · 묶음권 상품이 다른 행사의 것이면
     */
    public static EventPriceTable of(FloorPlan floorPlan, Optional<EventSchedule> schedule, List<BundleProduct> bundleProducts) {
        Objects.requireNonNull(floorPlan, "floorPlan");
        Objects.requireNonNull(schedule, "schedule");
        List<BundleProduct> products = List.copyOf(Objects.requireNonNull(bundleProducts, "bundleProducts"));
        long eventId = floorPlan.eventId();
        if (schedule.isPresent() && schedule.get().eventId() != eventId) {
            throw new IllegalArgumentException("다른 행사의 일정입니다: 도면 " + eventId + ", 일정 " + schedule.get().eventId());
        }
        if (products.stream().anyMatch(product -> product.eventId() != eventId)) {
            throw new IllegalArgumentException("다른 행사의 묶음권 상품이 있습니다: 행사 " + eventId);
        }
        if (!products.isEmpty() && schedule.isEmpty()) {
            throw new IllegalArgumentException("일정 없는 행사에 묶음권 상품이 있습니다: 행사 " + eventId);
        }
        return new EventPriceTable(floorPlan, schedule.orElse(null), products);
    }

    /**
     * 회차 가격 — 취소되지 않은 회차마다(일정 순서), 구역마다(도면 순서) 구역 기본가에 그 회차의 조정을 적용한 가격(docs/05 "회차별 가격 조정").
     * 위저드 4단계의 가격 미리보기다.
     * <blockquote>S석 기본 50,000원 — 3/14(금) 조정 없음 50,000원 · 3/15(토) +10,000원 60,000원 · 3/12(수, 프리뷰) -20% 40,000원</blockquote>
     * 0원 이하가 되는 조합은 가격이 비어 있다 — 그런 조합은 저장할 때 막지만({@link #requirePricesStayPositive}), 막기 전에 저장된 값도 화면에 보여야
     * 고칠 수 있다.
     */
    public List<SessionZonePrice> sessionPrices() {
        List<SessionZonePrice> prices = new ArrayList<>();
        for (EventSession session : selectableSessions()) {
            for (Zone zone : zones()) {
                Optional<Money> price = SessionPrice.isValid(zone.basePrice(), session.priceAdjustment())
                        ? Optional.of(SessionPrice.of(zone.basePrice(), session.priceAdjustment()).amount())
                        : Optional.empty();
                prices.add(new SessionZonePrice(session.id().getAsLong(), zone.id().getAsLong(), price));
            }
        }
        return List.copyOf(prices);
    }

    /**
     * 묶음권 상품의 시작 가격 — 가장 싼 회차 조합의 가격("3일권 40,000원~", {@link BundleProduct#startingPrice}). 취소되지 않은 회차를 모두 고를 수
     * 있는 것으로 본다.
     *
     * @return 시작 가격. 한 날짜라도 고를 회차가 없거나(지워진 날짜를 묶은 경우 포함), 할인율을 적용해 0원이 되면 비어 있다
     */
    public Optional<Money> startingPriceOf(BundleProduct bundleProduct) {
        Objects.requireNonNull(bundleProduct, "bundleProduct");
        if (schedule == null || !bundleProduct.leavesPositivePrice(schedule, floorPlan)) {
            return Optional.empty();
        }
        return bundleProduct.startingPrice(schedule, floorPlan, selectableSessionIds());
    }

    /**
     * 그 적용 범위의 조기예매가 닿는 가격 가운데 가장 낮은 가격 — 1일권을 끄지 않았으면 끄지 않은 구역의 회차 가격, 끄지 않은 묶음권 상품마다 끄지
     * 않은 대상 구역의 가장 싼 조합 가격. 0원 이하가 되는 회차 가격 · 묶음권 가격은 가격이 아니라 빼고 본다 — 그런 가격은
     * {@link #requirePricesStayPositive}가 따로 막는다.
     *
     * @return 가장 낮은 가격. 닿는 가격이 없으면(구역 · 회차가 아직 없는 등) 비어 있다
     */
    public Optional<Money> lowestPriceReachedBy(EarlyBirdScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (schedule == null) {
            return Optional.empty();
        }
        Stream<Money> singleSessionPrices = zones().stream()
                .filter(zone -> scope.covers(zone.id().getAsLong(), TicketProduct.singleSession()))
                .flatMap(zone -> selectableSessions().stream()
                        .filter(session -> SessionPrice.isValid(zone.basePrice(), session.priceAdjustment()))
                        .map(session -> SessionPrice.of(zone.basePrice(), session.priceAdjustment()).amount()));
        Stream<Money> bundlePrices = bundleProducts.stream()
                .filter(bundleProduct -> bundleProduct.leavesPositivePrice(schedule, floorPlan))
                .flatMap(bundleProduct -> zones().stream()
                        .filter(zone -> scope.covers(zone.id().getAsLong(), bundleProduct.product()))
                        .map(zone -> bundleProduct.cheapestPriceIn(zone.id().getAsLong(), schedule, floorPlan, selectableSessionIds()))
                        .flatMap(Optional::stream));
        return Stream.concat(singleSessionPrices, bundlePrices).min(Comparator.naturalOrder());
    }

    /** 그 행사의 모든 가격 가운데 가장 낮은 가격 — 쿠폰이 닿는 가격이다(묶음권에도 쓴다, docs/05 "쿠폰"). 없으면 비어 있다. */
    public Optional<Money> lowestPrice() {
        return lowestPriceReachedBy(EarlyBirdScope.everything());
    }

    /**
     * 조기예매를 이 행사에 정할 수 있는지 — 더할 때와 고칠 때 본다.
     * <ol>
     *   <li><b>끈 구역 · 상품이 이 행사의 것인지</b> — 도면에 있는 구역, 지우지 않은 묶음권 상품.</li>
     *   <li><b>한 티켓에라도 닿는지</b> — 구역 하나 이상과, 그 구역에서 파는 상품(1일권, 그 구역을 파는 묶음권) 하나 이상을 남겨야 한다. 확인 필요
     *       (#223) — 모두 끈 조기예매는 할인을 한 장도 주지 않으면서 기간 겹침 경고만 띄운다. 구역이 아직 없으면 보지 않는다.</li>
     *   <li><b>할인이 닿는 가장 낮은 가격에 적용해도 1원 이상 남는지</b>(docs/99 C "할인 값 (조기예매·쿠폰)").</li>
     * </ol>
     *
     * @throws NotFoundException      도면에 없는 구역이면({@link com.ticketon.domain.event.EventErrorCode#ZONE_NOT_FOUND}), 이 행사에 없는 묶음권
     *                                상품이면({@link PricingErrorCode#BUNDLE_PRODUCT_NOT_FOUND})
     * @throws RuleViolationException 아무 티켓에도 닿지 않으면({@link PricingErrorCode#EARLY_BIRD_SCOPE_EMPTY}), 할인을 적용해 0원이 되면
     *                                ({@link PricingErrorCode#DISCOUNT_MAKES_PRICE_ZERO})
     */
    public void requireEarlyBirdApplicable(EarlyBirdTerms terms) {
        Objects.requireNonNull(terms, "terms");
        EarlyBirdScope scope = terms.scope();
        scope.excludedZoneIds().forEach(floorPlan::zone);
        Set<Long> bundleProductIds = bundleProducts.stream().map(product -> product.id().orElseThrow()).collect(Collectors.toSet());
        for (TicketProduct product : scope.excludedProducts()) {
            if (product instanceof TicketProduct.Bundle bundle && !bundleProductIds.contains(bundle.bundleProductId())) {
                throw new NotFoundException(PricingErrorCode.BUNDLE_PRODUCT_NOT_FOUND,
                        "행사 " + floorPlan.eventId() + ", 묶음권 " + bundle.bundleProductId());
            }
        }
        if (!zones().isEmpty() && !reachesAnyTicket(scope)) {
            throw new RuleViolationException(PricingErrorCode.EARLY_BIRD_SCOPE_EMPTY, "행사 " + floorPlan.eventId() + ", 범위 " + scope);
        }
        lowestPriceReachedBy(scope).ifPresent(terms.discount()::requireLeavesPositivePrice);
    }

    /**
     * 쿠폰을 이 행사에 정할 수 있는지 — 할인이 그 행사의 가장 낮은 가격에 적용해도 1원 이상 남는지(docs/99 C "할인 값 (조기예매·쿠폰)").
     *
     * @throws RuleViolationException 할인을 적용해 0원이 되면({@link PricingErrorCode#DISCOUNT_MAKES_PRICE_ZERO})
     */
    public void requireCouponApplicable(CouponTerms terms) {
        Objects.requireNonNull(terms, "terms");
        lowestPrice().ifPresent(terms.discount()::requireLeavesPositivePrice);
    }

    /**
     * 가격 목록이 바뀐 뒤(구역 기본가 · 회차 조정을 바꾸거나 구역 · 회차를 더한 뒤) 0원이 되는 가격이 없는지 점검한다.
     * <ol>
     *   <li><b>회차 가격</b> — 모든 구역 × 취소되지 않은 회차가 1원 이상이다({@link SessionPrice}). 50,000원 구역에 −60,000원 조정을 저장하면 그 회차를
     *       예매할 수 없다(#48).</li>
     *   <li><b>묶음권 가격</b> — 묶음권 상품마다 구역별 가장 싼 조합에 할인율을 적용해도 1원 이상이다({@link BundleProduct#leavesPositivePrice}).</li>
     *   <li><b>조기예매 · 쿠폰</b> — 끝나지 않은 조기예매는 닿는 가장 낮은 가격에, 유효 기간이 끝나지 않은 쿠폰은 그 행사의 가장 낮은 가격에 할인을
     *       적용해도 1원 이상이다. 확인 필요 (#223) — 끝난 조기예매 · 쿠폰은 더 쓰이지 않아 보지 않는다. 지난달에 끝난 쿠폰 때문에 가격을 못
     *       낮추는 일이 없게 했다.</li>
     * </ol>
     *
     * @param earlyBirds 그 행사의 지우지 않은 조기예매
     * @param coupons    그 행사의 지우지 않은 쿠폰
     * @throws RuleViolationException 0원 이하가 되는 회차 가격이 있으면({@link PricingErrorCode#SESSION_PRICE_NOT_POSITIVE}), 묶음권 · 조기예매 ·
     *                                쿠폰을 적용해 0원이 되면({@link PricingErrorCode#PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO})
     */
    public void requirePricesStayPositive(EarlyBirds earlyBirds, List<Coupon> coupons, Instant now) {
        Objects.requireNonNull(earlyBirds, "earlyBirds");
        Objects.requireNonNull(coupons, "coupons");
        Objects.requireNonNull(now, "now");
        for (EventSession session : selectableSessions()) {
            zones().forEach(zone -> SessionPrice.of(zone.basePrice(), session.priceAdjustment()));
        }
        for (BundleProduct bundleProduct : bundleProducts) {
            if (!bundleProduct.leavesPositivePrice(schedule, floorPlan)) {
                throw discountedPriceZero("묶음권 " + bundleProduct.id().orElseThrow());
            }
        }
        for (EarlyBird earlyBird : earlyBirds.all()) {
            if (!earlyBird.status(now).isClosed() && !leavesPositivePrice(earlyBird.terms().discount(),
                    lowestPriceReachedBy(earlyBird.terms().scope()))) {
                throw discountedPriceZero("조기예매 " + earlyBird.id().orElseThrow());
            }
        }
        for (Coupon coupon : coupons) {
            if (!coupon.terms().validity().hasEndedAt(now) && !leavesPositivePrice(coupon.discount(), lowestPrice())) {
                throw discountedPriceZero("쿠폰 " + coupon.id().orElseThrow());
            }
        }
    }

    private static boolean leavesPositivePrice(Discount discount, Optional<Money> lowestPrice) {
        return lowestPrice.map(discount::leavesPositivePrice).orElse(true);
    }

    private RuleViolationException discountedPriceZero(String detail) {
        return new RuleViolationException(PricingErrorCode.PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO, "행사 " + floorPlan.eventId() + ", " + detail);
    }

    /** 끄지 않은 구역에서 끄지 않은 상품(1일권, 또는 그 구역을 파는 묶음권)이 하나라도 있는지. */
    private boolean reachesAnyTicket(EarlyBirdScope scope) {
        return zones().stream().anyMatch(zone -> {
            long zoneId = zone.id().getAsLong();
            return scope.covers(zoneId, TicketProduct.singleSession()) || bundleProducts.stream()
                    .anyMatch(product -> product.terms().zoneIds().contains(zoneId) && scope.covers(zoneId, product.product()));
        });
    }

    /** 도면에 있는 구역 — 도면 순서. 저장소에서 읽은 도면이라 모두 id가 있다. */
    private List<Zone> zones() {
        return floorPlan.zones().stream().filter(zone -> zone.id().isPresent()).toList();
    }

    /** 고를 수 있는 회차 — 취소되지 않은 회차, 일정 순서. 일정이 없으면 비어 있다. */
    private List<EventSession> selectableSessions() {
        if (schedule == null) {
            return List.of();
        }
        return schedule.sessions().stream().filter(session -> !session.isCancelled() && session.id().isPresent()).toList();
    }

    private Set<Long> selectableSessionIds() {
        return selectableSessions().stream().map(session -> session.id().getAsLong()).collect(Collectors.toSet());
    }

    /**
     * 한 회차 한 구역의 가격.
     *
     * @param sessionId 회차 id
     * @param zoneId    구역 id
     * @param price     회차 가격. 0원 이하가 되는 조합이면 비어 있다
     */
    public record SessionZonePrice(long sessionId, long zoneId, Optional<Money> price) {

        public SessionZonePrice {
            Objects.requireNonNull(price, "price");
        }
    }
}
