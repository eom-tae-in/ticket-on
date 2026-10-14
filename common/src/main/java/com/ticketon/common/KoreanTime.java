package com.ticketon.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 서비스 기준 시각 (한국 시각, Asia/Seoul).
 * <p>
 * 시각은 {@link Instant}로 저장·전달하고, 날짜 규칙(관람일, '낮 12시', '전날 자정')이 필요할 때만
 * 이 클래스로 한국 날짜·시각으로 바꾼다. 서버의 기본 시간대에 기대지 않는다.
 */
public final class KoreanTime {

    /** 문서의 모든 날짜·시각('낮 12시', '전날 자정' 등)은 이 시간대 기준이다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KoreanTime() {
    }

    /** 그 순간의 한국 날짜. */
    public static LocalDate toDate(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    /** 그 순간의 한국 날짜·시각. */
    public static LocalDateTime toDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    /** 한국 날짜·시각을 순간으로 바꾼다. 예: 전날 리마인더 = {@code toInstant(sessionDate.minusDays(1).atTime(12, 0))}. */
    public static Instant toInstant(LocalDateTime koreanDateTime) {
        return koreanDateTime.atZone(ZONE).toInstant();
    }

    /**
     * 그 날짜가 시작하는 순간 (한국 시각 0시).
     * <p>
     * 문서의 '전날 자정'이다. 종일형 취소 마감(해당 날짜 시작 전, docs/08)은 이 순간보다 앞서야 한다.
     */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    /**
     * 그 날짜가 끝나는 순간 (한국 시각 24시 = 다음 날 0시).
     * <p>
     * 문서의 '그날 자정'이다. 이 순간 자체는 다음 날에 속하므로, 그 날짜 안인지 볼 때는 이 순간보다 앞선지로 판단한다.
     */
    public static Instant endOfDay(LocalDate date) {
        return startOfDay(date.plusDays(1));
    }

    /**
     * 기준 순간의 한국 날짜부터 대상 날짜까지의 달력 일수.
     * <p>
     * 공제율 구간의 '관람일 N일 전'이 이 값이다(docs/08). 시각은 보지 않고 날짜만 본다:
     * 공연 3/15이면 3/12 23:50은 3일 전, 3/13 10:00은 2일 전, 3/15 당일은 0이다.
     * 대상 날짜가 지났으면 음수다.
     */
    public static long daysUntil(Instant reference, LocalDate targetDate) {
        return ChronoUnit.DAYS.between(toDate(reference), targetDate);
    }
}
