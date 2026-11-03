package com.ticketon.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.AudienceSuspension;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventVisibilitySetting;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Venue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * 예매 자격 판정 테스트에 쓰는 문서 속 행사 · 관객.
 * <ul>
 *   <li><b>봄 정기공연</b>(극단 열두달, docs/15 박정훈) — 공연, 회차형 8회차: 3/14 · 3/15 · 3/21 · 3/22, 날마다 15:00~17:00 · 19:00~21:00
 *       (docs/06 "8회차(4일×2회) 공연"). 처음 값(1시간 전)으로 19:00 회차는 18:00에 예매를 닫는다.</li>
 *   <li><b>늦여름 페스티벌</b>(docs/15 이도현) — 페스티벌, 종일형 5/3 · 5/4 · 5/5, 12:00~22:00. 3일 통권(docs/05 "묶음권").</li>
 *   <li>두 행사 모두 3/1 14:00에 예매를 열었고, 판매 중의 지금은 3/10 12:00이다.</li>
 *   <li><b>관객 한서윤</b>(docs/15) — 1998-04-02생, 이메일 인증을 마친 활동 계정.</li>
 * </ul>
 */
final class BookingFixtures {

    static final long PLAY_EVENT_ID = 7L;
    static final long FESTIVAL_EVENT_ID = 8L;

    /** 공연의 날짜 id — 3/14 · 3/15 · 3/21 · 3/22. */
    static final long MARCH_14 = 314L;
    static final long MARCH_15 = 315L;
    static final long MARCH_21 = 321L;
    static final long MARCH_22 = 322L;

    /** 공연의 회차 id — 날짜 + 시작 시각(15시 · 19시). */
    static final long MARCH_14_15H = 31415L;
    static final long MARCH_14_19H = 31419L;
    static final long MARCH_15_15H = 31515L;
    static final long MARCH_15_19H = 31519L;
    static final long MARCH_21_15H = 32115L;
    static final long MARCH_21_19H = 32119L;
    static final long MARCH_22_15H = 32215L;
    static final long MARCH_22_19H = 32219L;

    /** 페스티벌의 날짜 id — 5/3 · 5/4 · 5/5. */
    static final long MAY_3 = 53L;
    static final long MAY_4 = 54L;
    static final long MAY_5 = 55L;

    /** 페스티벌의 회차 id — 종일형이라 날짜마다 하나. */
    static final long SESSION_MAY_3 = 503L;
    static final long SESSION_MAY_4 = 504L;
    static final long SESSION_MAY_5 = 505L;

    /** 예매 오픈 — 3/1 14:00. */
    static final Instant OPENS_AT = korean(2026, 3, 1, 14, 0);

    /** 오픈 전의 지금 — 2/20 12:00. */
    static final Instant BEFORE_OPEN = korean(2026, 2, 20, 12, 0);

    /** 판매 중의 지금 — 3/10 12:00. */
    static final Instant DURING_SALES = korean(2026, 3, 10, 12, 0);

    /** 관객 계정 id. */
    static final long AUDIENCE_ID = 21L;

    /** 한서윤의 생년월일 — 1998-04-02. */
    static final LocalDate SEOYUN_BIRTH_DATE = LocalDate.of(1998, 4, 2);

    private static final Instant LAST_MODIFIED_AT = korean(2026, 2, 10, 21, 0);
    private static final Instant SIGNED_UP_AT = korean(2025, 10, 15, 10, 0);
    private static final OperatingHours FESTIVAL_HOURS = OperatingHours.of(LocalTime.of(12, 0), LocalTime.of(22, 0));

    private BookingFixtures() {
    }

    static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    /** 3/1 14:00에 예매를 여는 예매 설정 — 나머지는 처음 값(1인 총 매수 4매 · 날짜별, 회차 선택 제약 없음, 연령 제한 없음). */
    static BookingSettings defaultSettings() {
        return settings(TicketsPerPersonLimit.defaultLimit(), SessionChoiceRestriction.NONE, AgeRestriction.none());
    }

    /** 3/1 14:00에 예매를 열고 매수 제한 · 회차 선택 제약 · 연령 제한만 바꾼 예매 설정. */
    static BookingSettings settings(TicketsPerPersonLimit ticketsPerPerson, SessionChoiceRestriction sessionChoice,
            AgeRestriction ageRestriction) {
        BookingSettings defaults = BookingSettings.defaults();
        return new BookingSettings(OPENS_AT, defaults.bookingCloseLeadTime(), defaults.checkinOpenLeadTime(),
                defaults.seatsPerSelection(), ticketsPerPerson, sessionChoice, defaults.reentry(), ageRestriction);
    }

    /** 그 상태로 저장된 봄 정기공연 — 처음 값 예매 설정. */
    static Event play(EventStatus status) {
        return play(status, defaultSettings());
    }

    /** 그 상태 · 예매 설정으로 저장된 봄 정기공연. 작성 중 · 판매 대기가 아니면 3/1 14:00에 예매가 열렸던 것이다. */
    static Event play(EventStatus status, BookingSettings bookingSettings) {
        return event(PLAY_EVENT_ID, new EventBasicInformation(
                EventTitle.of("2026 봄 정기공연 〈열두 번째 달〉"),
                EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 해마다 올리는 봄 정기공연입니다.",
                new Venue("○○아트홀", "서울특별시 종로구 대학로 12"),
                null, null, null), status, bookingSettings);
    }

    /** 그 예매 설정으로 판매 중인 늦여름 페스티벌. */
    static Event festival(BookingSettings bookingSettings) {
        return event(FESTIVAL_EVENT_ID, new EventBasicInformation(
                EventTitle.of("늦여름 페스티벌 2026"),
                EventType.FESTIVAL,
                EventCategoryCatalog.initial().select(EventType.FESTIVAL, "음악", null),
                "인디 밴드 열두 팀이 사흘 동안 무대에 오릅니다.",
                new Venue("늦여름 야외무대", "경기도 가평군 가평읍 강변로 3"),
                null, null, null), EventStatus.ON_SALE, bookingSettings);
    }

    private static Event event(long eventId, EventBasicInformation basicInformation, EventStatus status,
            BookingSettings bookingSettings) {
        boolean beforeOpening = status == EventStatus.DRAFT || status == EventStatus.AWAITING_OPEN;
        return Event.restore(eventId, 12L, new EventPublicAddress("Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG" + eventId),
                basicInformation, new EventPoster("Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg"), EventVisibilitySetting.publicByDefault(),
                bookingSettings, CancellationPolicy.standard(), status, beforeOpening ? null : OPENS_AT, List.of(),
                EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, LAST_MODIFIED_AT);
    }

    /** 봄 정기공연의 8회차 — 취소된 회차 없음. */
    static EventSchedule eightPerformances() {
        return eightPerformances(Set.of());
    }

    /** 봄 정기공연의 8회차 — 그 회차들은 3/5 10:00에 취소됐다. */
    static EventSchedule eightPerformances(Set<Long> cancelledSessionIds) {
        return EventSchedule.restore(PLAY_EVENT_ID, SessionMode.TIMED, null, List.of(
                twoPerformancesOn(MARCH_14, LocalDate.of(2026, 3, 14), MARCH_14_15H, MARCH_14_19H, cancelledSessionIds),
                twoPerformancesOn(MARCH_15, LocalDate.of(2026, 3, 15), MARCH_15_15H, MARCH_15_19H, cancelledSessionIds),
                twoPerformancesOn(MARCH_21, LocalDate.of(2026, 3, 21), MARCH_21_15H, MARCH_21_19H, cancelledSessionIds),
                twoPerformancesOn(MARCH_22, LocalDate.of(2026, 3, 22), MARCH_22_15H, MARCH_22_19H, cancelledSessionIds)));
    }

    private static EventDate twoPerformancesOn(long dateId, LocalDate date, long matineeId, long eveningId,
            Set<Long> cancelledSessionIds) {
        return EventDate.restore(dateId, date, null, List.of(
                performance(matineeId, 15, cancelledSessionIds),
                performance(eveningId, 19, cancelledSessionIds)));
    }

    private static EventSession performance(long sessionId, int startHour, Set<Long> cancelledSessionIds) {
        return EventSession.restore(sessionId, TimedSessionTime.of(LocalTime.of(startHour, 0), LocalTime.of(startHour + 2, 0)), null,
                SessionPriceAdjustment.none(), cancelledSessionIds.contains(sessionId) ? korean(2026, 3, 5, 10, 0) : null);
    }

    /** 늦여름 페스티벌의 일정 — 취소된 회차 없음. */
    static EventSchedule festivalSchedule() {
        return festivalSchedule(Set.of());
    }

    /** 늦여름 페스티벌의 일정 — 그 회차들은 취소됐다. */
    static EventSchedule festivalSchedule(Set<Long> cancelledSessionIds) {
        return EventSchedule.restore(FESTIVAL_EVENT_ID, SessionMode.ALL_DAY, FESTIVAL_HOURS, List.of(
                allDayDate(MAY_3, LocalDate.of(2026, 5, 3), SESSION_MAY_3, cancelledSessionIds),
                allDayDate(MAY_4, LocalDate.of(2026, 5, 4), SESSION_MAY_4, cancelledSessionIds),
                allDayDate(MAY_5, LocalDate.of(2026, 5, 5), SESSION_MAY_5, cancelledSessionIds)));
    }

    private static EventDate allDayDate(long dateId, LocalDate date, long sessionId, Set<Long> cancelledSessionIds) {
        return EventDate.restore(dateId, date, null, List.of(EventSession.restore(sessionId, null, null, SessionPriceAdjustment.none(),
                cancelledSessionIds.contains(sessionId) ? korean(2026, 4, 20, 10, 0) : null)));
    }

    /** 한서윤 — 이메일 인증을 마친 활동 계정. */
    static AudienceAccount seoyun() {
        return audienceBornOn(SEOYUN_BIRTH_DATE);
    }

    /** 그날 태어난, 이메일 인증을 마친 활동 계정. */
    static AudienceAccount audienceBornOn(LocalDate birthDate) {
        return audience(birthDate, AudienceAccountStatus.ACTIVE, List.of());
    }

    /** 번호를 바꿔가며 매수 제한을 넘은 예매로 관리자가 이용 정지한 계정(docs/01 "관객 이용 정지 — 쓰는 경우"). */
    static AudienceAccount suspendedAudience() {
        return audience(SEOYUN_BIRTH_DATE, AudienceAccountStatus.SUSPENDED,
                List.of(new AudienceSuspension("번호를 바꿔가며 매수 제한을 넘는 예매", korean(2026, 3, 2, 9, 0), null)));
    }

    /** 이용 정지됐다가 풀린 계정. */
    static AudienceAccount liftedAudience() {
        return audience(SEOYUN_BIRTH_DATE, AudienceAccountStatus.ACTIVE,
                List.of(new AudienceSuspension("반복되는 허위 신고", korean(2026, 3, 2, 9, 0), korean(2026, 3, 9, 9, 0))));
    }

    /** 가입만 하고 이메일 인증을 마치지 않은 계정. */
    static AudienceAccount unverifiedAudience() {
        return AudienceAccount.restore(AUDIENCE_ID, personalInformation(SEOYUN_BIRTH_DATE), new PasswordHash("{test}hashed-password"),
                SIGNED_UP_AT, SignupConsentChoices.agreeingToAllRequired(false).recordsAt(SIGNED_UP_AT),
                AudienceAccountStatus.PENDING_VERIFICATION, null, List.of());
    }

    private static AudienceAccount audience(LocalDate birthDate, AudienceAccountStatus status, List<AudienceSuspension> suspensions) {
        return AudienceAccount.restore(AUDIENCE_ID, personalInformation(birthDate), new PasswordHash("{test}hashed-password"),
                SIGNED_UP_AT, SignupConsentChoices.agreeingToAllRequired(false).recordsAt(SIGNED_UP_AT), status,
                SIGNED_UP_AT.plusSeconds(300), suspensions);
    }

    private static AudiencePersonalInformation personalInformation(LocalDate birthDate) {
        return AudiencePersonalInformation.of("한서윤", birthDate, "seoyun@example.com", "010-1234-5678");
    }

    /** 행사 페이지에서 새로 예매하려는 판정. */
    static BookingEligibilityJudge judge(Event event, EventSchedule schedule, AudienceAccount audience) {
        return BookingEligibilityJudge.of(event, schedule, audience, BookingAccess.EVENT_PAGE, BookingPurpose.NEW_BOOKING);
    }

    /** 막혔고, 그 까닭의 코드 · 문구가 기대한 것인지 본다. */
    static void assertRefused(BookingEligibility eligibility, ErrorCode expectedReason, String expectedMessage) {
        assertThat(eligibility.isEligible()).isFalse();
        BookingRefusal refusal = eligibility.refusal().orElseThrow();
        assertThat(refusal.reason()).isEqualTo(expectedReason);
        assertThat(refusal.code()).isEqualTo(expectedReason.name());
        assertThat(refusal.message()).isEqualTo(expectedMessage);
    }
}
