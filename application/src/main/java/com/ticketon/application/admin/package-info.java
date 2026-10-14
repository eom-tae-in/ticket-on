/**
 * 관리자 — 심사, 신고, 정지 단계 실행, 예매 찾기·대신 취소, 로고 초기화 — 유스케이스.
 * <p>
 * port/in: 들어오는 포트(XxxUseCase)와 입출력(XxxCommand / XxxQuery / XxxResult).
 * port/out: 나가는 포트(XxxRepository, XxxPort) — 바깥 어댑터가 구현한다.
 * service: 들어오는 포트를 구현하고 나가는 포트를 부른다. 규칙 계산은 도메인에 맡긴다.
 * 근거 문서: docs/01, docs/13
 */
package com.ticketon.application.admin;
