package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("예매 설정 (docs/03 \"행사 만들기 위저드\" 5단계, docs/06 \"매수 제한\" · \"예매 오픈\" · \"예매 종료\")")
class BookingSettingsTest {

    private static Instant koreanInstant(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    @Nested
    @DisplayName("처음 값 — 5단계를 건너뛰면")
    class Defaults {

        @Test
        @DisplayName("오픈 시각 없음 · 예매 종료와 검표 시작은 1시간 전 · 1회 4석 · 1인 총 매수 제한 없음 · 회차 제약 없음 · 재입장 끔 · 연령 제한 없음")
        void defaults() {
            BookingSettings defaults = BookingSettings.defaults();

            assertThat(defaults.bookingOpensAt()).isNull();
            assertThat(defaults.bookingCloseLeadTime()).isEqualTo(LeadTime.of(Duration.ofHours(1)));
            assertThat(defaults.checkinOpenLeadTime()).isEqualTo(LeadTime.of(Duration.ofHours(1)));
            assertThat(defaults.seatsPerSelection()).isEqualTo(SeatsPerSelectionLimit.of(4));
            assertThat(defaults.ticketsPerPerson()).isEqualTo(TicketsPerPersonLimit.unlimited());
            assertThat(defaults.sessionChoice()).isEqualTo(SessionChoiceRestriction.NONE);
            assertThat(defaults.reentry()).isEqualTo(ReentrySetting.NOT_ALLOWED);
            assertThat(defaults.ageRestriction()).isEqualTo(AgeRestriction.none());
        }

        @Test
        @DisplayName("오픈 시각 말고는 비울 수 없다")
        void onlyOpeningTimeMayBeEmpty() {
            BookingSettings defaults = BookingSettings.defaults();

            assertThatThrownBy(() -> new BookingSettings(null, null, defaults.checkinOpenLeadTime(), defaults.seatsPerSelection(),
                    defaults.ticketsPerPerson(), defaults.sessionChoice(), defaults.reentry(), defaults.ageRestriction()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new BookingSettings(null, defaults.bookingCloseLeadTime(), defaults.checkinOpenLeadTime(),
                    defaults.seatsPerSelection(), defaults.ticketsPerPerson(), defaults.sessionChoice(), null,
                    defaults.ageRestriction()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("오픈 시각은 행사 단위로 하나 — 정해 두면 그대로 갖는다")
        void openingTimePerEvent() {
            BookingSettings defaults = BookingSettings.defaults();
            Instant opensAt = koreanInstant(2026, 3, 1, 14, 0);

            BookingSettings settings = new BookingSettings(opensAt, defaults.bookingCloseLeadTime(), defaults.checkinOpenLeadTime(),
                    defaults.seatsPerSelection(), defaults.ticketsPerPerson(), defaults.sessionChoice(), defaults.reentry(),
                    defaults.ageRestriction());

            assertThat(settings.bookingOpensAt()).isEqualTo(opensAt);
            assertThat(settings).isNotEqualTo(defaults);
        }
    }

    @Nested
    @DisplayName("예매 종료 · 검표 시작 — 기준 시각보다 앞서는 시간")
    class LeadTimes {

        @Test
        @DisplayName("19:00 회차는 처음 값으로 18:00에 예매를 닫고 18:00부터 검표를 연다 (docs/04, docs/10 예시)")
        void oneHourBeforeSevenPm() {
            Instant sessionStart = koreanInstant(2026, 3, 14, 19, 0);
            BookingSettings defaults = BookingSettings.defaults();

            assertThat(defaults.bookingCloseLeadTime().before(sessionStart)).isEqualTo(koreanInstant(2026, 3, 14, 18, 0));
            assertThat(defaults.checkinOpenLeadTime().before(sessionStart)).isEqualTo(koreanInstant(2026, 3, 14, 18, 0));
        }

        @Test
        @DisplayName("10시 오픈 종일형은 9시부터 검표가 열린다 (docs/03 예시)")
        void allDayCheckinOpensAtNine() {
            Instant operatingStart = koreanInstant(2026, 5, 3, 10, 0);

            assertThat(BookingSettings.defaults().checkinOpenLeadTime().before(operatingStart))
                    .isEqualTo(koreanInstant(2026, 5, 3, 9, 0));
        }

        @Test
        @DisplayName("주최자가 조정한다 — 0이면 기준 시각까지, 3시간이면 3시간 전")
        void adjustable() {
            Instant sessionStart = koreanInstant(2026, 3, 14, 19, 0);

            assertThat(LeadTime.of(Duration.ZERO).before(sessionStart)).isEqualTo(sessionStart);
            assertThat(LeadTime.of(Duration.ofHours(3)).before(sessionStart)).isEqualTo(koreanInstant(2026, 3, 14, 16, 0));
        }

        @Test
        @DisplayName("기준 시각보다 늦게(음수) 둘 수 없다")
        void negativeRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> LeadTime.of(Duration.ofMinutes(-1)));
            assertThatThrownBy(() -> LeadTime.of(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("1회 선택 매수 — 주최자 입력 + 비율 피드백")
    class SeatsPerSelection {

        @Test
        @DisplayName("처음에는 4석을 채워 둔다")
        void defaultFourSeats() {
            assertThat(SeatsPerSelectionLimit.defaultLimit().maxSeats()).isEqualTo(4);
        }

        @Test
        @DisplayName("4석은 전체 200석의 2% — 경고 없음 (docs/06 예시)")
        void fourOfTwoHundred() {
            SeatsPerSelectionFeedback feedback = SeatsPerSelectionLimit.of(4).feedbackFor(200);

            assertThat(feedback.percentOfTotal()).hasValue(2);
            assertThat(feedback.isWarning()).isFalse();
        }

        @Test
        @DisplayName("8석은 전체 50석의 16% — 10%를 넘어 경고 (docs/06 예시)")
        void eightOfFifty() {
            SeatsPerSelectionFeedback feedback = SeatsPerSelectionLimit.of(8).feedbackFor(50);

            assertThat(feedback.percentOfTotal()).hasValue(16);
            assertThat(feedback.exceedsSeatCount()).isFalse();
            assertThat(feedback.exceedsShareOfTotal()).isTrue();
            assertThat(feedback.isWarning()).isTrue();
        }

        @Test
        @DisplayName("8석은 경고가 아니고 9석부터 좌석 수 경고 — 비율이 작아도")
        void seatCountLine() {
            assertThat(SeatsPerSelectionLimit.of(8).feedbackFor(1000).isWarning()).isFalse();

            SeatsPerSelectionFeedback nine = SeatsPerSelectionLimit.of(9).feedbackFor(1000);
            assertThat(nine.exceedsSeatCount()).isTrue();
            assertThat(nine.exceedsShareOfTotal()).isFalse();
            assertThat(nine.isWarning()).isTrue();
        }

        @Test
        @DisplayName("딱 10%는 경고가 아니고 넘으면 경고 — 8석/80석은 10%, 9석/80석은 11.25%")
        void shareLine() {
            SeatsPerSelectionFeedback exactlyTenPercent = SeatsPerSelectionLimit.of(8).feedbackFor(80);
            SeatsPerSelectionFeedback overTenPercent = SeatsPerSelectionLimit.of(9).feedbackFor(80);

            assertThat(exactlyTenPercent.percentOfTotal()).hasValue(10);
            assertThat(exactlyTenPercent.isWarning()).isFalse();
            assertThat(overTenPercent.exceedsShareOfTotal()).isTrue();
        }

        @Test
        @DisplayName("비율은 올림한 정수 퍼센트 — 경고선을 넘은 비율이 10%로 보이지 않게")
        void percentRoundedUp() {
            assertThat(SeatsPerSelectionLimit.of(3).feedbackFor(200).percentOfTotal()).hasValue(2);
            assertThat(SeatsPerSelectionLimit.of(1).feedbackFor(1000).percentOfTotal()).hasValue(1);

            SeatsPerSelectionFeedback justOver = SeatsPerSelectionLimit.of(8).feedbackFor(79);
            assertThat(justOver.percentOfTotal()).hasValue(11);
            assertThat(justOver.isWarning()).isTrue();
        }

        @Test
        @DisplayName("전체보다 많이 정해도 막지 않는다 — 비율이 100%를 넘고 경고")
        void moreThanTotalNotBlocked() {
            SeatsPerSelectionFeedback feedback = SeatsPerSelectionLimit.of(10).feedbackFor(5);

            assertThat(feedback.percentOfTotal()).hasValue(200);
            assertThat(feedback.isWarning()).isTrue();
        }

        @Test
        @DisplayName("아직 좌석이 없으면 비율 없이 좌석 수 경고만 본다")
        void noSeatsYet() {
            assertThat(SeatsPerSelectionLimit.of(4).feedbackFor(0).percentOfTotal()).isEmpty();
            assertThat(SeatsPerSelectionLimit.of(4).feedbackFor(0).isWarning()).isFalse();
            assertThat(SeatsPerSelectionLimit.of(9).feedbackFor(0).isWarning()).isTrue();
        }

        @Test
        @DisplayName("1석 미만이나 음수 전체 좌석은 받지 않는다")
        void invalidValues() {
            assertThatIllegalArgumentException().isThrownBy(() -> SeatsPerSelectionLimit.of(0));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatsPerSelectionLimit.of(4).feedbackFor(-1));
        }
    }

    @Nested
    @DisplayName("1인 총 매수 — 매수와 기준")
    class TicketsPerPerson {

        @Test
        @DisplayName("처음 값은 제한 없음이다 (제안값 — 확인 필요 #35)")
        void defaultUnlimited() {
            TicketsPerPersonLimit limit = TicketsPerPersonLimit.defaultLimit();

            assertThat(limit.isLimited()).isFalse();
            assertThat(limit.maximumTicketsInEvent(4)).isEmpty();
        }

        @Test
        @DisplayName("8회차(4일 × 2회) 공연에서 4매 제한이면 날짜별 최대 16매, 행사 전체 4매 (docs/06 예시)")
        void perDateOrEventTotal() {
            TicketsPerPersonLimit perDate = TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.PER_DATE);
            TicketsPerPersonLimit eventTotal = TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.EVENT_TOTAL);
            int dateCount = 4;

            assertThat(perDate.isLimited()).isTrue();
            assertThat(perDate.maximumTicketsInEvent(dateCount)).hasValue(16);
            assertThat(eventTotal.maximumTicketsInEvent(dateCount)).hasValue(4);
        }

        @Test
        @DisplayName("날짜가 하나뿐이면 기준을 고르는 칸을 보여 주지 않는다 — 둘이 같은 값")
        void basisChoiceHiddenForSingleDate() {
            assertThat(TicketsPerPersonBasis.isChoiceShown(0)).isFalse();
            assertThat(TicketsPerPersonBasis.isChoiceShown(1)).isFalse();
            assertThat(TicketsPerPersonBasis.isChoiceShown(2)).isTrue();
            assertThat(TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.PER_DATE).maximumTicketsInEvent(1))
                    .isEqualTo(TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.EVENT_TOTAL).maximumTicketsInEvent(1));
        }

        @Test
        @DisplayName("한도는 1장 이상이고 기준이 있어야 한다")
        void invalidValues() {
            assertThatIllegalArgumentException().isThrownBy(() -> TicketsPerPersonLimit.of(0, TicketsPerPersonBasis.PER_DATE));
            assertThatThrownBy(() -> TicketsPerPersonLimit.of(4, null)).isInstanceOf(NullPointerException.class);
            assertThatIllegalArgumentException().isThrownBy(() -> TicketsPerPersonBasis.isChoiceShown(-1));
            assertThatIllegalArgumentException().isThrownBy(() -> TicketsPerPersonLimit.unlimited().maximumTicketsInEvent(-1));
        }
    }

    @Nested
    @DisplayName("회차 선택 제약")
    class SessionChoice {

        @Test
        @DisplayName("처음 값은 제한 없음이고, 하루에 한 회차만 · 행사 전체에서 한 회차만을 고를 수 있다")
        void choices() {
            assertThat(SessionChoiceRestriction.defaultRestriction()).isEqualTo(SessionChoiceRestriction.NONE);
            assertThat(SessionChoiceRestriction.values()).containsExactly(SessionChoiceRestriction.NONE,
                    SessionChoiceRestriction.ONE_SESSION_PER_DATE, SessionChoiceRestriction.ONE_SESSION_PER_EVENT);
        }
    }

    @Nested
    @DisplayName("재입장")
    class Reentry {

        @Test
        @DisplayName("처음 값은 끔이고 경고가 없다")
        void defaultOff() {
            ReentrySetting setting = ReentrySetting.defaultSetting();

            assertThat(setting).isEqualTo(ReentrySetting.NOT_ALLOWED);
            assertThat(setting.isAllowed()).isFalse();
            assertThat(setting.organizerWarning()).isEmpty();
        }

        @Test
        @DisplayName("켜면 문서의 경고를 보여 준다")
        void warningWhenAllowed() {
            assertThat(ReentrySetting.ALLOWED.isAllowed()).isTrue();
            assertThat(ReentrySetting.ALLOWED.organizerWarning()).hasValue(
                    "켜면 같은 QR로 여러 번 입장할 수 있습니다. 티켓 이미지를 공유해 여러 명이 들어오는 것을 막을 수 없으니, "
                            + "팔찌나 도장 같은 현장 수단을 함께 쓰시는 걸 권합니다.");
        }
    }

    @Nested
    @DisplayName("연령 제한")
    class Age {

        @Test
        @DisplayName("처음 값은 없음이고 안내가 없다")
        void defaultNone() {
            AgeRestriction none = AgeRestriction.none();

            assertThat(none.isRestricted()).isFalse();
            assertThat(none.requiredAge()).isEmpty();
            assertThat(none.organizerNotice()).isEmpty();
        }

        @Test
        @DisplayName("만 19세 이상으로 걸면 기준 나이 19이고, 생년월일이 자기 신고라는 안내를 보여 준다 (docs/03 예시)")
        void nineteenOrOlder() {
            AgeRestriction adultsOnly = AgeRestriction.atLeast(19);

            assertThat(adultsOnly.isRestricted()).isTrue();
            assertThat(adultsOnly.requiredAge()).hasValue(19);
            assertThat(adultsOnly.organizerNotice())
                    .hasValue("연령 정보는 본인이 입력한 값이며 검증되지 않습니다. 실제 확인은 현장 신분증으로 해주세요.");
        }

        @Test
        @DisplayName("기준 나이는 1 이상 — 없애려면 없음으로 둔다")
        void invalidAge() {
            assertThatIllegalArgumentException().isThrownBy(() -> AgeRestriction.atLeast(0));
            assertThatIllegalArgumentException().isThrownBy(() -> new AgeRestriction(-1));
            assertThat(new AgeRestriction(0)).isEqualTo(AgeRestriction.none());
        }
    }
}
