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
 */
package com.ticketon.domain.event;
