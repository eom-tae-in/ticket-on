/**
 * 현황·통계 — 예매 현황·오늘 판매·연령대, 입장 현황 (읽기 전용, 도메인을 거치지 않음) — 유스케이스.
 * <p>
 * port/in: 들어오는 포트(XxxUseCase)와 입출력(XxxCommand / XxxQuery / XxxResult).
 * port/out: 나가는 포트(XxxRepository, XxxPort) — 바깥 어댑터가 구현한다.
 * service: 들어오는 포트를 구현하고 나가는 포트를 부른다. 규칙 계산은 도메인에 맡긴다.
 * 근거 문서: docs/11
 */
package com.ticketon.application.stats;
