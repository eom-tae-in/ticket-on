package com.ticketon.api.event;

import java.time.LocalTime;

/** 회차형 회차의 시작 · 종료 시각을 받는 요청 — 둘의 차례를 함께 본다({@link SessionTimeOrder}). */
interface SessionTimeFields {

    LocalTime startTime();

    LocalTime endTime();
}
