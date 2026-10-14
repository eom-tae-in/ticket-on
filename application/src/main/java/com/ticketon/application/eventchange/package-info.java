/**
 * 행사 변경·취소 — 출연자 변경·연기·장소 변경·회차/행사 취소·공연 중단 → 환불·배상·알림을 한곳에서 지휘 — 유스케이스.
 * <p>
 * port/in: 들어오는 포트(XxxUseCase)와 입출력(XxxCommand / XxxQuery / XxxResult).
 * port/out: 나가는 포트(XxxRepository, XxxPort) — 바깥 어댑터가 구현한다.
 * service: 들어오는 포트를 구현하고 나가는 포트를 부른다. 규칙 계산은 도메인에 맡긴다.
 * 근거 문서: docs/04, docs/08
 */
package com.ticketon.application.eventchange;
