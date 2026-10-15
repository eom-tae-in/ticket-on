/**
 * 관리자 — 심사, 신고, 정지 단계 실행, 예매 찾기·대신 취소, 로고 초기화, 그리고 이 모든 조작의 기록.
 * <p>
 * 관리자의 모든 조작에 "누가 언제 무엇을"을 남긴다({@link com.ticketon.domain.admin.AdminOperationRecord}, docs/01 "관리자 2등급").
 * 관리자 계정과 등급별 권한은 계정 패키지(account)가 가진다 — 로그인이 계정 패키지의 일이기 때문이다(docs/16).
 * <p>
 * 순수 자바만 쓴다 (Spring·jOOQ 금지). 규칙은 이 패키지의 객체가 가진다.
 * 근거 문서: docs/01, docs/13
 */
package com.ticketon.domain.admin;
