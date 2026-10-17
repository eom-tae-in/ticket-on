package com.ticketon.domain.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 티켓 N장에 조기예매 몫을 잡은 결과({@link EarlyBirds#claim}). 선점이 좌석 · 몫 · 가격을 한 묶음으로 잠글 때 쓴다(docs/06 "선점이 잠그는 것 셋").
 * <p>
 * 몫이 2장만 남았는데 4매를 사면 앞의 두 장만 몫을 받는다 — 장마다 금액이 다르다(35,000 · 35,000 · 50,000 · 50,000원, docs/05 "조기예매가
 * 섞이면"). {@link #ticketsBeforeDiscount}가 그 할인 전 티켓을 만들고, 가격 계산({@link BookingPrice#calculate})이 쿠폰과 견줘 장마다 유리한 하나를
 * 고른다.
 *
 * <h2>31번째 사람 — 조용히 가격만 올리지 않는다</h2>
 * 좌석은 잡히지만 조기예매 몫이 없으면 <b>정가로 진행할지 관객에게 물어야 한다</b>({@link #needsRegularPriceConfirmation}, docs/05 "31번째 사람").
 * <blockquote>
 * 조기예매가 방금 마감되었습니다. 정가 50,000원으로 진행하시겠습니까?<br>
 * [계속] [좌석 취소]
 * </blockquote>
 * 닿는 조기예매가 기간 안인데 몫이 모자랄 때 묻는다 — 일부만 모자라도 묻는다(4매 중 2매만 받음). 남은 수량을 관객에게 보여 주지 않으므로(임박
 * 단계만, docs/05 "임박 표시") 소진은 관객이 미리 알 수 없다. 기간 밖이면 애초에 닿는 조기예매가 없어 묻지 않는다 — 기간은 공개된 시각이다.
 * 확인 필요 (#49) — 이미 소진된 지 오래돼 관객 화면에도 정가만 보였을 때도 묻는다(관객이 무엇을 보고 골랐는지는 선점 #70이 안다).
 * <p>
 * 확인을 기다리는 동안에도 잡은 몫은 그대로다 — [좌석 취소]를 눌러 좌석이 풀려도 몫은 돌아오지 않는다(docs/05 "선점만 하고 만료 — 돌아오지
 * 않습니다").
 *
 * @param basePrice   티켓 한 장의 기본 가격(회차 가격 또는 묶음권 가격)
 * @param ticketCount 몫을 원한 티켓 수, 1 이상. 묶음권 1장은 1장이다
 * @param applicable  그 구역 · 상품에 닿고 기간 안인 조기예매가 하나라도 있었는지. 없었으면 잡은 몫도 없다
 * @param shares      조기예매마다 잡은 몫, 고른 순서대로. 합이 ticketCount를 넘지 않는다
 */
public record EarlyBirdAllocation(Money basePrice, int ticketCount, boolean applicable, List<EarlyBirdShare> shares) {

    public EarlyBirdAllocation {
        Objects.requireNonNull(basePrice, "basePrice");
        shares = List.copyOf(shares);
        if (ticketCount < 1) {
            throw new IllegalArgumentException("티켓 수는 1 이상이어야 합니다: " + ticketCount);
        }
        if (!applicable && !shares.isEmpty()) {
            throw new IllegalArgumentException("닿는 조기예매가 없는데 잡은 몫이 있습니다");
        }
        int granted = shares.stream().mapToInt(EarlyBirdShare::count).sum();
        if (granted > ticketCount) {
            throw new IllegalArgumentException("잡은 몫이 티켓 수보다 많습니다: " + granted + " > " + ticketCount);
        }
    }

    /** 닿는 조기예매가 없다 — 모두 정가이고 묻지 않는다. */
    static EarlyBirdAllocation notApplicable(Money basePrice, int ticketCount) {
        return new EarlyBirdAllocation(basePrice, ticketCount, false, List.of());
    }

    /** 잡은 몫의 수. */
    public int grantedCount() {
        return shares.stream().mapToInt(EarlyBirdShare::count).sum();
    }

    /** 닿는 조기예매가 있었는데 몫을 받지 못한 티켓 수. 닿는 조기예매가 없었으면 0이다. */
    public int shortfall() {
        return applicable ? ticketCount - grantedCount() : 0;
    }

    /** 정가로 진행할지 관객에게 물어야 하는지 — 몫을 받지 못한 티켓이 있다(31번째 사람). */
    public boolean needsRegularPriceConfirmation() {
        return shortfall() > 0;
    }

    /** 할인 전 티켓 — 몫을 받은 티켓이 앞에(고른 순서대로), 정가 티켓이 뒤에. */
    public List<TicketBeforeDiscount> ticketsBeforeDiscount() {
        List<TicketBeforeDiscount> tickets = new ArrayList<>(ticketCount);
        for (EarlyBirdShare share : shares) {
            for (int index = 0; index < share.count(); index++) {
                tickets.add(TicketBeforeDiscount.withEarlyBird(basePrice, share.discount()));
            }
        }
        while (tickets.size() < ticketCount) {
            tickets.add(TicketBeforeDiscount.regular(basePrice));
        }
        return List.copyOf(tickets);
    }
}
