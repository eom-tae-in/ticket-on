package com.ticketon.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("한국 시각 변환")
class KoreanTimeTest {

    @Test
    @DisplayName("서비스 기준 시간대는 Asia/Seoul이다")
    void zoneIsSeoul() {
        assertThat(KoreanTime.ZONE.getId()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("UTC 15시는 한국 날짜로 다음 날 0시다")
    void utcAfternoonIsNextKoreanDay() {
        assertThat(KoreanTime.toDate(Instant.parse("2026-03-14T14:59:59Z"))).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(KoreanTime.toDate(Instant.parse("2026-03-14T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(KoreanTime.toDateTime(Instant.parse("2026-03-14T15:00:00Z")))
                .isEqualTo(LocalDateTime.of(2026, 3, 15, 0, 0));
    }

    @Test
    @DisplayName("한국 낮 12시는 UTC 3시다 (전날 리마인더 발송 시각, docs/12)")
    void koreanNoonToInstant() {
        LocalDate sessionDate = LocalDate.of(2026, 3, 15);

        Instant reminderAt = KoreanTime.toInstant(sessionDate.minusDays(1).atTime(12, 0));

        assertThat(reminderAt).isEqualTo(Instant.parse("2026-03-14T03:00:00Z"));
    }

    @Test
    @DisplayName("한국 날짜·시각을 순간으로 바꿨다가 되돌리면 같다")
    void roundTrip() {
        LocalDateTime koreanDateTime = LocalDateTime.of(2026, 12, 31, 23, 59, 59);

        assertThat(KoreanTime.toDateTime(KoreanTime.toInstant(koreanDateTime))).isEqualTo(koreanDateTime);
    }

    @Test
    @DisplayName("전날 자정은 그 날짜 한국 시각 0시다")
    void startOfDayIsKoreanMidnight() {
        assertThat(KoreanTime.startOfDay(LocalDate.of(2026, 3, 15))).isEqualTo(Instant.parse("2026-03-14T15:00:00Z"));
    }

    @Test
    @DisplayName("종일형 3/15 취소 마감: 3/14 23:59:59는 마감 전, 3/15 0시는 마감 후 (docs/08)")
    void allDayCancellationDeadline() {
        Instant deadline = KoreanTime.startOfDay(LocalDate.of(2026, 3, 15));
        Instant lastSecondBefore = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 14, 23, 59, 59));
        Instant midnight = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 15, 0, 0));

        assertThat(lastSecondBefore).isBefore(deadline);
        assertThat(midnight).isEqualTo(deadline);
    }

    @Test
    @DisplayName("그날 자정은 다음 날 한국 시각 0시다")
    void endOfDayIsNextKoreanMidnight() {
        assertThat(KoreanTime.endOfDay(LocalDate.of(2026, 3, 15))).isEqualTo(Instant.parse("2026-03-15T15:00:00Z"));
        assertThat(KoreanTime.endOfDay(LocalDate.of(2026, 3, 15)))
                .isEqualTo(KoreanTime.startOfDay(LocalDate.of(2026, 3, 16)));
    }

    @Test
    @DisplayName("연말 그날 자정은 새해 0시다")
    void endOfDayAcrossYearBoundary() {
        Instant endOfYear = KoreanTime.endOfDay(LocalDate.of(2026, 12, 31));

        assertThat(endOfYear).isEqualTo(Instant.parse("2026-12-31T15:00:00Z"));
        assertThat(KoreanTime.toDate(endOfYear)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(KoreanTime.toDate(endOfYear.minusNanos(1))).isEqualTo(LocalDate.of(2026, 12, 31));
    }
}
