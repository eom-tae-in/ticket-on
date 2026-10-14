/**
 * 검표 — 검표 계정의 행사·입구 배정, 스캔 판정, 수동 검색, 입장 기록(30일 후 삭제), 티켓 없는 입장 — 유스케이스.
 * <p>
 * port/in: 들어오는 포트(XxxUseCase)와 입출력(XxxCommand / XxxQuery / XxxResult).
 * port/out: 나가는 포트(XxxRepository, XxxPort) — 바깥 어댑터가 구현한다.
 * service: 들어오는 포트를 구현하고 나가는 포트를 부른다. 규칙 계산은 도메인에 맡긴다.
 * 근거 문서: docs/10
 */
package com.ticketon.application.checkin;
