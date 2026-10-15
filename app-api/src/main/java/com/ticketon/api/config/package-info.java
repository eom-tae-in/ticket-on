/**
 * API 서버 공통 설정 — 시계(Clock), 추측 불가 값 생성기(TokenGenerator), 무작위 수 생성기(RandomGenerator), 영업일 계산
 * (BusinessDayCalculator), 응답과 떼어 실행하기(DetachedExecution) 등 여러 도메인이 함께 쓰는 빈.
 * <p>
 * 도메인별 컨트롤러는 여기가 아니라 {@code com.ticketon.api.<도메인>}에 둔다.
 */
package com.ticketon.api.config;
