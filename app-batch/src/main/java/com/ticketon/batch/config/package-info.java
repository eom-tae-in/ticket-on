/**
 * 배치 서버 공통 설정 — 시계(Clock), 추측 불가 값 생성기(TokenGenerator), 무작위 수 생성기(RandomGenerator) 등 여러 작업이 함께 쓰는 빈.
 * <p>
 * API 서버(com.ticketon.api.config)와 같은 빈을 둔다. 두 실행 모듈은 서로 의존하지 않으므로 각자 등록한다.
 */
package com.ticketon.batch.config;
