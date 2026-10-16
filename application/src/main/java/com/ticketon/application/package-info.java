/**
 * 유스케이스 — 도메인별 패키지마다 port/in · port/out · service.
 *
 * <h2>현재 시각은 서비스가 만들어 도메인에 넘긴다</h2>
 * <ul>
 *   <li>서비스는 {@link java.time.Clock}을 생성자로 주입받는다. 빈은 app-api·app-batch가 한국 시각(Asia/Seoul)으로 등록하고,
 *       DB({@code timestamptz})와 같은 1마이크로초 단위로 끊는다(#207).</li>
 *   <li>유스케이스 하나에서 {@code Instant now = clock.instant()}를 한 번 만들고, 도메인 메서드에 인자로 넘긴다
 *       (예: {@code ticket.cancel(reason, now)}). 한국 날짜가 필요하면 {@code KoreanTime.toDate(now)}로 바꾼다.</li>
 *   <li>{@code Instant.now()}, {@code LocalDate.now()}처럼 시스템 시계를 직접 읽지 않는다. 도메인은 시계를 아예 모른다
 *       (.claude/rules/domain.md "현재 시각은 인자로 받는다").</li>
 * </ul>
 *
 * <h2>추측 불가 값도 서비스가 만들어 넘긴다</h2>
 * 비공개 행사 주소·예매번호·QR 열쇠·공유 링크·초대권 링크 값은 common의 {@code TokenGenerator}를 주입받아 만들고
 * 도메인 메서드에 인자로 넘긴다. 도메인은 생성기를 모른다.
 *
 * <h2>테스트</h2>
 * 서비스 단위 테스트는 common 테스트 픽스처의 {@code MutableClock}(고정·이동 가능한 시계)과
 * {@code FixedTokenGenerator}(고정 생성기)를 생성자에 넣는다. Spring 통합 테스트는
 * {@code @TestBean(name = "clock")}으로 Clock 빈을 {@code MutableClock}으로 바꾼다. 이때 칸 타입은 {@code MutableClock}이 아니라
 * {@link java.time.Clock}으로 두고, 같은 이름의 static 메서드가 static 상수 {@code MutableClock}을 돌려주게 한다.
 * 칸 타입이 {@code MutableClock}이면 {@code @SpringBootTest}에서 같은 이름의 빈을 새로 등록하려다 막혀 컨텍스트가 뜨지 않는다
 * (app-api의 ClockOverrideTest).
 */
package com.ticketon.application;
