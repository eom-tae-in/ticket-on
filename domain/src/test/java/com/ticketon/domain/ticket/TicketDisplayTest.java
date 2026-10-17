package com.ticketon.domain.ticket;

import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MARCH_14;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_3;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_4;
import static com.ticketon.domain.ticket.TicketFixtures.SESSION_MAY_5;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionTimes;
import com.ticketon.domain.event.TimedSessionTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("관객 화면의 표시는 계산된다 — 유효 · 입장 완료 · 공연 종료 · 취소됨 (docs/07, docs/14 \"저장하는 것과 계산하는 것\")")
class TicketDisplayTest {

    /** 3월 14일 (금) 19:00 ~ 21:00 회차 (docs/07 "티켓 화면"). */
    private static final SessionTimes MARCH_14_19_TO_21 =
            SessionTimes.timed(LocalDate.of(2026, 3, 14), TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)));

    /** 8회차 행사의 다음 회차 — 3월 15일 19:00 ~ 21:00 (docs/07 "화면에서는 버튼만 빠집니다"). */
    private static final long SESSION_MARCH_15 = 315L;
    private static final SessionTimes MARCH_15_19_TO_21 =
            SessionTimes.timed(LocalDate.of(2026, 3, 15), TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)));

    /** 페스티벌 3일권 — 5/3 · 5/4 · 5/5 종일 10:00 ~ 22:00 (docs/07 "묶음권 티켓"). */
    private static final OperatingHours FESTIVAL_HOURS = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(22, 0));
    private static final Map<Long, SessionTimes> FESTIVAL_TIMES = Map.of(
            SESSION_MAY_3, SessionTimes.allDay(LocalDate.of(2026, 5, 3), FESTIVAL_HOURS),
            SESSION_MAY_4, SessionTimes.allDay(LocalDate.of(2026, 5, 4), FESTIVAL_HOURS),
            SESSION_MAY_5, SessionTimes.allDay(LocalDate.of(2026, 5, 5), FESTIVAL_HOURS));

    private static final Map<Long, SessionTimes> MARCH_14_TIMES = Map.of(SESSION_MARCH_14, MARCH_14_19_TO_21);

    private static Instant at(int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(2026, month, day, hour, minute));
    }

    @Nested
    @DisplayName("상태")
    class Status {

        @Test
        @DisplayName("유효 — 취소 안 됨 + 회차가 아직 안 지남. QR · 공유 버튼이 있다")
        void valid() {
            TicketDisplay display = TicketFixtures.designatedTicket().display(MARCH_14_TIMES, false, at(3, 14, 18, 0));

            assertThat(display.status()).isEqualTo(TicketDisplayStatus.VALID);
            assertThat(display.label()).isEqualTo("유효");
            assertThat(display.showsQrAndShareButtons()).isTrue();
        }

        @Test
        @DisplayName("입장 완료 — 입장 기록이 있음. 버튼은 그대로 있다 (\"R석 12열 8번 [QR 보기] [공유] ✓ 입장 완료\")")
        void entered() {
            TicketDisplay display = TicketFixtures.designatedTicket().display(MARCH_14_TIMES, true, at(3, 14, 19, 5));

            assertThat(display.status()).isEqualTo(TicketDisplayStatus.ENTERED);
            assertThat(display.label()).isEqualTo("입장 완료");
            assertThat(display.showsQrAndShareButtons()).isTrue();
        }

        @Test
        @DisplayName("취소됨 · 사유 — 취소 시각이 있음. 회색 줄로 남고 버튼이 없다 (\"R석 12열 9번 취소됨 · 관객 취소\")")
        void cancelled() {
            Ticket ticket = TicketFixtures.designatedTicket();
            ticket.cancel(TicketCancellationReason.AUDIENCE_CANCELLATION, at(3, 5, 14, 20));

            TicketDisplay display = ticket.display(MARCH_14_TIMES, false, at(3, 10, 12, 0));

            assertThat(display.status()).isEqualTo(TicketDisplayStatus.CANCELLED);
            assertThat(display.cancellationReason()).contains(TicketCancellationReason.AUDIENCE_CANCELLATION);
            assertThat(display.label()).isEqualTo("취소됨 · 관객 취소");
            assertThat(display.showsQrAndShareButtons()).isFalse();
        }

        @Test
        @DisplayName("취소된 티켓은 공연이 끝난 뒤에도 '취소됨 · 사유'로 남는다")
        void cancelledStaysAfterEnd() {
            Ticket ticket = TicketFixtures.designatedTicket();
            ticket.cancel(TicketCancellationReason.SESSION_CANCELLED, at(3, 10, 12, 0));

            assertThat(ticket.display(MARCH_14_TIMES, false, at(3, 20, 12, 0)).label()).isEqualTo("취소됨 · 회차 취소");
        }

        @Test
        @DisplayName("회수된 초대권은 '회수됨'으로 남는다 (docs/09 \"단체 링크 화면\")")
        void recalledInvitation() {
            Ticket ticket = TicketFixtures.invitationTicket();
            ticket.cancel(TicketCancellationReason.INVITATION_RECALLED, at(3, 10, 12, 0));

            TicketDisplay display = ticket.display(MARCH_14_TIMES, false, at(3, 10, 13, 0));

            assertThat(display.label()).isEqualTo("회수됨");
            assertThat(display.showsQrAndShareButtons()).isFalse();
        }

        @Test
        @DisplayName("상태로 저장하지 않는다 — 같은 티켓이 시각과 입장 기록에 따라 달리 보인다")
        void computedEachTime() {
            Ticket ticket = TicketFixtures.designatedTicket();

            assertThat(ticket.display(MARCH_14_TIMES, false, at(3, 14, 18, 0)).status()).isEqualTo(TicketDisplayStatus.VALID);
            assertThat(ticket.display(MARCH_14_TIMES, true, at(3, 14, 19, 5)).status()).isEqualTo(TicketDisplayStatus.ENTERED);
            assertThat(ticket.display(MARCH_14_TIMES, true, at(3, 14, 21, 0)).status())
                    .isEqualTo(TicketDisplayStatus.PERFORMANCE_ENDED);
        }
    }

    @Nested
    @DisplayName("공연 종료 — 가리키는 회차가 모두 끝나면 버튼을 그리지 않는다 (docs/07 \"화면에서는 버튼만 빠집니다\")")
    class PerformanceEnded {

        @Test
        @DisplayName("3/14 21:00 회차가 끝나는 순간부터 '공연 종료', 버튼 없음 — 좌석 · 금액만 보인다")
        void endsAtSessionEnd() {
            Ticket ticket = TicketFixtures.designatedTicket();

            assertThat(ticket.display(MARCH_14_TIMES, false, at(3, 14, 20, 59)).showsQrAndShareButtons()).isTrue();
            TicketDisplay ended = ticket.display(MARCH_14_TIMES, false, at(3, 14, 21, 0));
            assertThat(ended.status()).isEqualTo(TicketDisplayStatus.PERFORMANCE_ENDED);
            assertThat(ended.label()).isEqualTo("공연 종료");
            assertThat(ended.showsQrAndShareButtons()).isFalse();
        }

        @Test
        @DisplayName("입장 기록이 있어도 공연이 끝나면 '공연 종료'다 — 1년 전 공연에 '입장 완료'를 붙이지 않는다")
        void endedWinsOverEntered() {
            TicketDisplay display = TicketFixtures.designatedTicket().display(MARCH_14_TIMES, true, at(3, 15, 10, 0));

            assertThat(display.status()).isEqualTo(TicketDisplayStatus.PERFORMANCE_ENDED);
        }

        @Test
        @DisplayName("판단은 티켓마다다 — 8회차 행사에서 3/14 표는 3/15 회차가 남아 있어도 3/14 공연이 끝나면 버튼이 사라진다")
        void judgedPerTicket() {
            Map<Long, SessionTimes> eventSessions = Map.of(SESSION_MARCH_14, MARCH_14_19_TO_21, SESSION_MARCH_15, MARCH_15_19_TO_21);

            assertThat(TicketFixtures.designatedTicket().display(eventSessions, false, at(3, 14, 22, 0)).showsQrAndShareButtons())
                    .isFalse();
        }

        @Test
        @DisplayName("3일권은 마지막 날이 끝나야 버튼이 사라진다 — 5/4 밤에도 유효, 5/5 22:00부터 공연 종료")
        void bundleEndsOnLastDay() {
            Ticket pass = TicketFixtures.festivalPass();

            assertThat(pass.display(FESTIVAL_TIMES, true, at(5, 4, 23, 0)).showsQrAndShareButtons()).isTrue();
            assertThat(pass.display(FESTIVAL_TIMES, true, at(5, 5, 21, 59)).showsQrAndShareButtons()).isTrue();
            assertThat(pass.hasAllSessionsEnded(FESTIVAL_TIMES, at(5, 5, 22, 0))).isTrue();
            assertThat(pass.display(FESTIVAL_TIMES, true, at(5, 5, 22, 0)).status()).isEqualTo(TicketDisplayStatus.PERFORMANCE_ENDED);
            assertThat(pass.display(FESTIVAL_TIMES, true, at(5, 5, 22, 0)).showsQrAndShareButtons()).isFalse();
        }

        @Test
        @DisplayName("종료 시각을 비워 둔 회차형 회차는 끝났는지 알 수 없어 유효로 남는다 — 행사 종료도 판정하지 않는다 (docs/04)")
        void unknownEnd() {
            Map<Long, SessionTimes> withoutEnd = Map.of(SESSION_MARCH_14,
                    SessionTimes.timed(LocalDate.of(2026, 3, 14), TimedSessionTime.startingAt(LocalTime.of(19, 0))));

            TicketDisplay display = TicketFixtures.designatedTicket().display(withoutEnd, false, at(3, 20, 12, 0));

            assertThat(display.status()).isEqualTo(TicketDisplayStatus.VALID);
            assertThat(display.showsQrAndShareButtons()).isTrue();
        }

        @Test
        @DisplayName("가리키는 회차의 시각이 빠지면 계산하지 않는다")
        void requiresTimesOfPointedSessions() {
            Ticket pass = TicketFixtures.festivalPass();
            Map<Long, SessionTimes> missingLastDay = Map.of(SESSION_MAY_3, FESTIVAL_TIMES.get(SESSION_MAY_3),
                    SESSION_MAY_4, FESTIVAL_TIMES.get(SESSION_MAY_4));

            assertThatIllegalArgumentException().isThrownBy(() -> pass.display(missingLastDay, false, at(5, 6, 0, 0)));
        }
    }
}
