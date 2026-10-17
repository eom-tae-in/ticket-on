package com.ticketon.domain.event;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 회차형 회차의 시간 — 시작 시각(필수)과 종료 시각(선택, 러닝타임)(docs/02 "회차의 시간 정보"). 회차 날짜의 한국 시각이다.
 * <blockquote>3/14 · 19:00 시작 ~ 21:00 종료</blockquote>
 * <b>종료 시각은 비워 둘 수 있지만 쓰이는 곳이 있다</b> — 행사 종료 판정(docs/04 "전환 트리거")과 공연 중단 판정(docs/08 "공연 중단")이 회차의
 * 시작~종료로 본다. 그래서 그 회차의 시간 수정이 잠기는 시점(회차 시작 1주일 전)부터 비어 있으면 입력을 요구한다
 * ({@link SessionTimes#needsEndTimeInput}).
 * <p>
 * 회차는 "입장 가능한 창구"이지 "퇴장 시각"이 아니다 — 14-16시 회차로 15시 50분에 들어가 18시까지 봐도 된다(docs/02). 종료 시각은 검표가
 * 열려 있는 끝이지 관객이 나가야 하는 시각이 아니다({@link SessionTimes#checkinClosesAt}).
 * <p>
 * 종료 시각은 시작보다 늦어야 한다. 자정을 넘겨 끝나는 회차(22:00 시작 ~ 00:30 종료)는 문서에 없어 받지 않는다(확인 필요 #36, #135) — 받으려면
 * 종료가 다음 날임을 어떻게 적는지부터 정해야 한다. 그래서 지금은 22:00에 시작해 다음 날 끝나는 회차의 종료 시각을 넣을 수 없다.
 * <p>
 * 시각은 분 단위다 — 초 · 나노초가 붙은 시각은 받지 않는다({@link MinutePrecision}, 확인 필요 #41).
 *
 * @param startTime 시작 시각
 * @param endTime   종료 시각. 비워 두면 null
 */
public record TimedSessionTime(LocalTime startTime, LocalTime endTime) {

    public TimedSessionTime {
        MinutePrecision.requireWholeMinute(Objects.requireNonNull(startTime, "startTime"), "시작 시각");
        if (endTime != null) {
            MinutePrecision.requireWholeMinute(endTime, "종료 시각");
        }
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 늦어야 합니다: " + startTime + " ~ " + endTime);
        }
    }

    /** 종료 시각을 비운 시간. */
    public static TimedSessionTime startingAt(LocalTime startTime) {
        return new TimedSessionTime(startTime, null);
    }

    /**
     * @param endTime 종료 시각. 비워 두면 null
     * @throws IllegalArgumentException 종료 시각이 시작 시각보다 늦지 않으면
     */
    public static TimedSessionTime of(LocalTime startTime, LocalTime endTime) {
        return new TimedSessionTime(startTime, endTime);
    }

    /** {@link #of}로 만들 수 있는 값인지 — 분 단위이고 종료가 시작보다 늦다. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || !MinutePrecision.isWholeMinute(startTime)) {
            return false;
        }
        return endTime == null || (MinutePrecision.isWholeMinute(endTime) && endTime.isAfter(startTime));
    }

    /** 비워 두었으면 비어 있다. */
    public Optional<LocalTime> end() {
        return Optional.ofNullable(endTime);
    }

    /** 시작은 그대로 두고 종료 시각을 채운 시간. */
    public TimedSessionTime withEndTime(LocalTime newEndTime) {
        Objects.requireNonNull(newEndTime, "newEndTime");
        return new TimedSessionTime(startTime, newEndTime);
    }
}
