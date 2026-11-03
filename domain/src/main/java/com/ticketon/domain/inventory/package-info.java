/**
 * 재고 — 회차별 좌석 판매 상태·잔여, 판매 제외, 관계자 확보분(docs/02 "두 개의 축", docs/14 "2층 — 재고").
 * <p>
 * <b>도면과 재고는 다른 층이다.</b> 도면(블록 · 좌석 · 구역)은 행사에 하나이고(① event), 재고는 회차에만 붙는다 — 좌석은 하나, 판매 상태는
 * 회차마다다. 관객 선점 · 초대권 발권 · 주최자의 판매 제외가 모두 같은 좌석을 잡으므로 재고를 예매 밖에 따로 둔다(docs/16 "재고를 따로 둔 이유").
 * <ul>
 *   <li><b>지정형</b> — 회차 × 좌석 판매 상태({@link com.ticketon.domain.inventory.SeatStock}). 상태 다섯 가지와 바뀌는 길은
 *       {@link com.ticketon.domain.inventory.SeatSaleStatus}의 표 하나에 있다.</li>
 *   <li><b>수량형</b> — 회차 × 구역 정원 · 판매 수 · 선점 수 · 관계자 확보 수 · 초대 발권 수({@link com.ticketon.domain.inventory.QuantityZoneStock}).
 *       수량형의 판매 제외는 "관계자용 10석 확보"다(docs/09).</li>
 *   <li><b>회차 재고</b>({@link com.ticketon.domain.inventory.SessionInventory}) — 한 회차의 좌석 재고와 수량형 재고. 좌석 여럿을 한꺼번에
 *       잡고, 도면과 함께 구역별 잔여를 센다.</li>
 *   <li><b>행사 재고</b>({@link com.ticketon.domain.inventory.EventInventory}) — 한 행사의 회차 재고 전부. 판매 제외 전 회차 일괄 적용과,
 *       회차 · 좌석 · 수량형 구역이 생기고 지워질 때 회차마다 재고를 만들고 빼는 일을 한다.</li>
 *   <li><b>묶음권 판매 가능 여부</b>({@link com.ticketon.domain.inventory.BundleAvailability}) — 한 날짜라도 들어갈 회차가 없으면 판매가
 *       멈춘다(docs/05 "재고가 엮입니다").</li>
 * </ul>
 * '매진'은 상태가 아니라 잔여가 0인 순간의 표시라 저장하지 않는다(docs/04 "'매진'은 상태가 아닙니다"). 매진 · 매진 임박은
 * {@link com.ticketon.domain.inventory.RemainingSeats}가 계산한다.
 * <p>
 * 순수 자바만 쓴다 (Spring·jOOQ 금지). 규칙은 이 패키지의 객체가 가진다. 층 ②라 event(①)의 회차 상태 · 도면을 쓸 수 있고, event는 이 패키지를
 * 부르지 않는다 — 도면 · 일정이 바뀌면 event가 알리고(#41 · #42 · #43) 재고가 듣는다(#54). 같은 층의 pricing과는 서로 부르지 않는다.
 * <p>
 * 근거 문서: docs/02, docs/04, docs/05, docs/09, docs/13, docs/14, docs/16
 */
package com.ticketon.domain.inventory;
