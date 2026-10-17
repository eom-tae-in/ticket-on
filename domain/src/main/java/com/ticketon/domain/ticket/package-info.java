/**
 * 티켓 — 티켓 번호, QR 열쇠·재발행, 공유 링크·무효 사유.
 * <p>
 * 순수 자바만 쓴다 (Spring·jOOQ 금지). 규칙은 이 패키지의 객체가 가진다.
 * 근거 문서: docs/07, docs/14 "3층 — 티켓", docs/08(공제 면제 · 회차별 포기 · 그날 몫 · 기준 금액)
 * <p>
 * 티켓({@link com.ticketon.domain.ticket.Ticket})은 예매 건 또는 초대권({@link com.ticketon.domain.ticket.TicketParent})에 속하고, 회차 1개
 * 이상({@link com.ticketon.domain.ticket.TicketSession}) · 구역 · 좌석 0~1개를 가리킨다. 결제 금액 · 적용 할인 · 공제 면제 · 취소 시 배상 ·
 * 회차별 포기 · 취소 시각과 사유 · QR 열쇠({@link com.ticketon.domain.ticket.QrKey})는 직접 갖는다. 티켓 번호
 * ({@link com.ticketon.domain.ticket.TicketNumber})는 부모 번호 + 순번이다.
 * <p>
 * 같은 층(②)의 가격(pricing)을 쓴다 — 금액은 {@link com.ticketon.domain.pricing.Money}, 장당 금액 · 적용 할인은
 * {@link com.ticketon.domain.pricing.TicketPrice}, 산 때의 회차 가격은 {@link com.ticketon.domain.pricing.SessionPrice}. 가격은 티켓을 부르지
 * 않는다.
 */
package com.ticketon.domain.ticket;
