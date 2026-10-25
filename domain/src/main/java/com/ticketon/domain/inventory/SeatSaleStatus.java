package com.ticketon.domain.inventory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 좌석 판매 상태 — 한 회차에서 좌석 하나가 어떤 상태인가(docs/14 "재고는 회차에만 붙습니다" — 지정형은 좌석마다 팔렸나 / 선점 중인가 / 판매
 * 제외인가). 좌석은 도면에 하나, 판매 상태는 회차마다다 — 같은 "1층 12열 7번"이 3/14에는 팔렸고 3/15에는 비어 있을 수 있다(docs/02 "두 개의 축").
 * <p>
 * <b>바뀔 수 있는 길은 이 표 하나다</b>({@link #nextStatuses}). 길마다 어떤 조작이 그 길을 쓰는지와 막는 규칙은 {@link SeatStock}이 가진다.
 * <table>
 *   <caption>좌석 판매 상태가 바뀌는 길</caption>
 *   <tr><th>지금</th><th>다음</th><th>조작</th></tr>
 *   <tr><td>빈자리</td><td>선점 중</td><td>관객이 좌석을 누름(docs/06 "좌석 클릭 즉시")</td></tr>
 *   <tr><td>빈자리</td><td>판매 제외</td><td>주최자가 관계자석으로 뺌(docs/09 "관계자석 확보")</td></tr>
 *   <tr><td>빈자리</td><td>초대 발권</td><td>그 회차의 예매가 마감된 뒤 안 팔린 일반 좌석에 초대권(docs/09 "판매가 끝난 뒤에도 됩니다")</td></tr>
 *   <tr><td>선점 중</td><td>빈자리</td><td>선점이 풀림 — 시간 초과 · 관객이 놓음(docs/06 "표시와 만료")</td></tr>
 *   <tr><td>선점 중</td><td>팔림</td><td>결제 승인 → 티켓 발급(docs/06)</td></tr>
 *   <tr><td>팔림</td><td>빈자리</td><td>티켓 취소 — 좌석은 복구되어 다시 팔린다(docs/05 "소진되면 영구 마감", docs/06 "취소하면 좌석이 즉시 풀려")</td></tr>
 *   <tr><td>판매 제외</td><td>빈자리</td><td>주최자가 판매 제외를 풂</td></tr>
 *   <tr><td>판매 제외</td><td>초대 발권</td><td>관계자석에 초대권(docs/09)</td></tr>
 *   <tr><td>초대 발권</td><td>판매 제외 · 빈자리</td><td>초대권 회수 — 걸기 전의 상태로 돌아간다(docs/09 "회수하면 그만큼 풀립니다")</td></tr>
 * </table>
 * 표에 없는 길은 없다. 특히 <b>팔린 좌석은 판매 제외로 가지 못하고</b>, <b>초대권이 걸린 판매 제외 좌석은 판매 제외를 풀지 못한다</b>(docs/04 "예매가
 * 열린 뒤 수정 범위" — 판매 제외 좌석).
 * <p>
 * '매진'은 상태가 아니다 — 빈자리가 0인 순간의 표시다(docs/04 "'매진'은 상태가 아닙니다", {@link RemainingSeats}).
 */
public enum SeatSaleStatus {

    /** 빈자리 — 관객이 고를 수 있다. */
    AVAILABLE,

    /** 선점 중 — 관객이 눌러 잡았고 결제 전이다(선점 10분 + 결제 7분, docs/06 "타이머 두 개"). 선점 하나가 잡는다. */
    HELD,

    /** 팔림 — 결제가 끝나 티켓이 걸렸다. */
    SOLD,

    /** 판매 제외 — 주최자가 관계자석으로 빼 두었다. 관객 화면에서 회색이고 선택이 안 된다(docs/09). 회차 단위다(docs/02 "판매 제외는 회차 단위입니다"). */
    EXCLUDED,

    /**
     * 초대 발권 — 초대권 티켓이 걸렸다. 판매 제외 좌석에 걸리거나, 그 회차의 예매가 마감된 뒤 안 팔린 일반 좌석에 걸린다(docs/09 "판매가 끝난 뒤에도
     * 됩니다"). 판매 제외 좌석에 걸렸으면 판매 제외는 그대로이고 풀 수 없다.
     */
    INVITED;

    /** 이 상태에서 바뀔 수 있는 다음 상태 — 위 표 그대로. */
    public Set<SeatSaleStatus> nextStatuses() {
        return Collections.unmodifiableSet(switch (this) {
            case AVAILABLE -> EnumSet.of(HELD, EXCLUDED, INVITED);
            case HELD -> EnumSet.of(AVAILABLE, SOLD);
            case SOLD -> EnumSet.of(AVAILABLE);
            case EXCLUDED -> EnumSet.of(AVAILABLE, INVITED);
            case INVITED -> EnumSet.of(EXCLUDED, AVAILABLE);
        });
    }

    /** 이 상태에서 그 상태로 바뀔 수 있는지 — 위 표에 있는 길인지. */
    public boolean canChangeTo(SeatSaleStatus next) {
        return nextStatuses().contains(next);
    }

    /** 선점 · 티켓이 걸려 있는지 — 선점 중 · 팔림 · 초대 발권. 이런 좌석의 재고는 지울 수 없다(docs/04 "티켓이 걸린 좌석은 옮기거나 지울 수 없음"). */
    public boolean isTaken() {
        return this == HELD || this == SOLD || this == INVITED;
    }
}
