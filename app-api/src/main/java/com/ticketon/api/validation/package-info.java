/**
 * 여러 도메인의 Request가 함께 쓰는 입력 검증 어노테이션 — 예: 꼭 적어야 하는 글 칸({@link com.ticketon.api.validation.FilledText}).
 * <p>
 * 한 도메인의 값 객체 규칙을 그대로 옮긴 검증(이메일 형식 · 닉네임 길이 등)은 그 도메인의 {@code com.ticketon.api.<도메인>}에 둔다.
 */
package com.ticketon.api.validation;
