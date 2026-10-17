package com.ticketon.domain.pricing;

import java.util.Objects;
import java.util.Optional;

/**
 * 할인 전 티켓 — 기본 가격과, 이 티켓이 조기예매 몫을 받았으면 그 조기예매 할인.
 * <p>
 * 기본 가격은 일반 티켓이면 회차 가격({@link SessionPrice}), 묶음권이면 묶음권 가격({@link BundleDayPrices#bundlePrice})이다. 조기예매 몫은
 * 좌석을 선점하는 순간 티켓마다 잡히므로(docs/05 "조기예매 — 재고") 몫이 2장만 남았는데 4매를 사면 두 장에만 조기예매가 붙는다. 어느 티켓이 몫을
 * 받았는지는 선점(#70)이, 조기예매의 할인 값은 조기예매 규칙(#49)이 정한다.
 *
 * @param basePrice 기본 가격, 1원 이상
 * @param earlyBird 이 티켓이 받은 조기예매 할인. 몫을 받지 못했으면 비어 있다
 */
public record TicketBeforeDiscount(Money basePrice, Optional<Discount> earlyBird) {

    public TicketBeforeDiscount {
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(earlyBird, "earlyBird");
        if (basePrice.isZero()) {
            throw new IllegalArgumentException("기본 가격은 1원 이상이어야 합니다 — 돈을 받지 않는 표는 초대권입니다");
        }
    }

    /** 조기예매 몫을 받지 못한 티켓 — 정가. */
    public static TicketBeforeDiscount regular(Money basePrice) {
        return new TicketBeforeDiscount(basePrice, Optional.empty());
    }

    /** 조기예매 몫을 받은 티켓. */
    public static TicketBeforeDiscount withEarlyBird(Money basePrice, Discount earlyBird) {
        return new TicketBeforeDiscount(basePrice, Optional.of(earlyBird));
    }
}
