package com.ticketon.domain.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 예매 건의 티켓별 가격 — 기본 가격 위에 <b>조기예매 또는 쿠폰 중 관객에게 유리한 하나만</b> 적용한 결과(docs/05 "가격의 층위", "할인 중복 규칙").
 * <pre>
 * 구역 기본가
 *   + 회차별 조정
 *   (묶음권이면 날짜별 합계 × (1 − 묶음권 할인율), 100원 내림)
 *   − 조기예매 또는 쿠폰     ← 둘 중 유리한 하나만
 *   = 티켓 가격
 * </pre>
 * 관객이 고르는 할인은 없다 — 조기예매는 몫을 받은 티켓에 저절로 붙고 쿠폰은 입력만 받으며, 둘 중 유리한 쪽을 시스템이 고른다. 자동으로 고르므로
 * 무엇이 적용됐는지({@link #appliedDiscount})를 결제 내역에 보여 준다.
 *
 * <h2>고르는 법</h2>
 * <ol>
 *   <li>조기예매 쪽 — 조기예매 몫을 받은 티켓에 조기예매를 적용하고, 나머지는 정가.</li>
 *   <li>쿠폰 쪽 — 모든 티켓에 쿠폰을 적용한다. 정률은 티켓마다 적용해 100원 단위로 내리고, 정액은 예매 건에 붙는 성격이라 티켓 가격에 비례해 나눈다
 *       ({@link Money#splitProportionally} — 4매에 5,000원이면 장당 1,250원, 나누어떨어지지 않아도 합이 맞는다). 정액이 예매 건 합계보다 크면 합계까지만
 *       나눈다.</li>
 *   <li>합계가 더 적은 쪽을 고른다. 같으면 조기예매 쪽 — 쿠폰을 쓰지 않고 남겨 둔다(쿠폰은 전체 사용 횟수 · 1인 1회가 걸린 권리다).</li>
 * </ol>
 * 확인 필요 (#48) — 조기예매와 쿠폰의 중복 불가를 예매 건 전체로 본다. 4매 중 2매만 조기예매 몫을 받았을 때 두 장은 조기예매, 두 장은 쿠폰처럼
 * 한 예매 건 안에서 섞지 않는다. 정액 쿠폰은 예매 건의 모든 티켓에 나누는 것이라(docs/05) 섞으면 나눌 대상이 정해지지 않는다. 합계가 같을 때 조기예매
 * 쪽을 고르는 것도 확인 필요 (#48)이다.
 * <p>
 * 티켓 순서는 티켓 순번이다 — 정액 쿠폰을 나누고 남은 원을 줄 때 앞 순번이 먼저다. 이 결과는 결제 시점에 티켓마다 저장한다(#51, #73).
 *
 * @param tickets 티켓별 가격, 티켓 순번대로. 하나 이상이고, 조기예매와 쿠폰이 섞이지 않는다
 */
public record BookingPrice(List<TicketPrice> tickets) {

    public BookingPrice {
        tickets = List.copyOf(tickets);
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("예매 건에 티켓이 없습니다");
        }
        boolean hasEarlyBird = tickets.stream().anyMatch(ticket -> ticket.discountKind() == DiscountKind.EARLY_BIRD);
        boolean hasCoupon = tickets.stream().anyMatch(ticket -> ticket.discountKind() == DiscountKind.COUPON);
        if (hasEarlyBird && hasCoupon) {
            throw new IllegalArgumentException("조기예매와 쿠폰은 한 예매 건에 함께 적용하지 않습니다");
        }
    }

    /**
     * 할인 전 티켓들에 조기예매 또는 쿠폰 중 유리한 하나를 적용한다.
     *
     * @param tickets 할인 전 티켓, 티켓 순번대로. 하나 이상
     * @param coupon  예매 건에 입력한 쿠폰의 할인. 입력하지 않았으면 비어 있다. 쓸 수 있는 쿠폰인지(기간 · 횟수 · 1인 1회)는 쿠폰 규칙(#49)이 먼저 본다
     */
    public static BookingPrice calculate(List<TicketBeforeDiscount> tickets, Optional<Discount> coupon) {
        List<TicketBeforeDiscount> orderedTickets = List.copyOf(tickets);
        Objects.requireNonNull(coupon, "coupon");
        BookingPrice earlyBirdSide = new BookingPrice(orderedTickets.stream().map(BookingPrice::earlyBirdOrRegular).toList());
        if (coupon.isEmpty()) {
            return earlyBirdSide;
        }
        BookingPrice couponSide = withCoupon(orderedTickets, coupon.get());
        return couponSide.total().isLessThan(earlyBirdSide.total()) ? couponSide : earlyBirdSide;
    }

    /** 결제 금액 — 티켓 가격의 합계. */
    public Money total() {
        return Money.sum(tickets.stream().map(TicketPrice::price).toList());
    }

    /** 이 예매 건에 적용된 할인 — 쿠폰, 조기예매(한 장이라도 받았으면), 없음 중 하나. */
    public DiscountKind appliedDiscount() {
        if (tickets.stream().anyMatch(ticket -> ticket.discountKind() == DiscountKind.COUPON)) {
            return DiscountKind.COUPON;
        }
        if (tickets.stream().anyMatch(ticket -> ticket.discountKind() == DiscountKind.EARLY_BIRD)) {
            return DiscountKind.EARLY_BIRD;
        }
        return DiscountKind.NONE;
    }

    private static TicketPrice earlyBirdOrRegular(TicketBeforeDiscount ticket) {
        return ticket.earlyBird()
                .map(earlyBird -> TicketPrice.discounted(ticket.basePrice(), DiscountKind.EARLY_BIRD, earlyBird.applyTo(ticket.basePrice())))
                .orElseGet(() -> TicketPrice.undiscounted(ticket.basePrice()));
    }

    private static BookingPrice withCoupon(List<TicketBeforeDiscount> tickets, Discount coupon) {
        List<Money> basePrices = tickets.stream().map(TicketBeforeDiscount::basePrice).toList();
        List<Money> couponShares = switch (coupon) {
            case Discount.Rate rate -> basePrices.stream().map(basePrice -> basePrice.minus(rate.applyTo(basePrice))).toList();
            case Discount.FixedAmount fixedAmount -> fixedAmount.amount().min(Money.sum(basePrices)).splitProportionally(basePrices);
        };
        List<TicketPrice> prices = new ArrayList<>(tickets.size());
        for (int index = 0; index < tickets.size(); index++) {
            prices.add(new TicketPrice(basePrices.get(index), DiscountKind.COUPON, couponShares.get(index)));
        }
        return new BookingPrice(prices);
    }
}
