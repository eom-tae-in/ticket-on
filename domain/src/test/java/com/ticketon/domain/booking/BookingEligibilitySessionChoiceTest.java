package com.ticketon.domain.booking;

import static com.ticketon.domain.booking.BookingFixtures.DURING_SALES;
import static com.ticketon.domain.booking.BookingFixtures.FESTIVAL_EVENT_ID;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_14_15H;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_14_19H;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_15_19H;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_22_19H;
import static com.ticketon.domain.booking.BookingFixtures.MAY_3;
import static com.ticketon.domain.booking.BookingFixtures.MAY_4;
import static com.ticketon.domain.booking.BookingFixtures.SESSION_MAY_3;
import static com.ticketon.domain.booking.BookingFixtures.SESSION_MAY_4;
import static com.ticketon.domain.booking.BookingFixtures.SESSION_MAY_5;
import static com.ticketon.domain.booking.BookingFixtures.STANDING_A;
import static com.ticketon.domain.booking.BookingFixtures.assertRefused;
import static com.ticketon.domain.booking.BookingFixtures.audienceBornOn;
import static com.ticketon.domain.booking.BookingFixtures.bookedTicket;
import static com.ticketon.domain.booking.BookingFixtures.bookedTickets;
import static com.ticketon.domain.booking.BookingFixtures.cancelledBookedTicket;
import static com.ticketon.domain.booking.BookingFixtures.eightPerformances;
import static com.ticketon.domain.booking.BookingFixtures.festival;
import static com.ticketon.domain.booking.BookingFixtures.festivalPassTicket;
import static com.ticketon.domain.booking.BookingFixtures.festivalSchedule;
import static com.ticketon.domain.booking.BookingFixtures.invitationTicket;
import static com.ticketon.domain.booking.BookingFixtures.judge;
import static com.ticketon.domain.booking.BookingFixtures.korean;
import static com.ticketon.domain.booking.BookingFixtures.play;
import static com.ticketon.domain.booking.BookingFixtures.seoyun;
import static com.ticketon.domain.booking.BookingFixtures.settings;
import static com.ticketon.domain.booking.BookingFixtures.suspendedAudience;
import static com.ticketon.domain.booking.BookingFixtures.threeDayPass;
import static com.ticketon.domain.booking.BookingFixtures.threeDayPassSelection;
import static com.ticketon.domain.booking.BookingFixtures.weekendPass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.BundleProductName;
import com.ticketon.domain.pricing.BundleProductTerms;
import com.ticketon.domain.pricing.PricingErrorCode;
import com.ticketon.domain.ticket.Ticket;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("예매 자격 판정 — 회차 선택 (회차 상태 · 연령 제한 · 회차 선택 제약)")
class BookingEligibilitySessionChoiceTest {

    @Test
    @DisplayName("판매 중인 회차를 고르면 예매할 수 있다")
    void eligibleSession() {
        BookingEligibility eligibility = judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun())
                .atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES);

        assertThat(eligibility.isEligible()).isTrue();
    }

    @Nested
    @DisplayName("[예매하기]의 판정을 다시 한다 — 회차를 고르는 사이 바뀌었을 수 있다")
    class StartChecksAgain {

        @Test
        @DisplayName("판매 중단 중이면 회차를 고를 때도 막는다")
        void salesPaused() {
            assertRefused(judge(play(EventStatus.SALES_PAUSED), eightPerformances(), seoyun())
                            .atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES),
                    BookingErrorCode.SALES_PAUSED, "판매가 일시 중단되었습니다.");
        }

        @Test
        @DisplayName("이용 정지 계정은 회차를 고를 때도 막는다")
        void suspended() {
            assertRefused(judge(play(EventStatus.ON_SALE), eightPerformances(), suspendedAudience())
                            .atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES),
                    AccountErrorCode.AUDIENCE_SUSPENDED, "이용 정지 중에는 이용할 수 없는 기능입니다.");
        }
    }

    @Nested
    @DisplayName("회차 취소 안 됨 · 그 회차 예매 종료 전 (docs/06 \"예매 종료\", docs/04 \"회차 취소\")")
    class SessionState {

        @Test
        @DisplayName("3/14 19:00 회차는 1시간 전(18:00)까지 고를 수 있다 — 17:59")
        void beforeSessionBookingClose() {
            BookingEligibility eligibility = judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun())
                    .atSessionChoice(BookingSelection.session(MARCH_14_19H), korean(2026, 3, 14, 17, 59));

            assertThat(eligibility.isEligible()).isTrue();
        }

        @Test
        @DisplayName("3/14 18:00부터는 그 회차의 예매가 마감됐다 — 행사는 3/22까지 판매 중이어도")
        void afterSessionBookingClose() {
            BookingEligibilityJudge judge = judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun());

            assertRefused(judge.atSessionChoice(BookingSelection.session(MARCH_14_19H), korean(2026, 3, 14, 18, 0)),
                    BookingErrorCode.SESSION_BOOKING_CLOSED, "이 회차는 예매가 마감되었습니다.");
            assertThat(judge.atSessionChoice(BookingSelection.session(MARCH_15_19H), korean(2026, 3, 14, 18, 0)).isEligible()).isTrue();
        }

        @Test
        @DisplayName("취소된 회차는 고를 수 없다")
        void cancelledSession() {
            assertRefused(judge(play(EventStatus.ON_SALE), eightPerformances(Set.of(MARCH_14_19H)), seoyun())
                            .atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES),
                    BookingErrorCode.SESSION_CANCELLED, "취소된 회차라 예매할 수 없습니다.");
        }

        @Test
        @DisplayName("묶음권은 날짜 하나라도 취소됐으면 막는다")
        void bundleWithCancelledDay() {
            assertRefused(judge(festival(BookingFixtures.defaultSettings()), festivalSchedule(Set.of(SESSION_MAY_4)), seoyun())
                            .atSessionChoice(threeDayPassSelection(), DURING_SALES),
                    BookingErrorCode.SESSION_CANCELLED, "취소된 회차라 예매할 수 없습니다.");
        }

        @Test
        @DisplayName("일정에 없는 회차는 찾을 수 없다")
        void unknownSession() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun())
                            .atSessionChoice(BookingSelection.session(SESSION_MAY_3), DURING_SALES))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.SESSION_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("묶음권은 묶은 날짜마다 회차 하나씩 (docs/05 \"묶음권\")")
    class BundleSelection {

        @Test
        @DisplayName("3일 통권에 날짜마다 회차를 하나씩 고르면 예매할 수 있다")
        void onePerBundledDate() {
            assertThat(judge(festival(BookingFixtures.defaultSettings()), festivalSchedule(), seoyun())
                    .atSessionChoice(threeDayPassSelection(), DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("빠진 날짜가 있거나, 묶지 않은 날짜의 회차를 고르면 잘못된 선택이다")
        void mismatchedSessions() {
            BookingEligibilityJudge judge = judge(festival(BookingFixtures.defaultSettings()), festivalSchedule(), seoyun());

            assertBundleMismatch(() -> judge.atSessionChoice(
                    BookingSelection.bundle(threeDayPass(), List.of(SESSION_MAY_3, SESSION_MAY_4)), DURING_SALES));
            assertBundleMismatch(() -> judge.atSessionChoice(
                    BookingSelection.bundle(weekendPass(), List.of(SESSION_MAY_3, SESSION_MAY_4)), DURING_SALES));
            assertBundleMismatch(() -> judge.atSessionChoice(
                    BookingSelection.bundle(weekendPass(), List.of(SESSION_MAY_4, SESSION_MAY_4)), DURING_SALES));
        }

        @Test
        @DisplayName("다른 행사의 묶음권은 받지 않는다")
        void bundleOfAnotherEvent() {
            BundleProduct otherEventPass = BundleProduct.restore(99L, FESTIVAL_EVENT_ID + 1, new BundleProductTerms(
                    BundleProductName.of("2일권"), Set.of(MAY_3, MAY_4), Set.of(STANDING_A), Percent.of(3)));

            assertThatIllegalArgumentException().isThrownBy(() -> judge(festival(BookingFixtures.defaultSettings()), festivalSchedule(),
                    seoyun()).atSessionChoice(BookingSelection.bundle(otherEventPass, List.of(SESSION_MAY_3, SESSION_MAY_4)), DURING_SALES));
        }

        private void assertBundleMismatch(ThrowingCallable action) {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(action)
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.BUNDLE_SESSIONS_MISMATCH));
        }
    }

    @Nested
    @DisplayName("연령 제한 — 그 회차 날짜의 만 나이, 묶음권은 첫 날 (docs/03 \"연령 제한\")")
    class Age {

        private final Event adultsOnlyPlay = play(EventStatus.ON_SALE, ageRestricted(19));

        @Test
        @DisplayName("만 19세 기준 · 3/14 회차 — 19번째 생일이 회차 날짜 하루 전(2007-03-13생)이면 예매할 수 있다")
        void birthdayTheDayBeforeSession() {
            assertThat(atMarch14(LocalDate.of(2007, 3, 13)).isEligible()).isTrue();
        }

        @Test
        @DisplayName("만 19세 기준 · 3/14 회차 — 19번째 생일이 회차 당일(2007-03-14생)이면 예매할 수 있다")
        void birthdayOnSessionDate() {
            assertThat(atMarch14(LocalDate.of(2007, 3, 14)).isEligible()).isTrue();
        }

        @Test
        @DisplayName("만 19세 기준 · 3/14 회차 — 19번째 생일이 회차 다음 날(2007-03-15생)이면 \"이 공연은 만 19세 이상만 예매할 수 있습니다.\"")
        void birthdayTheDayAfterSession() {
            assertRefused(atMarch14(LocalDate.of(2007, 3, 15)),
                    BookingErrorCode.AGE_RESTRICTED, "이 공연은 만 19세 이상만 예매할 수 있습니다.");
        }

        @Test
        @DisplayName("오늘(3/10)이 아니라 회차 날짜로 센다 — 오늘 만 18세여도 3/14에 19세면(2007-03-12생) 예매할 수 있다")
        void ageOnSessionDateNotToday() {
            assertThat(atMarch14(LocalDate.of(2007, 3, 12)).isEligible()).isTrue();
        }

        @Test
        @DisplayName("같은 관객(2007-03-15생)도 3/14 회차는 막히고 3/15 회차는 된다")
        void sameAudienceDifferentSessions() {
            BookingEligibilityJudge judge = judge(adultsOnlyPlay, eightPerformances(), audienceBornOn(LocalDate.of(2007, 3, 15)));

            assertThat(judge.atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES).isEligible()).isFalse();
            assertThat(judge.atSessionChoice(BookingSelection.session(MARCH_15_19H), DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("묶음권은 첫 날(5/3)의 나이로 본다 — 5/4에 19세가 되는 2007-05-04생은 3일 통권을 살 수 없다")
        void bundleUsesFirstDate() {
            Event adultsOnlyFestival = festival(ageRestricted(19));

            assertRefused(judge(adultsOnlyFestival, festivalSchedule(), audienceBornOn(LocalDate.of(2007, 5, 4)))
                            .atSessionChoice(threeDayPassSelection(), DURING_SALES),
                    BookingErrorCode.AGE_RESTRICTED, "이 페스티벌은 만 19세 이상만 예매할 수 있습니다.");
        }

        @Test
        @DisplayName("묶음권의 첫 날이 5/4인 주말 2일권은 같은 관객(2007-05-04생)이 살 수 있다")
        void bundleStartingOnBirthday() {
            Event adultsOnlyFestival = festival(ageRestricted(19));

            assertThat(judge(adultsOnlyFestival, festivalSchedule(), audienceBornOn(LocalDate.of(2007, 5, 4)))
                    .atSessionChoice(BookingSelection.bundle(weekendPass(), List.of(SESSION_MAY_4, SESSION_MAY_5)), DURING_SALES)
                    .isEligible()).isTrue();
        }

        @Test
        @DisplayName("관객 화면의 행사 이름으로 부른다 — 전시는 \"이 전시는\"(받침 없음)")
        void exhibitionSubject() {
            assertRefused(judge(BookingFixtures.photoExhibition(ageRestricted(19)), eightPerformances(),
                            audienceBornOn(LocalDate.of(2007, 3, 15))).atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES),
                    BookingErrorCode.AGE_RESTRICTED, "이 전시는 만 19세 이상만 예매할 수 있습니다.");
        }

        @Test
        @DisplayName("연령 제한이 없으면 만 15세도 예매할 수 있다")
        void noAgeRestriction() {
            assertThat(judge(play(EventStatus.ON_SALE), eightPerformances(), audienceBornOn(LocalDate.of(2011, 1, 1)))
                    .atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("막힌 까닭을 던지면 채운 문구가 그대로 나간다")
        void thrownRefusalCarriesFilledMessage() {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> atMarch14(LocalDate.of(2007, 3, 15)).requireEligible())
                    .satisfies(exception -> {
                        assertThat(exception.errorCode().code()).isEqualTo("AGE_RESTRICTED");
                        assertThat(exception.errorCode().message()).isEqualTo("이 공연은 만 19세 이상만 예매할 수 있습니다.");
                    });
        }

        private BookingEligibility atMarch14(LocalDate birthDate) {
            return judge(adultsOnlyPlay, eightPerformances(), audienceBornOn(birthDate))
                    .atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES);
        }
    }

    @Nested
    @DisplayName("회차 선택 제약 — 이미 가진 유효 예매 티켓과 견준다 (docs/06 \"회차 선택 제약\")")
    class SessionChoice {

        @Test
        @DisplayName("'하루에 한 회차만' — 3/14 15시 2매를 가졌으면 3/14 19시는 고를 수 없다")
        void otherSessionOnSameDate() {
            assertRefused(onePerDate(bookedTickets(MARCH_14_15H, 2), MARCH_14_19H),
                    BookingErrorCode.ONE_SESSION_PER_DATE, "이 공연은 하루에 한 회차만 예매할 수 있습니다. 이 날짜의 다른 회차를 이미 예매했습니다.");
        }

        @Test
        @DisplayName("'하루에 한 회차만' — 다른 날(3/15 19시)은 고를 수 있다")
        void sessionOnAnotherDate() {
            assertThat(onePerDate(bookedTickets(MARCH_14_15H, 2), MARCH_15_19H).isEligible()).isTrue();
        }

        @Test
        @DisplayName("'하루에 한 회차만' — 같은 회차(3/14 15시)를 더 사는 것은 된다")
        void sameSessionAgain() {
            assertThat(onePerDate(bookedTickets(MARCH_14_15H, 2), MARCH_14_15H).isEligible()).isTrue();
        }

        @Test
        @DisplayName("'행사 전체에서 한 회차만' — 3/14 15시를 가졌으면 3/22 19시는 고를 수 없고, 같은 회차는 된다")
        void otherSessionInEvent() {
            assertRefused(onePerEvent(List.of(bookedTicket(MARCH_14_15H)), MARCH_22_19H),
                    BookingErrorCode.ONE_SESSION_PER_EVENT, "이 공연은 한 회차만 예매할 수 있습니다.");
            assertThat(onePerEvent(List.of(bookedTicket(MARCH_14_15H)), MARCH_14_15H).isEligible()).isTrue();
        }

        @Test
        @DisplayName("'행사 전체에서 한 회차만'이어도 아직 산 표가 없으면 어느 회차든 고를 수 있다")
        void firstSessionInEvent() {
            assertThat(onePerEvent(List.of(), MARCH_22_19H).isEligible()).isTrue();
        }

        @Test
        @DisplayName("'행사 전체에서 한 회차만'이면 회차 여럿을 가리키는 묶음권은 살 수 없다")
        void bundleUnderOneSessionPerEvent() {
            Event festival = festival(settings(TicketsPerPersonLimit.defaultLimit(), SessionChoiceRestriction.ONE_SESSION_PER_EVENT,
                    AgeRestriction.none()));

            assertRefused(judge(festival, festivalSchedule(), seoyun()).atSessionChoice(threeDayPassSelection(), DURING_SALES),
                    BookingErrorCode.ONE_SESSION_PER_EVENT, "이 페스티벌은 한 회차만 예매할 수 있습니다.");
        }

        @Test
        @DisplayName("'하루에 한 회차만'이면 3일권을 가진 관객도 날짜마다 회차가 하나뿐인 페스티벌의 3일권을 더 살 수 있다")
        void bundleUnderOneSessionPerDate() {
            Event festival = festival(settings(TicketsPerPersonLimit.defaultLimit(), SessionChoiceRestriction.ONE_SESSION_PER_DATE,
                    AgeRestriction.none()));

            assertThat(judge(festival, festivalSchedule(), seoyun(), List.of(festivalPassTicket()))
                    .atSessionChoice(threeDayPassSelection(), DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("제한 없음이면 같은 날 다른 회차도 고를 수 있다 (처음 값)")
        void noRestriction() {
            assertThat(judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun(), bookedTickets(MARCH_14_15H, 2))
                    .atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("초대권 티켓은 세지 않는다 — 3/14 15시 초대권을 받았어도 3/14 19시를 예매할 수 있다 (docs/09 \"발권 후\")")
        void invitationTicketsAreNotCounted() {
            assertThat(onePerDate(List.of(invitationTicket(MARCH_14_15H)), MARCH_14_19H).isEligible()).isTrue();
        }

        @Test
        @DisplayName("취소된 티켓은 세지 않는다")
        void cancelledTicketsAreNotCounted() {
            assertThat(onePerDate(List.of(cancelledBookedTicket(MARCH_14_15H)), MARCH_14_19H).isEligible()).isTrue();
        }

        @Test
        @DisplayName("취소된 회차는 세지 않는다 — 티켓은 유효한 채 회차만 취소됐어도 그 날 다른 회차를 고를 수 있다")
        void cancelledSessionsAreNotCounted() {
            Event play = play(EventStatus.ON_SALE, onePerDateSettings());

            assertThat(judge(play, eightPerformances(Set.of(MARCH_14_15H)), seoyun(), List.of(bookedTicket(MARCH_14_15H)))
                    .atSessionChoice(BookingSelection.session(MARCH_14_19H), DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("다른 행사의 티켓은 받지 않는다")
        void ticketOfAnotherEvent() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun(), List.of(festivalPassTicket())));
        }

        private BookingEligibility onePerDate(List<Ticket> heldTickets, long chosenSessionId) {
            return judge(play(EventStatus.ON_SALE, onePerDateSettings()), eightPerformances(), seoyun(), heldTickets)
                    .atSessionChoice(BookingSelection.session(chosenSessionId), DURING_SALES);
        }

        private BookingEligibility onePerEvent(List<Ticket> heldTickets, long chosenSessionId) {
            BookingSettings onePerEvent = settings(TicketsPerPersonLimit.defaultLimit(), SessionChoiceRestriction.ONE_SESSION_PER_EVENT,
                    AgeRestriction.none());
            return judge(play(EventStatus.ON_SALE, onePerEvent), eightPerformances(), seoyun(), heldTickets)
                    .atSessionChoice(BookingSelection.session(chosenSessionId), DURING_SALES);
        }

        private BookingSettings onePerDateSettings() {
            return settings(TicketsPerPersonLimit.defaultLimit(), SessionChoiceRestriction.ONE_SESSION_PER_DATE, AgeRestriction.none());
        }
    }

    private static BookingSettings ageRestricted(int age) {
        return settings(TicketsPerPersonLimit.defaultLimit(), SessionChoiceRestriction.NONE, AgeRestriction.atLeast(age));
    }
}
