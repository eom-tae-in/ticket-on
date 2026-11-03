package com.ticketon.domain.booking;

import static com.ticketon.domain.booking.BookingFixtures.DURING_SALES;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_14_15H;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_14_19H;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_15_15H;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_21_19H;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_22_15H;
import static com.ticketon.domain.booking.BookingFixtures.MARCH_22_19H;
import static com.ticketon.domain.booking.BookingFixtures.SESSION_MAY_3;
import static com.ticketon.domain.booking.BookingFixtures.SESSION_MAY_4;
import static com.ticketon.domain.booking.BookingFixtures.SESSION_MAY_5;
import static com.ticketon.domain.booking.BookingFixtures.assertRefused;
import static com.ticketon.domain.booking.BookingFixtures.audienceBornOn;
import static com.ticketon.domain.booking.BookingFixtures.bookedTickets;
import static com.ticketon.domain.booking.BookingFixtures.cancelledBookedTicket;
import static com.ticketon.domain.booking.BookingFixtures.eightPerformances;
import static com.ticketon.domain.booking.BookingFixtures.festival;
import static com.ticketon.domain.booking.BookingFixtures.festivalDayTickets;
import static com.ticketon.domain.booking.BookingFixtures.festivalPassForfeiting;
import static com.ticketon.domain.booking.BookingFixtures.festivalPassInvitation;
import static com.ticketon.domain.booking.BookingFixtures.festivalPassTicket;
import static com.ticketon.domain.booking.BookingFixtures.festivalSchedule;
import static com.ticketon.domain.booking.BookingFixtures.invitationTickets;
import static com.ticketon.domain.booking.BookingFixtures.judge;
import static com.ticketon.domain.booking.BookingFixtures.play;
import static com.ticketon.domain.booking.BookingFixtures.seoyun;
import static com.ticketon.domain.booking.BookingFixtures.settings;
import static com.ticketon.domain.booking.BookingFixtures.suspendedAudience;
import static com.ticketon.domain.booking.BookingFixtures.threeDayPassSelection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.TicketsPerPersonBasis;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.ticket.Ticket;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("예매 자격 판정 — 좌석 · 매수 선택 (1인 총 매수, docs/06 \"1인 총 매수 — 기준을 고릅니다\")")
class BookingEligibilityTicketsPerPersonTest {

    private static final String PLAY_PER_DATE_MESSAGE = "이 공연은 한 사람이 하루에 4매까지 예매할 수 있습니다. 이미 예매한 표도 함께 셉니다.";
    private static final String PLAY_IN_EVENT_MESSAGE = "이 공연은 한 사람이 4매까지 예매할 수 있습니다. 이미 예매한 표도 함께 셉니다.";

    @Nested
    @DisplayName("날짜별 4매 (처음 값)")
    class PerDate {

        @Test
        @DisplayName("아직 산 표가 없으면 한 날짜에 4매까지 산다 — 5매는 막는다")
        void upToLimitOnFirstBooking() {
            assertThat(playPerDate(List.of(), MARCH_14_19H, 4).isEligible()).isTrue();
            assertRefused(playPerDate(List.of(), MARCH_14_19H, 5), BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_ON_DATE, PLAY_PER_DATE_MESSAGE);
        }

        @Test
        @DisplayName("같은 날 15시 2매를 가졌으면 19시 3매는 막는다 — 시간 회차별로 세면 15시와 19시를 다 사서 우회된다")
        void sameDateDifferentSessionsAreAddedUp() {
            assertRefused(playPerDate(bookedTickets(MARCH_14_15H, 2), MARCH_14_19H, 3),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_ON_DATE, PLAY_PER_DATE_MESSAGE);
        }

        @Test
        @DisplayName("같은 날 15시 2매를 가졌으면 19시 2매는 된다")
        void sameDateWithinLimit() {
            assertThat(playPerDate(bookedTickets(MARCH_14_15H, 2), MARCH_14_19H, 2).isEligible()).isTrue();
        }

        @Test
        @DisplayName("다른 날은 따로 센다 — 8회차(4일 × 2회) 공연이면 한 사람이 16매까지, 17번째는 막는다")
        void eachDateHasItsOwnLimit() {
            List<Ticket> twelveTickets = new ArrayList<>();
            twelveTickets.addAll(bookedTickets(MARCH_14_15H, 4));
            twelveTickets.addAll(bookedTickets(MARCH_15_15H, 4));
            twelveTickets.addAll(bookedTickets(MARCH_21_19H, 4));

            assertThat(playPerDate(twelveTickets, MARCH_22_19H, 4).isEligible()).isTrue();

            List<Ticket> sixteenTickets = new ArrayList<>(twelveTickets);
            sixteenTickets.addAll(bookedTickets(MARCH_22_15H, 4));
            assertRefused(playPerDate(sixteenTickets, MARCH_22_19H, 1),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_ON_DATE, PLAY_PER_DATE_MESSAGE);
        }

        private BookingEligibility playPerDate(List<Ticket> heldTickets, long sessionId, int ticketCount) {
            return judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun(), heldTickets)
                    .atTicketChoice(BookingSelection.session(sessionId), ticketCount, DURING_SALES);
        }
    }

    @Nested
    @DisplayName("행사 전체 4매")
    class InEvent {

        @Test
        @DisplayName("3/14 15시 2매를 가졌으면 3/22 19시는 2매까지 — 3매는 막는다. 8회차 공연이어도 4매다")
        void wholeEventIsOneLimit() {
            assertThat(playInEvent(bookedTickets(MARCH_14_15H, 2), MARCH_22_19H, 2).isEligible()).isTrue();
            assertRefused(playInEvent(bookedTickets(MARCH_14_15H, 2), MARCH_22_19H, 3),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_IN_EVENT, PLAY_IN_EVENT_MESSAGE);
        }

        @Test
        @DisplayName("4매를 가졌으면 다른 날도 더 살 수 없다")
        void limitReached() {
            assertRefused(playInEvent(bookedTickets(MARCH_14_15H, 4), MARCH_22_19H, 1),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_IN_EVENT, PLAY_IN_EVENT_MESSAGE);
        }

        private BookingEligibility playInEvent(List<Ticket> heldTickets, long sessionId, int ticketCount) {
            Event play = play(EventStatus.ON_SALE, limit(TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.EVENT_TOTAL)));
            return judge(play, eightPerformances(), seoyun(), heldTickets)
                    .atTicketChoice(BookingSelection.session(sessionId), ticketCount, DURING_SALES);
        }
    }

    @Nested
    @DisplayName("묶음권 1장 = 묶은 날짜마다 1매 (docs/05 \"매수 제한 계산\")")
    class Bundle {

        private static final String FESTIVAL_PER_DATE_MESSAGE =
                "이 페스티벌은 한 사람이 하루에 4매까지 예매할 수 있습니다. 이미 예매한 표도 함께 셉니다. 묶음권은 날짜마다 1매로 셉니다.";
        private static final String FESTIVAL_IN_EVENT_MESSAGE =
                "이 페스티벌은 한 사람이 4매까지 예매할 수 있습니다. 이미 예매한 표도 함께 셉니다. 묶음권은 날짜마다 1매로 셉니다.";

        @Test
        @DisplayName("날짜별 4매 — 3일권 1장이 5/3 · 5/4 · 5/5에 1매씩 차지해, 5/4 1일권은 3매까지 된다")
        void passTakesOnePerDate() {
            List<Ticket> onePass = List.of(festivalPassTicket());

            assertThat(perDate(onePass).atTicketChoice(BookingSelection.session(SESSION_MAY_4), 3, DURING_SALES).isEligible()).isTrue();
            assertRefused(perDate(onePass).atTicketChoice(BookingSelection.session(SESSION_MAY_4), 4, DURING_SALES),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_ON_DATE, FESTIVAL_PER_DATE_MESSAGE);
        }

        @Test
        @DisplayName("날짜별 4매 — 3일권 1장을 가졌으면 3일권을 3장 더 살 수 있고 4장은 막는다")
        void passesAddUpPerDate() {
            List<Ticket> onePass = List.of(festivalPassTicket());

            assertThat(perDate(onePass).atTicketChoice(threeDayPassSelection(), 3, DURING_SALES).isEligible()).isTrue();
            assertRefused(perDate(onePass).atTicketChoice(threeDayPassSelection(), 4, DURING_SALES),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_ON_DATE, FESTIVAL_PER_DATE_MESSAGE);
        }

        @Test
        @DisplayName("날짜별 4매 — 5/4 1일권 4매를 가졌으면 그날이 든 3일권은 살 수 없다")
        void dayTicketsBlockPassOnThatDate() {
            assertRefused(perDate(festivalDayTickets(SESSION_MAY_4, 4)).atTicketChoice(threeDayPassSelection(), 1, DURING_SALES),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_ON_DATE, FESTIVAL_PER_DATE_MESSAGE);
        }

        @Test
        @DisplayName("행사 전체 4매 — 3일권 1장은 3매라 3일권 2장(6매)은 막는다")
        void passCountsAsThreeInEvent() {
            assertThat(inEvent(List.of()).atTicketChoice(threeDayPassSelection(), 1, DURING_SALES).isEligible()).isTrue();
            assertRefused(inEvent(List.of()).atTicketChoice(threeDayPassSelection(), 2, DURING_SALES),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_IN_EVENT, FESTIVAL_IN_EVENT_MESSAGE);
        }

        @Test
        @DisplayName("행사 전체 4매 — 3일권 1장(3매)을 가졌으면 1일권은 1매까지 된다")
        void heldPassCountsAsThreeInEvent() {
            List<Ticket> onePass = List.of(festivalPassTicket());

            assertThat(inEvent(onePass).atTicketChoice(BookingSelection.session(SESSION_MAY_3), 1, DURING_SALES).isEligible()).isTrue();
            assertRefused(inEvent(onePass).atTicketChoice(BookingSelection.session(SESSION_MAY_3), 2, DURING_SALES),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_IN_EVENT, FESTIVAL_IN_EVENT_MESSAGE);
        }

        @Test
        @DisplayName("묶음권에서 주최자가 하루(5/4)를 취소했으면 그날은 세지 않는다 — 행사 전체로 2매")
        void cancelledDayOfPassIsNotCounted() {
            EventSchedule may4Cancelled = festivalSchedule(Set.of(SESSION_MAY_4));
            BookingEligibilityJudge judge = judge(festival(limit(TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.EVENT_TOTAL))),
                    may4Cancelled, seoyun(), List.of(festivalPassTicket()));

            assertThat(judge.atTicketChoice(BookingSelection.session(SESSION_MAY_5), 2, DURING_SALES).isEligible()).isTrue();
            assertThat(judge.atTicketChoice(BookingSelection.session(SESSION_MAY_5), 3, DURING_SALES).isEligible()).isFalse();
        }

        @Test
        @DisplayName("출연자가 바뀐 날(5/4)을 포기한 3일권은 그날도 센다 — 티켓이 계속 가리킨다")
        void forfeitedDayOfPassIsCounted() {
            List<Ticket> forfeitedPass = List.of(festivalPassForfeiting(SESSION_MAY_4));

            assertThat(perDate(forfeitedPass).atTicketChoice(BookingSelection.session(SESSION_MAY_4), 3, DURING_SALES).isEligible()).isTrue();
            assertRefused(perDate(forfeitedPass).atTicketChoice(BookingSelection.session(SESSION_MAY_4), 4, DURING_SALES),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_ON_DATE, FESTIVAL_PER_DATE_MESSAGE);
        }

        @Test
        @DisplayName("3일권 초대권은 세지 않는다 — 4장을 받았어도 3일권 4장을 예매할 수 있다")
        void passInvitationsAreNotCounted() {
            List<Ticket> fourInvitations = List.of(festivalPassInvitation(), festivalPassInvitation(), festivalPassInvitation(),
                    festivalPassInvitation());

            assertThat(perDate(fourInvitations).atTicketChoice(threeDayPassSelection(), 4, DURING_SALES).isEligible()).isTrue();
        }

        private BookingEligibilityJudge perDate(List<Ticket> heldTickets) {
            return judge(festival(BookingFixtures.defaultSettings()), festivalSchedule(), seoyun(), heldTickets);
        }

        private BookingEligibilityJudge inEvent(List<Ticket> heldTickets) {
            return judge(festival(limit(TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.EVENT_TOTAL))), festivalSchedule(), seoyun(),
                    heldTickets);
        }
    }

    @Nested
    @DisplayName("세지 않는 표 (docs/06 \"집계 기준\", docs/09 \"발권 후\")")
    class NotCounted {

        @Test
        @DisplayName("초대권은 매수 제한에 포함되지 않는다 — 3/14 초대권 4장을 받았어도 3/14를 4매 예매할 수 있다")
        void invitationsAreNotCounted() {
            assertThat(judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun(), invitationTickets(MARCH_14_15H, 4))
                    .atTicketChoice(BookingSelection.session(MARCH_14_19H), 4, DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("취소된 티켓은 세지 않는다 — 2매를 취소하고 2매가 남았으면 같은 날 2매까지")
        void cancelledTicketsAreNotCounted() {
            List<Ticket> held = new ArrayList<>(bookedTickets(MARCH_14_15H, 2));
            held.add(cancelledBookedTicket(MARCH_14_15H));
            held.add(cancelledBookedTicket(MARCH_14_15H));
            BookingEligibilityJudge judge = judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun(), held);

            assertThat(judge.atTicketChoice(BookingSelection.session(MARCH_14_19H), 2, DURING_SALES).isEligible()).isTrue();
            assertThat(judge.atTicketChoice(BookingSelection.session(MARCH_14_19H), 3, DURING_SALES).isEligible()).isFalse();
        }
    }

    @Nested
    @DisplayName("판매 중에 매수 제한을 바꾸면 신규 예매부터, 이미 산 표도 센다 (docs/04 \"예매가 열린 뒤(판매 중) 수정 범위\")")
    class ChangedDuringSales {

        @Test
        @DisplayName("3/14에 3매를 산 뒤 한도를 날짜별 2매로 낮추면 그날은 더 살 수 없고, 다른 날은 2매까지다")
        void loweredLimitCountsAlreadyBoughtTickets() {
            Event lowered = play(EventStatus.ON_SALE, limit(TicketsPerPersonLimit.of(2, TicketsPerPersonBasis.PER_DATE)));
            BookingEligibilityJudge judge = judge(lowered, eightPerformances(), seoyun(), bookedTickets(MARCH_14_15H, 3));

            assertRefused(judge.atTicketChoice(BookingSelection.session(MARCH_14_19H), 1, DURING_SALES),
                    BookingErrorCode.TICKETS_PER_PERSON_EXCEEDED_ON_DATE,
                    "이 공연은 한 사람이 하루에 2매까지 예매할 수 있습니다. 이미 예매한 표도 함께 셉니다.");
            assertThat(judge.atTicketChoice(BookingSelection.session(MARCH_15_15H), 2, DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("제한 없음으로 바꾸면 몇 장을 가졌든 더 산다")
        void unlimited() {
            Event unlimited = play(EventStatus.ON_SALE, limit(TicketsPerPersonLimit.unlimited()));

            assertThat(judge(unlimited, eightPerformances(), seoyun(), bookedTickets(MARCH_14_15H, 4))
                    .atTicketChoice(BookingSelection.session(MARCH_14_19H), 4, DURING_SALES).isEligible()).isTrue();
        }
    }

    @Nested
    @DisplayName("앞 단계의 판정을 다시 한다")
    class EarlierStagesAgain {

        @Test
        @DisplayName("이용 정지 계정은 매수를 고를 때도 막는다")
        void suspended() {
            assertRefused(judge(play(EventStatus.ON_SALE), eightPerformances(), suspendedAudience())
                            .atTicketChoice(BookingSelection.session(MARCH_14_19H), 1, DURING_SALES),
                    AccountErrorCode.AUDIENCE_SUSPENDED, "이용 정지 중에는 이용할 수 없는 기능입니다.");
        }

        @Test
        @DisplayName("연령 미달과 매수 초과가 겹치면 연령 제한을 먼저 알린다 — 회차를 고르는 순간 막혔어야 할 까닭이다")
        void ageComesBeforeTicketsPerPerson() {
            Event adultsOnly = play(EventStatus.ON_SALE,
                    settings(TicketsPerPersonLimit.defaultLimit(), SessionChoiceRestriction.NONE, AgeRestriction.atLeast(19)));

            assertRefused(judge(adultsOnly, eightPerformances(), audienceBornOn(LocalDate.of(2010, 1, 1)))
                            .atTicketChoice(BookingSelection.session(MARCH_14_19H), 5, DURING_SALES),
                    BookingErrorCode.AGE_RESTRICTED, "이 공연은 만 19세 이상만 예매할 수 있습니다.");
        }

        @Test
        @DisplayName("이번에 담는 티켓은 1장 이상이다")
        void ticketCountMustBePositive() {
            assertThatIllegalArgumentException().isThrownBy(() -> judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun())
                    .atTicketChoice(BookingSelection.session(MARCH_14_19H), 0, DURING_SALES));
        }
    }

    @Test
    @DisplayName("변경은 바꿀 티켓을 빼고 센다 — 3/14 15시 4매 중 2매를 같은 날 19시로 옮기면 남은 2매 + 2매로 된다")
    void changeExcludesTicketsBeingReplaced() {
        List<Ticket> fourTickets = bookedTickets(MARCH_14_15H, 4);
        List<Ticket> remaining = fourTickets.subList(2, 4);

        BookingEligibility eligibility = BookingEligibilityJudge.of(play(EventStatus.ON_SALE), eightPerformances(), seoyun(), remaining,
                BookingAccess.EVENT_PAGE, BookingPurpose.CHANGE).atTicketChoice(BookingSelection.session(MARCH_14_19H), 2, DURING_SALES);

        assertThat(eligibility.isEligible()).isTrue();
    }

    private static BookingSettings limit(TicketsPerPersonLimit ticketsPerPerson) {
        return settings(ticketsPerPerson, SessionChoiceRestriction.NONE, AgeRestriction.none());
    }
}
