/**
 * 가격 — 기본가·회차 조정, 묶음권 상품, 조기예매 몫, 쿠폰.
 * <p>
 * 순수 자바만 쓴다 (Spring·jOOQ 금지). 규칙은 이 패키지의 객체가 가진다.
 * 근거 문서: docs/05
 * <p>
 * 금액은 {@link com.ticketon.domain.pricing.Money}(원, 0 이상)로 다룬다. 끝자리 규칙 — 퍼센트를 적용하면 100원 단위 내림, 비율로 나눈 몫은 원 미만
 * 버림, 한 금액을 티켓마다 나누면 합을 정확히 맞춤 — 은 모두 Money 한곳에 있다.
 */
package com.ticketon.domain.pricing;
