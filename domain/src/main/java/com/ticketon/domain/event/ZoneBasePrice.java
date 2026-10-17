package com.ticketon.domain.event;

/**
 * 구역 기본가(원) — 가격이 붙는 곳(docs/05 "가격의 층위"). <b>가격은 구역에 붙는다.</b> 블록이 아니다(docs/02 "구역과 블록은 다른 축입니다").
 * <pre>
 * 구역 기본가               ← 가격이 붙는 곳
 *   + 회차별 조정           ← 평일/주말, 프리뷰
 *   − 조기예매 또는 쿠폰     ← 둘 중 유리한 하나만
 *   = 티켓 가격
 * </pre>
 * 기본가는 구역에 하나이고 회차에는 조정값만 둔다({@link SessionPriceAdjustment}). 도면이 행사에 하나인 까닭이 이것이다 — 회차마다 도면이 생기면
 * 기본가도 회차 수만큼 생긴다(docs/02 "두 개의 축").
 * <p>
 * 1원 이상이어야 한다. 돈을 받지 않는 표는 초대권이다(docs/03 "초대권은 무료 발권"). 회차 조정을 적용한 가격의 계산은 가격 도메인(pricing)의
 * 회차 가격(SessionPrice, #48)이 한다. 판매 중 가격 변경을 신규 예매부터 적용하는 일은 저장 · 선점(#51, #70)이 맡는다.
 *
 * @param won 기본가(원), 1 이상
 */
public record ZoneBasePrice(long won) {

    public ZoneBasePrice {
        if (won < 1) {
            throw new IllegalArgumentException("구역 기본가는 1원 이상이어야 합니다: " + won);
        }
    }

    /**
     * 기본가를 만든다.
     *
     * @throws IllegalArgumentException 1원보다 적으면
     */
    public static ZoneBasePrice of(long won) {
        return new ZoneBasePrice(won);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(long won) {
        return won >= 1;
    }
}
