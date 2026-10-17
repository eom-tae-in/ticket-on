package com.ticketon.domain.event;

import java.time.LocalTime;
import java.util.Objects;

/**
 * 일정의 시각 단위 — 분. 회차의 시작 · 종료({@link TimedSessionTime})와 운영 시간({@link OperatingHours})은 분까지만 받는다.
 * <p>
 * 확인 필요 (#41, #207 인계) — 문서는 시각을 "19:00", "10:00~18:00"처럼 분까지만 적고 초 단위 예시가 없다. 초 · 나노초가 붙은 값을 받으면 저장
 * 뒤 다시 읽은 값(DB의 TIME은 마이크로초까지)이 보낸 값과 달라질 수 있고, 관객 화면에 "19:00:30"처럼 보이거나 같은 날짜의 시작 시각 겹침 확인을
 * 초 차이로 빠져나간다. 그래서 조용히 자르지 않고 받지 않는다 — 입력 검증이 같은 규칙으로 막고({@link #isWholeMinute}), 다른 입구로 들어와도 값
 * 객체가 막는다.
 */
public final class MinutePrecision {

    private MinutePrecision() {
    }

    /** 초와 그 아래가 모두 0인 시각인지 — 일정이 받는 시각이다. */
    public static boolean isWholeMinute(LocalTime time) {
        Objects.requireNonNull(time, "time");
        return time.getSecond() == 0 && time.getNano() == 0;
    }

    /**
     * @param what 오류 설명에 넣을 칸 이름
     * @throws IllegalArgumentException 초나 그 아래가 0이 아니면
     */
    static LocalTime requireWholeMinute(LocalTime time, String what) {
        if (!isWholeMinute(time)) {
            throw new IllegalArgumentException(what + "은 분 단위여야 합니다: " + time);
        }
        return time;
    }
}
