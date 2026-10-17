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
 * 취소는 의도 메서드({@link com.ticketon.domain.ticket.Ticket#cancel})로만 하고 시각과 사유를 남긴다. QR을 재발행하면 열쇠만 바뀌고 옛 열쇠를
 * 돌려준다({@link com.ticketon.domain.ticket.PreviousQrKey}). 공유 링크는 열쇠로 만들고, 무효가 된 링크는 이유를 알려 준다
 * ({@link com.ticketon.domain.ticket.ShareLinkInvalidReason}).
 * <p>
 * 그 티켓에만 붙는 권리도 티켓이 갖는다 — 공제 면제와 취소 시 배상({@link com.ticketon.domain.ticket.DeductionExemptionCause}), 묶음권의
 * 회차별 포기, 이미 돌려준 부분 금액(그날 몫 · 공연 지연 환급). 그날 몫은 산 때의 날짜별 가격으로 나누고, 환불 기준액은 결제 금액에서 돌려준 금액을
 * 뺀 값이다. 공제율 · 배상액은 환불 계산(#78 · #96)이 이 값들로 계산한다.
 * <p>
 * <b>"입장 완료"와 "만료"는 저장하지 않고 계산한다</b>(docs/14 "저장하는 것과 계산하는 것"). 관객 화면의 상태
 * ({@link com.ticketon.domain.ticket.TicketDisplayStatus} — 유효 · 입장 완료 · 공연 종료 · 취소됨)와 QR · 공유 버튼을 그릴지는 회차의 시각과
 * 입장 기록 여부를 받아 그때마다 낸다({@link com.ticketon.domain.ticket.TicketDisplay}). 티켓 표기({@link com.ticketon.domain.ticket.TicketLabel}
 * — "R석 12열 7번", "스탠딩A · 0091-2")도 글자로 복사하지 않고 지금 구역 · 좌석 이름으로 만든다.
 * <p>
 * 같은 층(②)의 가격(pricing)을 쓴다 — 금액은 {@link com.ticketon.domain.pricing.Money}, 장당 금액 · 적용 할인은
 * {@link com.ticketon.domain.pricing.TicketPrice}, 산 때의 회차 가격은 {@link com.ticketon.domain.pricing.SessionPrice}. 가격은 티켓을 부르지
 * 않는다. 아래 층(①)의 행사(event)도 쓴다 — 공유 링크의 행사 종료는 행사 상태로, 공연 종료는 회차의 시각
 * ({@link com.ticketon.domain.event.SessionTimes})으로 보고, 표기는 구역 이름 · 좌석 표기({@link com.ticketon.domain.event.SeatLabel})로 만든다.
 */
package com.ticketon.domain.ticket;
