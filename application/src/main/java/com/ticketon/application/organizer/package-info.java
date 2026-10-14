/**
 * 주최자 — 단체, 소유자·운영자 소속, 심사 신청, 공개 정보(로고), 사업자·정산 계좌, 주최자 정지 — 유스케이스.
 * <p>
 * port/in: 들어오는 포트(XxxUseCase)와 입출력(XxxCommand / XxxQuery / XxxResult).
 * port/out: 나가는 포트(XxxRepository, XxxPort) — 바깥 어댑터가 구현한다.
 * service: 들어오는 포트를 구현하고 나가는 포트를 부른다. 규칙 계산은 도메인에 맡긴다.
 * 근거 문서: docs/01, docs/11
 */
package com.ticketon.application.organizer;
