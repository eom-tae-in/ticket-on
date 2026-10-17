package com.ticketon.domain.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 예매 건의 티켓별 가격 — 티켓마다 기본 가격 위에 <b>조기예매 또는 쿠폰 중 관객에게 유리한 하나만</b> 적용한 결과(docs/05 "가격의 층위", "할인 중복
 * 규칙").
 * <pre>
 * 구역 기본가
 *   + 회차별 조정
 *   (묶음권이면 날짜별 합계 × (1 − 묶음권 할인율), 100원 내림)
 *   − 조기예매 또는 쿠폰     ← 둘 중 유리한 하나만
 *   = 티켓 가격
 * </pre>
 * 관객이 고르는 할인은 없다 — 조기예매는 몫을 받은 티켓에 저절로 붙고 쿠폰은 입력만 받으며, 둘 중 유리한 쪽을 시스템이 고른다. 자동으로 고르므로
 * 티켓마다 무엇이 적용됐는지({@link TicketPrice#discountKind})를 결제 내역에 보여 준다.
 *
 * <h2>고르는 법 — 티켓마다</h2>
 * 중복 불가는 <b>티켓 한 장 안에서</b> 조기예매와 쿠폰을 함께 빼지 않는다는 뜻이다. 비교도 티켓마다 한다 — 예매를 바꿀 때 새 티켓마다 "평소처럼
 * 유리한 하나 — 옮겨온 쿠폰과 지금 받을 수 있는 조기예매 중"을 고르고(docs/08 "새 티켓에 쿠폰을 적용하는 법"), 4매 중 1매만 바꾸면 한 예매 건에
 * 쿠폰 티켓과 조기예매 티켓이 함께 남는다(docs/08 "부분 변경"). 예매 건 전체로 하나만 고르면 한꺼번에 산 관객이 나눠 산 관객보다 비싸게 낸다.
 * <ol>
 *   <li>장마다 쿠폰가를 정한다. 정률은 그 장의 기본 가격에 적용해 100원 단위로 내린다. 정액은 예매 건에 붙는 성격이라 먼저 예매 건의 모든 티켓에
 *       기본 가격 비례로 나눠 장당 몫을 정하고({@link Money#splitProportionally} — 4매에 5,000원이면 장당 1,250원, 나누어떨어지지 않아도 합이 맞는다,
 *       예매 건 합계보다 크면 합계까지만), 쿠폰가 = 기본 가격 − 그 장의 몫이다.</li>
 *   <li>장마다 조기예매를 받은 가격(몫이 없으면 정가)과 쿠폰가를 비교해 싼 쪽을 고른다. 같으면 쿠폰을 쓰지 않는 쪽이다.</li>
 *   <li>조기예매를 고른 장에 나눠 둔 정액 쿠폰 몫은 쓰지 않는다 — 다른 장으로 옮기지 않는다. 예매를 바꿀 때 기존 티켓에 나눠 둔 금액을 그대로 옮기는
 *       것(docs/08)과 같은 방식이다.</li>
 * </ol>
 * <pre>
 * 50,000원 4매, 조기예매 몫 2장(35,000원)
 *   정률 20% 쿠폰   → 35,000 · 35,000 · 40,000 · 40,000 = 150,000원
 *   정액 5,000원 쿠폰 → 장당 1,250원 → 35,000 · 35,000 · 48,750 · 48,750 = 167,500원
 * </pre>
 * 확인 필요 (#48) — 정액 쿠폰 몫을 모든 티켓에 먼저 나누고 조기예매를 고른 장의 몫은 쓰지 않는 것, 같을 때 쿠폰을 쓰지 않는 쪽을 고르는 것(쿠폰은
 * 전체 사용 횟수 · 1인 1회가 걸린 권리라 남겨 둔다)은 문서에 없다.
 * <p>
 * 티켓 순서는 티켓 순번이다 — 정액 쿠폰을 나누고 남은 원을 줄 때 앞 순번이 먼저다. 이 결과는 결제 시점에 티켓마다 저장한다(#51, #73).
 *
 * @param tickets 티켓별 가격, 티켓 순번대로. 하나 이상
 */
public record BookingPrice(List<TicketPrice> tickets) {

    public BookingPrice {
        tickets = List.copyOf(tickets);
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("예매 건에 티켓이 없습니다");
        }
    }

    /**
     * 할인 전 티켓마다 조기예매 또는 쿠폰 중 유리한 하나를 적용한다.
     *
     * @param tickets 할인 전 티켓, 티켓 순번대로. 하나 이상
     * @param coupon  예매 건에 입력한 쿠폰의 할인({@link Coupon#discount}). 입력하지 않았으면 비어 있다. 쓸 수 있는 쿠폰인지(행사 · 기간 · 횟수 ·
     *                1인 1회)는 쿠폰이 먼저 본다({@link Coupon#requireUsableIn}, {@link Coupon#checkAvailableTo})
     */
    public static BookingPrice calculate(List<TicketBeforeDiscount> tickets, Optional<Discount> coupon) {
        List<TicketBeforeDiscount> orderedTickets = List.copyOf(tickets);
        Objects.requireNonNull(coupon, "coupon");
        if (orderedTickets.isEmpty()) {
            throw new IllegalArgumentException("예매 건에 티켓이 없습니다");
        }
        if (coupon.isEmpty()) {
            return new BookingPrice(orderedTickets.stream().map(BookingPrice::earlyBirdOrRegular).toList());
        }
        List<Money> couponPrices = couponPrices(orderedTickets, coupon.get());
        List<TicketPrice> prices = new ArrayList<>(orderedTickets.size());
        for (int index = 0; index < orderedTickets.size(); index++) {
            prices.add(favorableOne(orderedTickets.get(index), couponPrices.get(index)));
        }
        return new BookingPrice(prices);
    }

    /** 결제 금액 — 티켓 가격의 합계. */
    public Money total() {
        return Money.sum(tickets.stream().map(TicketPrice::price).toList());
    }

    /**
     * 한 장이라도 쿠폰을 골랐는지. 어느 장도 고르지 않았으면 쿠폰은 쓰지 않은 것이다 — 전체 사용 횟수 · 1인 1회를 차지하지 않는다. 골랐을 때만
     * 쿠폰을 쓴다({@link Coupon#use}).
     */
    public boolean usesCoupon() {
        return tickets.stream().anyMatch(ticket -> ticket.discountKind() == DiscountKind.COUPON);
    }

    /** 조기예매를 받은 가격(몫이 없으면 정가)과 쿠폰가 중 싼 쪽. 같으면 쿠폰을 쓰지 않는다. */
    private static TicketPrice favorableOne(TicketBeforeDiscount ticket, Money couponPrice) {
        TicketPrice withoutCoupon = earlyBirdOrRegular(ticket);
        if (couponPrice.isLessThan(withoutCoupon.price())) {
            return TicketPrice.discounted(ticket.basePrice(), DiscountKind.COUPON, couponPrice);
        }
        return withoutCoupon;
    }

    private static TicketPrice earlyBirdOrRegular(TicketBeforeDiscount ticket) {
        return ticket.earlyBird()
                .map(earlyBird -> TicketPrice.discounted(ticket.basePrice(), DiscountKind.EARLY_BIRD, earlyBird.applyTo(ticket.basePrice())))
                .orElseGet(() -> TicketPrice.undiscounted(ticket.basePrice()));
    }

    /** 장마다 쿠폰가 — 정률은 장마다 적용, 정액은 모든 티켓에 기본 가격 비례로 나눈 몫을 뺀다. */
    private static List<Money> couponPrices(List<TicketBeforeDiscount> tickets, Discount coupon) {
        List<Money> basePrices = tickets.stream().map(TicketBeforeDiscount::basePrice).toList();
        return switch (coupon) {
            case Discount.Rate rate -> basePrices.stream().map(rate::applyTo).toList();
            case Discount.FixedAmount fixedAmount -> {
                List<Money> shares = fixedAmount.amount().min(Money.sum(basePrices)).splitProportionally(basePrices);
                List<Money> prices = new ArrayList<>(basePrices.size());
                for (int index = 0; index < basePrices.size(); index++) {
                    prices.add(basePrices.get(index).minus(shares.get(index)));
                }
                yield List.copyOf(prices);
            }
        };
    }
}
