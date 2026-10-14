package com.ticketon.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트용 시계. 정한 순간에 멈춰 있다가 테스트가 옮길 때만 움직인다. 시간대는 한국 시각({@link KoreanTime#ZONE})이다.
 * <p>
 * 서비스 단위 테스트는 생성자에 바로 넣고, Spring 통합 테스트는 {@code @TestBean(name = "clock")}으로 Clock 빈을 이것으로 바꾼다.
 * <pre>{@code
 * MutableClock clock = MutableClock.at(LocalDateTime.of(2026, 3, 14, 19, 0));
 * clock.advance(TimePolicy.SEAT_SELECTION_TIMER);   // 10분 뒤로
 * }</pre>
 * {@link #withZone}으로 만든 시계도 같은 순간을 가리킨다(하나를 옮기면 함께 옮겨진다).
 */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> currentInstant;
    private final ZoneId zone;

    private MutableClock(AtomicReference<Instant> currentInstant, ZoneId zone) {
        this.currentInstant = currentInstant;
        this.zone = zone;
    }

    /** 그 순간에 멈춘 시계. */
    public static MutableClock at(Instant instant) {
        return new MutableClock(new AtomicReference<>(Objects.requireNonNull(instant, "instant")), KoreanTime.ZONE);
    }

    /** 한국 날짜·시각에 멈춘 시계. */
    public static MutableClock at(LocalDateTime koreanDateTime) {
        return at(KoreanTime.toInstant(koreanDateTime));
    }

    /** 시계를 그 순간으로 옮긴다. 앞뒤 어느 쪽이든 된다. */
    public void moveTo(Instant instant) {
        currentInstant.set(Objects.requireNonNull(instant, "instant"));
    }

    /** 시계를 그 한국 날짜·시각으로 옮긴다. */
    public void moveTo(LocalDateTime koreanDateTime) {
        moveTo(KoreanTime.toInstant(koreanDateTime));
    }

    /** 시계를 그만큼 앞으로 옮긴다. 음수면 뒤로 옮긴다. */
    public void advance(Duration amount) {
        Objects.requireNonNull(amount, "amount");
        currentInstant.updateAndGet(instant -> instant.plus(amount));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(currentInstant, Objects.requireNonNull(newZone, "newZone"));
    }

    @Override
    public Instant instant() {
        return currentInstant.get();
    }
}
