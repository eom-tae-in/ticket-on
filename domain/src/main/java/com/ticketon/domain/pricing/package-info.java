/**
 * 가격 — 기본가·회차 조정, 묶음권 상품, 조기예매 몫, 쿠폰.
 * <p>
 * 순수 자바만 쓴다 (Spring·jOOQ 금지). 규칙은 이 패키지의 객체가 가진다.
 * 근거 문서: docs/05
 * <p>
 * 금액은 {@link com.ticketon.domain.pricing.Money}(원, 0 이상)로 다룬다. 끝자리 규칙 — 퍼센트를 적용하면 100원 단위 내림, 비율로 나눈 몫은 원 미만
 * 버림, 한 금액을 티켓마다 나누면 합을 정확히 맞춤 — 은 모두 Money 한곳에 있다.
 * <p>
 * 가격의 층위(docs/05)를 그대로 따른다.
 * <ol>
 *   <li>회차 가격({@link com.ticketon.domain.pricing.SessionPrice}) = 구역 기본가 + 회차 조정. 행사(event)의 구역 기본가 · 회차 가격 조정을 받아
 *       계산한다 — 가격(②)이 행사(①)를 쓰고, 행사는 가격을 부르지 않는다.</li>
 *   <li>묶음권이면 날짜마다 고른 회차의 가격({@link com.ticketon.domain.pricing.BundleDayPrices}) 합계 × (1 − 할인율), 100원 내림.
 *       하루가 취소 · 포기되면 같은 날짜별 가격으로 그날 몫을 낸다.</li>
 *   <li>그 위에 조기예매 또는 쿠폰 중 유리한 하나({@link com.ticketon.domain.pricing.BookingPrice}) — 정액 쿠폰은 티켓마다 비례로 나눠 티켓 한 장의
 *       가격({@link com.ticketon.domain.pricing.TicketPrice})을 하나의 값으로 만든다.</li>
 * </ol>
 */
package com.ticketon.domain.pricing;
