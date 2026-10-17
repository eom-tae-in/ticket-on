package com.ticketon.domain.pricing;

/**
 * 예매 건의 티켓이 취소되는 경우 — 쿠폰을 돌려줄지가 여기서 갈린다(docs/05 "취소하면 쿠폰은 돌아옵니다", docs/08 "취소 직후").
 * <table>
 *   <caption>취소와 쿠폰 (docs/08)</caption>
 *   <tr><th>경우</th><th>쿠폰</th><th>까닭</th></tr>
 *   <tr><td>전체 취소</td><td><b>돌려줌</b> (유효 기간은 그대로)</td><td>쿠폰은 특정 개인에게 준 권리라, 취소했다고 잃으면 억울하다</td></tr>
 *   <tr><td>부분 취소</td><td>돌려주지 않음</td><td>남은 티켓이 아직 그 쿠폰을 쓰고 있다 — 4매에 쓴 쿠폰을 2매만 취소했으면 여전히 사용 중</td></tr>
 *   <tr><td>변경</td><td>돌려주지 않고 따라감</td><td>예매가 이어진다. 새 티켓에 옮겨 적용하는 법은 변경 계산(#80)이 한다(docs/08 "새 티켓에 쿠폰을 적용하는 법")</td></tr>
 * </table>
 * 조기예매 몫은 어느 경우에도 돌아오지 않는다 — 선착순으로 다투는 자원이라서다({@link EarlyBird}).
 */
public enum TicketCancellationKind {

    /** 전체 취소 — 이 취소로 그 예매 건에 살아 있는 티켓이 남지 않는다. 관객 전체 취소, 회차 · 행사 취소, 관리자 대신 취소 모두. */
    WHOLE_BOOKING,

    /** 부분 취소 — 취소한 뒤에도 그 예매 건에 살아 있는 티켓이 남는다. */
    PARTIAL,

    /** 변경 — 기존 티켓을 취소하고 같은 예매 건에 새 티켓을 발급한다(docs/14 "변경은 티켓을 바꿔치웁니다"). 몇 장을 바꾸든 이 경우다. */
    CHANGE;

    /**
     * 변경이 아닌 취소가 전체 취소인지 부분 취소인지 — 취소한 뒤 살아 있는 티켓이 남지 않으면 전체 취소다. 확인 필요 (#49) — 부분 취소가 이어져
     * 마지막 티켓까지 취소되면 그 마지막 취소를 전체 취소로 본다(쿠폰을 쓰는 남은 티켓이 없다). 4매 중 2매를 먼저 취소하고 남은 2매를 취소하면 그때
     * 쿠폰이 돌아온다.
     *
     * @param liveTicketsBefore 취소 전 그 예매 건의 살아 있는(취소되지 않은) 티켓 수, 1 이상
     * @param cancellingTickets 이번에 취소하는 티켓 수, 1 이상 liveTicketsBefore 이하
     */
    public static TicketCancellationKind ofCancellation(int liveTicketsBefore, int cancellingTickets) {
        if (cancellingTickets < 1 || cancellingTickets > liveTicketsBefore) {
            throw new IllegalArgumentException("취소할 티켓 수가 살아 있는 티켓 수 범위 밖입니다: " + cancellingTickets + " / " + liveTicketsBefore);
        }
        return cancellingTickets == liveTicketsBefore ? WHOLE_BOOKING : PARTIAL;
    }

    /** 쿠폰을 돌려주는지 — 전체 취소일 때만. */
    public boolean returnsCoupon() {
        return this == WHOLE_BOOKING;
    }
}
