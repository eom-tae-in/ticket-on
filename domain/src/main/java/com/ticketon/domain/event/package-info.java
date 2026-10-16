/**
 * 행사 — 행사 정보·위저드, 도면(블록·좌석·구역·입구), 날짜·회차·출연, 변경 이력, 상태 자동 전환, 공개 목록·검색.
 * <p>
 * 순수 자바만 쓴다 (Spring·jOOQ 금지). 규칙은 이 패키지의 객체가 가진다.
 * 근거 문서: docs/02, docs/03, docs/04, docs/06(매수 제한 · 예매 오픈 · 예매 종료), docs/08(취소 규정), docs/13
 * <p>
 * 행사({@link com.ticketon.domain.event.Event})는 위저드 1단계의 기본 정보({@link com.ticketon.domain.event.EventBasicInformation}),
 * 포스터, 공개 수준({@link com.ticketon.domain.event.EventVisibilitySetting}), 추측할 수 없는 공개 주소
 * ({@link com.ticketon.domain.event.EventPublicAddress}), 위저드 5단계의 예매 설정({@link com.ticketon.domain.event.BookingSettings})과
 * 취소 규정({@link com.ticketon.domain.event.CancellationPolicy})을 갖는다. 카테고리는 관리자 승격으로 늘어나는 데이터라 enum이 아니라
 * {@link com.ticketon.domain.event.EventCategoryCatalog 목록}으로 다룬다.
 * <p>
 * 날짜 · 회차는 행사의 일정({@link com.ticketon.domain.event.EventSchedule})이 갖는다 — 회차 방식(회차형 / 종일형), 종일형의 기본 운영
 * 시간, 날짜({@link com.ticketon.domain.event.EventDate})와 그 아래 회차({@link com.ticketon.domain.event.EventSession}). 회차의 예매
 * 종료 · 검표 시작 · 회차 상태는 저장하지 않고 회차의 시각({@link com.ticketon.domain.event.SessionTimes})으로 계산한다.
 * <p>
 * 도면은 회차와 무관하게 행사에 하나다({@link com.ticketon.domain.event.FloorPlan}) — 가격을 매기는 구역({@link com.ticketon.domain.event.Zone},
 * 지정형 / 수량형), 번호를 매기는 블록({@link com.ticketon.domain.event.SeatBlock})과 그 안의 좌석({@link com.ticketon.domain.event.Seat}), 무대
 * 위치. 좌석의 정체성은 좌석 id이고 도면을 고쳐도 좌석 번호는 다시 매기지 않는다. 좌석 표기("12열 7번" / "1층 12열 7번")는 티켓에 복사하지 않고
 * 그릴 때마다 만든다({@link com.ticketon.domain.event.SeatLabel}). 회차마다 다른 판매 상태 · 판매 제외는 재고(inventory)가 갖는다. 입구 목록도
 * 도면에 속하지만 판매가 끝난 뒤에도 고칠 수 있어 따로 둔다({@link com.ticketon.domain.event.EventEntrances}).
 */
package com.ticketon.domain.event;
