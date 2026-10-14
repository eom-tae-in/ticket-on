/**
 * 행사 — 행사 정보·위저드, 도면(블록·좌석·구역·입구), 날짜·회차·출연, 변경 이력, 상태 자동 전환, 공개 목록·검색 — 유스케이스.
 * <p>
 * port/in: 들어오는 포트(XxxUseCase)와 입출력(XxxCommand / XxxQuery / XxxResult).
 * port/out: 나가는 포트(XxxRepository, XxxPort) — 바깥 어댑터가 구현한다.
 * service: 들어오는 포트를 구현하고 나가는 포트를 부른다. 규칙 계산은 도메인에 맡긴다.
 * 근거 문서: docs/02, docs/03, docs/04, docs/13
 */
package com.ticketon.application.event;
