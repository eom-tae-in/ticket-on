package com.ticketon.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("테스트용 고정·이동 시계")
class MutableClockTest {

    private static final LocalDateTime SESSION_START = LocalDateTime.of(2026, 3, 14, 19, 0);

    @Test
    @DisplayName("정한 순간에 멈춰 있고 시간대는 한국 시각이다")
    void staysAtGivenInstant() {
        MutableClock clock = MutableClock.at(SESSION_START);

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-03-14T10:00:00Z"));
        assertThat(clock.getZone()).isEqualTo(KoreanTime.ZONE);
        assertThat(LocalDateTime.now(clock)).isEqualTo(SESSION_START);
    }

    @Test
    @DisplayName("advance로 앞으로, 음수로 뒤로 옮긴다")
    void advance() {
        MutableClock clock = MutableClock.at(SESSION_START);

        clock.advance(Duration.ofMinutes(10));
        assertThat(LocalDateTime.now(clock)).isEqualTo(SESSION_START.plusMinutes(10));

        clock.advance(Duration.ofMinutes(-30));
        assertThat(LocalDateTime.now(clock)).isEqualTo(SESSION_START.minusMinutes(20));
    }

    @Test
    @DisplayName("moveTo로 원하는 한국 날짜·시각으로 옮긴다")
    void moveTo() {
        MutableClock clock = MutableClock.at(SESSION_START);

        clock.moveTo(LocalDateTime.of(2026, 3, 15, 0, 0));

        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-03-14T15:00:00Z"));
    }

    @Test
    @DisplayName("withZone으로 만든 시계도 같은 순간을 따라 움직인다")
    void withZoneSharesInstant() {
        MutableClock clock = MutableClock.at(SESSION_START);
        Clock utcView = clock.withZone(ZoneOffset.UTC);

        clock.advance(Duration.ofHours(1));

        assertThat(utcView.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(utcView.instant()).isEqualTo(clock.instant());
        assertThat(LocalDateTime.now(utcView)).isEqualTo(LocalDateTime.of(2026, 3, 14, 11, 0));
    }
}
