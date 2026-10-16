package com.ticketon.domain.event;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.policy.TimePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * 회차 하나의 시각들 — 날짜와 회차의 시간(회차형은 {@link TimedSessionTime}, 종일형은 그날의 {@link OperatingHours})을 한국 시각으로 읽어
 * 순간으로 바꾼 것. 예매 종료 · 검표 시작 · 검표 끝 · 회차 상태 · 시간 수정 잠금을 여기서 계산한다.
 * <p>
 * <b>회차형과 종일형이 같은 모양을 갖는다</b>(docs/02 "회차의 시간 정보") — 시작과 끝이 있고, 종일형만 그 사이에 마지막 입장이 있다.
 * 계산마다 어느 시각을 기준으로 삼는지가 회차 방식에 따라 갈린다.
 * <table>
 *   <caption>회차 방식별 기준 시각</caption>
 *   <tr><th>계산</th><th>회차형</th><th>종일형</th><th>근거</th></tr>
 *   <tr><td>시작 — 시간 수정 잠금 · 마지막 회차</td><td>시작</td><td>운영 시작</td><td>docs/04</td></tr>
 *   <tr><td>예매 종료 = 기준 − 예매 종료 설정</td><td>시작</td><td>마지막 입장(비우면 운영 종료)</td><td>docs/06 "예매 종료", docs/99 C</td></tr>
 *   <tr><td>검표 시작 = 기준 − 검표 시작 설정</td><td>시작</td><td>운영 시작</td><td>docs/03 "검표 시작 시각", docs/99 C</td></tr>
 *   <tr><td>검표 가능 구간의 끝</td><td>종료(비어 있을 수 있음)</td><td>마지막 입장(비우면 운영 종료)</td><td>docs/02 "입장 가능한 창구"</td></tr>
 *   <tr><td>끝 — 회차 상태 '종료' · 행사 종료</td><td>종료(비어 있을 수 있음)</td><td>운영 종료</td><td>docs/04 "행사 상태와 회차 상태"</td></tr>
 * </table>
 * 예: 19:00 회차형 회차는 처음 값(1시간)으로 18:00에 예매를 닫고 18:00부터 검표를 연다(docs/04 "판매 종료는 공연이 끝난 때가 아닙니다").
 * 10:00 ~ 18:00 종일형은 마지막 입장을 비우면 17:00에 예매를 닫고 9:00부터 검표를 연다(docs/03).
 * <p>
 * 이 값은 회차 시간을 읽어 낸 결과일 뿐이라 회차가 취소됐는지는 모른다({@link EventSession#isCancelled}).
 */
public final class SessionTimes {

    private final SessionMode mode;
    private final LocalDate date;
    private final Instant startsAt;
    private final Instant endsAt;
    private final Instant lastEntryAt;

    private SessionTimes(SessionMode mode, LocalDate date, Instant startsAt, Instant endsAt, Instant lastEntryAt) {
        this.mode = mode;
        this.date = date;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.lastEntryAt = lastEntryAt;
    }

    /** 회차형 회차의 시각 — 그 날짜의 시작 · 종료(비어 있으면 비어 있음). */
    public static SessionTimes timed(LocalDate date, TimedSessionTime time) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(time, "time");
        Instant endsAt = time.end().map(endTime -> KoreanTime.toInstant(date.atTime(endTime))).orElse(null);
        return new SessionTimes(SessionMode.TIMED, date, KoreanTime.toInstant(date.atTime(time.startTime())), endsAt, null);
    }

    /** 종일형 회차의 시각 — 그 날짜의 운영 시작 · 운영 종료 · 마지막 입장(비우면 운영 종료). */
    public static SessionTimes allDay(LocalDate date, OperatingHours operatingHours) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(operatingHours, "operatingHours");
        return new SessionTimes(SessionMode.ALL_DAY, date,
                KoreanTime.toInstant(date.atTime(operatingHours.openTime())),
                KoreanTime.toInstant(date.atTime(operatingHours.closeTime())),
                KoreanTime.toInstant(date.atTime(operatingHours.effectiveLastEntryTime())));
    }

    public SessionMode mode() {
        return mode;
    }

    /** 회차 날짜(한국 날짜). */
    public LocalDate date() {
        return date;
    }

    /** 시작 — 회차형은 시작 시각, 종일형은 운영 시작. */
    public Instant startsAt() {
        return startsAt;
    }

    /** 끝 — 회차형은 종료 시각, 종일형은 운영 종료. 회차형에서 종료 시각을 비워 두었으면 비어 있다(끝났는지 알 수 없다). */
    public Optional<Instant> endsAt() {
        return Optional.ofNullable(endsAt);
    }

    /** 실제로 쓰는 마지막 입장 — 종일형만 있다. 비워 두었으면 운영 종료. 회차형은 비어 있다. */
    public Optional<Instant> lastEntryAt() {
        return Optional.ofNullable(lastEntryAt);
    }

    /**
     * 그 회차의 예매 종료 시각 — 회차형은 시작, 종일형은 마지막 입장(비우면 운영 종료)보다 예매 종료 설정만큼 앞선다
     * ({@link BookingSettings#bookingCloseLeadTime}). 이 순간부터 그 회차는 팔지 않는다.
     */
    public Instant bookingClosesAt(BookingSettings bookingSettings) {
        Objects.requireNonNull(bookingSettings, "bookingSettings");
        Instant reference = mode == SessionMode.TIMED ? startsAt : lastEntryAt;
        return bookingSettings.bookingCloseLeadTime().before(reference);
    }

    /**
     * 그 회차의 검표 시작 시각 — 회차형은 시작, 종일형은 운영 시작보다 검표 시작 설정만큼 앞선다({@link BookingSettings#checkinOpenLeadTime}).
     * 이 순간부터 검표가 열린다. 열리기 전에 찍으면 "아직 입장 시간이 아닙니다"(docs/10 "검표 시작 시각").
     */
    public Instant checkinOpensAt(BookingSettings bookingSettings) {
        Objects.requireNonNull(bookingSettings, "bookingSettings");
        return bookingSettings.checkinOpenLeadTime().before(startsAt);
    }

    /**
     * 검표 가능 구간의 끝 — 회차형은 종료 시각, 종일형은 마지막 입장(비우면 운영 종료). 이 시각까지 들어올 수 있다.
     * <p>
     * 회차는 "입장 가능한 창구"이지 "퇴장 시각"이 아니다(docs/02) — 14-16시 회차는 15시 50분에 들어와도 되고, 이 끝은 관객이 나가야 하는
     * 시각이 아니다. 회차형에서 종료 시각을 비워 두었으면 비어 있다 — 그때 어떻게 판정할지는 검표 판정(#90)이 정한다.
     */
    public Optional<Instant> checkinClosesAt() {
        return mode == SessionMode.TIMED ? endsAt() : lastEntryAt();
    }

    /**
     * 지금 회차 상태 — 예매 종료 전이면 판매 중, 지났으면 판매 종료, 끝이 지났으면 종료. 끝을 알 수 없으면(회차형 종료 시각 비움) 예매 종료
     * 뒤로는 판매 종료에 머문다.
     */
    public SessionStatus status(BookingSettings bookingSettings, Instant now) {
        Objects.requireNonNull(now, "now");
        if (endsAt != null && !now.isBefore(endsAt)) {
            return SessionStatus.ENDED;
        }
        if (!now.isBefore(bookingClosesAt(bookingSettings))) {
            return SessionStatus.SALES_CLOSED;
        }
        return SessionStatus.ON_SALE;
    }

    /**
     * 그 회차의 시간 수정이 잠기는 순간 — 시작({@link #startsAt}) 1주일 전({@link TimePolicy#SESSION_TIME_EDIT_LOCK_BEFORE_START}). 잠금은
     * 회차마다 따로다 — 첫 공연이 시작했어도 아직 열흘 남은 마지막 주 회차는 고칠 수 있다(docs/04 "회차 시간 잠금은 회차마다 따로입니다").
     * <p>
     * 잠금이 언제 걸리는지(예매가 열린 뒤에만, 연기는 예외)와 잠긴 뒤 막는 일은 회차 시간 수정(#105) · 상태별 수정 범위(#38)가 한다.
     */
    public Instant timeEditLockedFrom() {
        return KoreanTime.toInstant(KoreanTime.toDateTime(startsAt).minus(TimePolicy.SESSION_TIME_EDIT_LOCK_BEFORE_START));
    }

    /** 지금 그 회차의 시간 수정이 잠기는 시점({@link #timeEditLockedFrom}) 이후인지 — 그 순간부터 잠긴다. */
    public boolean isTimeEditLocked(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(timeEditLockedFrom());
    }

    /**
     * 종료 시각 '입력 필요'인지 — 시간 수정이 잠기는 시점(회차 시작 1주일 전)부터 종료 시각이 비어 있으면. 종료 시각이 없으면 언제 끝났는지
     * 알 수 없어 행사 종료를 판정할 수 없기 때문이다(docs/02 "회차의 시간 정보", docs/04 "전환 트리거"). 종일형은 운영 종료가 늘 있어 해당하지
     * 않는다.
     * <p>
     * 잠긴 뒤에도 비어 있던 종료 시각을 채우는 것만은 할 수 있다({@link EventSchedule#enterSessionEndTime}).
     */
    public boolean needsEndTimeInput(Instant now) {
        return endsAt == null && isTimeEditLocked(now);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionTimes that)) {
            return false;
        }
        return mode == that.mode && date.equals(that.date) && startsAt.equals(that.startsAt)
                && Objects.equals(endsAt, that.endsAt) && Objects.equals(lastEntryAt, that.lastEntryAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, date, startsAt, endsAt, lastEntryAt);
    }

    @Override
    public String toString() {
        return "SessionTimes[" + mode + " " + date + ", startsAt=" + startsAt + ", endsAt=" + endsAt + ", lastEntryAt=" + lastEntryAt + "]";
    }
}
