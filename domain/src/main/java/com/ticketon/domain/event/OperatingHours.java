package com.ticketon.domain.event;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 운영 시간 — 종일형 회차의 시간. 운영 시작~종료(필수)와 마지막 입장 시각(선택)(docs/02 "회차의 시간 정보"). 그 날짜의 한국 시각이다.
 * <blockquote>3/14 · 10:00 오픈 ~ 18:00 마감</blockquote>
 * <b>마지막 입장을 비워 두면 운영 종료와 같게 처리한다</b>({@link #effectiveLastEntryTime}). 비워 둔 것과 운영 종료와 같은 시각을 적은 것은
 * 지금은 같게 쓰이지만 다른 값이다 — 비워 두면 나중에 운영 종료를 바꿀 때 따라 바뀐다.
 * <p>
 * 날짜마다 다를 수 있다 — 전시가 주말에 더 늦게까지 여는 경우가 흔하다. 30일짜리 전시에 30번 입력하게 하지 않으려고 행사 단위로 기본 운영
 * 시간을 한 번 넣고 다른 날만 덮어쓴다(docs/02 "날짜마다 운영 시간이 다를 수 있습니다", {@link EventSchedule#operatingHoursOn}).
 * <p>
 * 운영 종료는 운영 시작보다 늦어야 하고, 마지막 입장은 운영 시작보다 늦고 운영 종료보다 늦지 않아야 한다. 자정을 넘겨 운영하는 날(18:00 ~
 * 02:00)은 문서에 없어 받지 않는다(확인 필요 #36, #135).
 * <p>
 * 시각은 분 단위다 — 초 · 나노초가 붙은 시각은 받지 않는다({@link MinutePrecision}, 확인 필요 #41).
 *
 * @param openTime      운영 시작
 * @param closeTime     운영 종료
 * @param lastEntryTime 마지막 입장. 비워 두면 null — 운영 종료와 같게 처리한다
 */
public record OperatingHours(LocalTime openTime, LocalTime closeTime, LocalTime lastEntryTime) {

    public OperatingHours {
        MinutePrecision.requireWholeMinute(Objects.requireNonNull(openTime, "openTime"), "운영 시작");
        MinutePrecision.requireWholeMinute(Objects.requireNonNull(closeTime, "closeTime"), "운영 종료");
        if (lastEntryTime != null) {
            MinutePrecision.requireWholeMinute(lastEntryTime, "마지막 입장");
        }
        if (!closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("운영 종료는 운영 시작보다 늦어야 합니다: " + openTime + " ~ " + closeTime);
        }
        if (lastEntryTime != null && !isWithin(openTime, closeTime, lastEntryTime)) {
            throw new IllegalArgumentException(
                    "마지막 입장은 운영 시작보다 늦고 운영 종료보다 늦지 않아야 합니다: " + openTime + " ~ " + closeTime + ", 마지막 입장 " + lastEntryTime);
        }
    }

    /** 마지막 입장을 비운 운영 시간. */
    public static OperatingHours of(LocalTime openTime, LocalTime closeTime) {
        return new OperatingHours(openTime, closeTime, null);
    }

    /**
     * @param lastEntryTime 마지막 입장. 비워 두면 null
     * @throws IllegalArgumentException 운영 종료가 운영 시작보다 늦지 않거나, 마지막 입장이 운영 시간 밖이면
     */
    public static OperatingHours of(LocalTime openTime, LocalTime closeTime, LocalTime lastEntryTime) {
        return new OperatingHours(openTime, closeTime, lastEntryTime);
    }

    /** {@link #of(LocalTime, LocalTime, LocalTime)}로 만들 수 있는 값인지 — 분 단위이고 차례가 맞다. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(LocalTime openTime, LocalTime closeTime, LocalTime lastEntryTime) {
        if (openTime == null || closeTime == null || !MinutePrecision.isWholeMinute(openTime)
                || !MinutePrecision.isWholeMinute(closeTime) || !closeTime.isAfter(openTime)) {
            return false;
        }
        return lastEntryTime == null
                || (MinutePrecision.isWholeMinute(lastEntryTime) && isWithin(openTime, closeTime, lastEntryTime));
    }

    /** 적은 마지막 입장. 비워 두었으면 비어 있다. */
    public Optional<LocalTime> lastEntry() {
        return Optional.ofNullable(lastEntryTime);
    }

    /** 실제로 쓰는 마지막 입장 — 비워 두었으면 운영 종료(docs/02 "종일형 마지막 입장은 비워두면 운영 종료 시각과 같게 처리"). */
    public LocalTime effectiveLastEntryTime() {
        return lastEntryTime == null ? closeTime : lastEntryTime;
    }

    private static boolean isWithin(LocalTime openTime, LocalTime closeTime, LocalTime lastEntryTime) {
        return lastEntryTime.isAfter(openTime) && !lastEntryTime.isAfter(closeTime);
    }
}
