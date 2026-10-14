/**
 * 계정 — 로그인(관객·운영진·관리자·검표), 가입·탈퇴, 비밀번호 찾기, 관객 제재 — 유스케이스.
 * <p>
 * port/in: 들어오는 포트(XxxUseCase)와 입출력(XxxCommand / XxxQuery / XxxResult).
 * port/out: 나가는 포트(XxxRepository, XxxPort) — 바깥 어댑터가 구현한다.
 * service: 들어오는 포트를 구현하고 나가는 포트를 부른다. 규칙 계산은 도메인에 맡긴다.
 * 근거 문서: docs/01
 */
package com.ticketon.application.account;
