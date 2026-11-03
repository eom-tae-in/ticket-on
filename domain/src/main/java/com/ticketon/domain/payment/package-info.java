/**
 * 결제 — 결제·환불 내역.
 * <p>
 * 순수 자바만 쓴다 (Spring·jOOQ 금지). 규칙은 이 패키지의 객체가 가진다.
 * 근거 문서: docs/08 "결제·환불 내역", docs/14 "예매 건은 결제 단위입니다", "변경은 티켓을 바꿔치웁니다", docs/06
 * <p>
 * 예매 건의 결제·환불 내역({@link com.ticketon.domain.payment.PaymentHistory})은 돈이 움직일 때마다 한 줄
 * ({@link com.ticketon.domain.payment.PaymentHistoryEntry})을 쌓는다. 줄은 종류(결제 / 환불 / 배상 / 지연 환급,
 * {@link com.ticketon.domain.payment.PaymentHistoryEntryKind}) · 까닭({@link com.ticketon.domain.payment.PaymentHistoryCause}) · 금액 ·
 * 시각 · 대상 티켓들을 갖는다. 실 결제액은 결제 합 − 환불 합이고, 지연 환급은 환불처럼 빼고 배상은 빼지 않는다.
 * <p>
 * 줄의 설명("2매 취소", "R석 12열 10번 변경", "5/4 그날 몫", 적용 할인)은 글자로 저장하지 않고, 그릴 때마다 대상 티켓의 지금 표기 · 받은 할인 ·
 * 회차의 지금 날짜({@link com.ticketon.domain.payment.PaymentHistoryDescriptionSource})로 까닭이 만든다 — 티켓 표기를 글자로 복사하지 않는
 * 원칙과 같다(docs/99 B "티켓에 좌석 표기를 글자로 복사").
 * <p>
 * <b>변경은 환불 한 줄 + 결제 한 줄</b>이다. 티켓에 "변경됨" 상태나 "이전 티켓" 사슬을 두지 않고, 예매 건과 티켓을 잇는 이력은 이 내역이 갖는다
 * (docs/99 B "티켓 변경 사슬"). 화면 이름은 "결제·환불 내역", 개별 항목은 "결제 상세"이고 <b>"영수증"이라 부르지 않는다</b> — 세금계산서 ·
 * 현금영수증은 만들지 않는다(docs/99 B "세금계산서·현금영수증·자동 송금").
 * <p>
 * 같은 층(②)의 티켓(ticket)과 가격(pricing)을 쓴다 — 대상 티켓의 예매 건 · 취소 사유 · 결제 금액 · 환불 기준액 · 그날 몫은
 * {@link com.ticketon.domain.ticket.Ticket}이, 금액은 {@link com.ticketon.domain.pricing.Money}가 갖는다. 티켓과 가격은 이 패키지를 부르지
 * 않는다. 예매 건(booking, ③)은 id로만 가리킨다.
 */
package com.ticketon.domain.payment;
