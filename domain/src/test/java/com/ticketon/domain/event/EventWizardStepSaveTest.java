package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static com.ticketon.domain.event.EventStatusFixtures.ADDRESS;
import static com.ticketon.domain.event.EventStatusFixtures.LAST_MODIFIED_AT;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.policy.CancellationFeeTier;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 위저드 단계 저장 — 단계를 넘길 때마다 저장되고 나갔다 들어오면 이어서 한다(docs/03 "임시저장은 자동으로"). 같은 단계를 여러 번 저장해도
 * 안전하고, 바뀐 항목마다 상태별 수정 범위를 본다(docs/04 "상태별로 할 수 있는 것", "예매가 열린 뒤(판매 중) 수정 범위"). 극단 열두달(docs/15
 * 박정훈)의 봄 정기공연이다.
 */
@DisplayName("위저드 단계 저장 (docs/03 \"행사 만들기 위저드\", docs/04 \"작성 중 행사 관리\", \"상태별로 할 수 있는 것\")")
class EventWizardStepSaveTest {

    /** 1단계를 넘겨 행사를 만든 시각 — 2/1 10:00. */
    private static final Instant CREATED_AT = korean(LocalDate.of(2026, 2, 1), 10, 0);

    /** 다음 단계를 저장한 시각 — 2/3 21:00. */
    private static final Instant SAVED_AT = korean(LocalDate.of(2026, 2, 3), 21, 0);

    /** 그다음 저장 — 2/4 09:00. */
    private static final Instant SAVED_AGAIN_AT = korean(LocalDate.of(2026, 2, 4), 9, 0);

    private static final EventPasswordHash HASH_OF_0315 = new EventPasswordHash("{test}0315");
    private static final EventPasswordHash HASH_OF_0412 = new EventPasswordHash("{test}0412");

    /** "공연 3일 전까지 전액 환불"(docs/08 조정 예시). */
    private static final CancellationPolicy FULL_REFUND_UNTIL_THREE_DAYS_BEFORE = CancellationPolicy.of(List.of(
            CancellationFeeTier.of(3, 0), CancellationFeeTier.of(1, 30), CancellationFeeTier.of(0, 90)));

    private static Event newSpringPlay() {
        return Event.create(TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), CREATED_AT);
    }

    /** 제목만 고친 봄 정기공연 — "단독공연"을 "앵콜공연"으로(docs/03 "제목도 같습니다"). */
    private static EventBasicInformation retitled() {
        EventBasicInformation original = twelveMonthsSpringPlay();
        return new EventBasicInformation(EventTitle.of("2026 봄 앵콜공연 〈열두 번째 달〉"), original.type(), original.category(),
                original.introduction(), original.venue(), original.venueDetails(), original.mapLink(),
                original.publicContact());
    }

    /** 장소를 옮긴 봄 정기공연 — 홍대 소극장에서 강남으로(docs/04 "장소만 승인제인 이유"). 제목도 함께 고쳤다. */
    private static EventBasicInformation retitledAndMoved() {
        EventBasicInformation original = retitled();
        return new EventBasicInformation(original.title(), original.type(), original.category(), original.introduction(),
                new Venue("△△소극장", "서울특별시 강남구 테헤란로 7"), original.venueDetails(), original.mapLink(),
                original.publicContact());
    }

    /** 카테고리를 뮤지컬로 바꾼 봄 정기공연. */
    private static EventBasicInformation recategorized() {
        EventBasicInformation original = twelveMonthsSpringPlay();
        return new EventBasicInformation(original.title(), original.type(),
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "뮤지컬", null), original.introduction(),
                original.venue(), original.venueDetails(), original.mapLink(), original.publicContact());
    }

    /** 상세 안내를 고친 봄 정기공연 — "주차 불가"를 "주차 2시간 무료"로. */
    private static EventBasicInformation withNewVenueDetails() {
        EventBasicInformation original = twelveMonthsSpringPlay();
        return new EventBasicInformation(original.title(), original.type(), original.category(), original.introduction(),
                original.venue(), "지하 1층, 주차 2시간 무료", original.mapLink(), original.publicContact());
    }

    /** 예매 종료만 2시간 전으로 고친 예매 설정 — 나머지는 처음 값. */
    private static BookingSettings closingTwoHoursBefore() {
        BookingSettings defaults = BookingSettings.defaults();
        return new BookingSettings(defaults.bookingOpensAt(), LeadTime.of(Duration.ofHours(2)), defaults.checkinOpenLeadTime(),
                defaults.seatsPerSelection(), defaults.ticketsPerPerson(), defaults.sessionChoice(), defaults.reentry(),
                defaults.ageRestriction());
    }

    /** 만 19세 이상만 받게 고친 예매 설정 — 나머지는 처음 값. */
    private static BookingSettings adultsOnly() {
        BookingSettings defaults = BookingSettings.defaults();
        return new BookingSettings(defaults.bookingOpensAt(), defaults.bookingCloseLeadTime(), defaults.checkinOpenLeadTime(),
                defaults.seatsPerSelection(), defaults.ticketsPerPerson(), defaults.sessionChoice(), defaults.reentry(),
                AgeRestriction.atLeast(19));
    }

    /** 오픈 시각 3/1 14:00, 1회 6석, 날짜별 4매, 하루에 한 회차만, 재입장 켬으로 고친 예매 설정. */
    private static BookingSettings adjustedBookingSettings() {
        return new BookingSettings(korean(LocalDate.of(2026, 3, 1), 14, 0), LeadTime.of(Duration.ofHours(1)),
                LeadTime.of(Duration.ofMinutes(90)), SeatsPerSelectionLimit.of(6),
                TicketsPerPersonLimit.of(4, TicketsPerPersonBasis.PER_DATE), SessionChoiceRestriction.ONE_SESSION_PER_DATE,
                ReentrySetting.ALLOWED, AgeRestriction.none());
    }

    private static boolean saveDefaultBookingStep(Event event, Instant now) {
        return event.saveBookingStep(EventVisibility.PUBLIC, null, BookingSettings.defaults(), CancellationPolicy.standard(),
                now);
    }

    /** 판매를 시작한 뒤의 그 상태 행사인데 진행 단계는 1단계에 머물러 있다 — 판매를 시작한 뒤에는 진행 단계가 움직이지 않는지 보려고. */
    private static Event startedEventStillAtFirstStep() {
        return Event.restore(EventStatusFixtures.EVENT_ID, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), null,
                EventVisibilitySetting.publicByDefault(), BookingSettings.defaults(), CancellationPolicy.standard(),
                EventStatus.AWAITING_OPEN, null, List.of(), EventWizardStep.BASIC_INFORMATION, LAST_MODIFIED_AT);
    }

    @Nested
    @DisplayName("1단계 — 기본 정보")
    class BasicInformationStep {

        @Test
        @DisplayName("같은 기본 정보를 다시 저장하면 아무것도 바꾸지 않고 false — 마지막 수정 시각도 그대로다")
        void sameValuesChangeNothing() {
            Event event = newSpringPlay();

            assertThat(event.saveBasicInformationStep(twelveMonthsSpringPlay(), SAVED_AT)).isFalse();
            assertThat(event.saveBasicInformationStep(twelveMonthsSpringPlay(), SAVED_AGAIN_AT)).isFalse();

            assertThat(event.basicInformation()).isEqualTo(twelveMonthsSpringPlay());
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
            assertThat(event.lastModifiedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("제목을 고치면 true — 기본 정보가 바뀌고 마지막 수정 시각이 저장한 시각이 된다")
        void changedTitleIsSaved() {
            Event event = newSpringPlay();

            assertThat(event.saveBasicInformationStep(retitled(), SAVED_AT)).isTrue();

            assertThat(event.basicInformation()).isEqualTo(retitled());
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("5단계까지 저장한 뒤 1단계로 돌아가 고쳐도 진행 단계는 5단계에서 내려가지 않는다")
        void progressDoesNotGoBack() {
            Event event = newSpringPlay();
            saveDefaultBookingStep(event, SAVED_AT);

            assertThat(event.saveBasicInformationStep(retitled(), SAVED_AGAIN_AT)).isTrue();

            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BOOKING_SETTINGS);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AGAIN_AT);
        }

        @Test
        @DisplayName("작성 중 · 판매 대기는 전부 고친다 — 장소 · 유형 · 카테고리 · 상세 안내도")
        void draftAndAwaitingOpenEditEverything() {
            for (Event event : List.of(newSpringPlay(), springPlayIn(EventStatus.AWAITING_OPEN))) {
                assertThat(event.saveBasicInformationStep(retitledAndMoved(), SAVED_AT)).isTrue();
                assertThat(event.saveBasicInformationStep(recategorized(), SAVED_AT)).isTrue();
                assertThat(event.saveBasicInformationStep(withNewVenueDetails(), SAVED_AT)).isTrue();
            }
        }

        @Test
        @DisplayName("예매가 열린 뒤 제목만 고치면 장소를 그대로 보내도 저장된다 — 바뀌지 않은 항목은 보지 않는다")
        void titleOnlyAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            assertThat(event.saveBasicInformationStep(retitled(), SAVED_AT)).isTrue();

            assertThat(event.basicInformation().title().value()).isEqualTo("2026 봄 앵콜공연 〈열두 번째 달〉");
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("예매가 열린 뒤 장소를 바꾸면 관리자 승인 안내로 막고, 함께 보낸 제목도 바꾸지 않는다")
        void venueAfterBookingOpenedNeedsApproval() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            assertRejected(() -> event.saveBasicInformationStep(retitledAndMoved(), SAVED_AT),
                    EventErrorCode.VENUE_CHANGE_NEEDS_ADMIN_APPROVAL);

            assertThat(event.basicInformation()).isEqualTo(twelveMonthsSpringPlay());
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("확인 필요 (#38) — 예매가 열린 뒤 유형 · 카테고리 · 상세 안내는 표에 없어 막는다")
        void unlistedItemsAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            assertRejected(() -> event.saveBasicInformationStep(recategorized(), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_BOOKING_OPENED);
            assertRejected(() -> event.saveBasicInformationStep(withNewVenueDetails(), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_BOOKING_OPENED);
            assertThat(event.basicInformation()).isEqualTo(twelveMonthsSpringPlay());
        }

        @Test
        @DisplayName("판매가 끝나면 제목도 고칠 수 없고, 지난 행사는 전부 고정이다 — 같은 값을 다시 보내는 것은 막지 않는다")
        void lockedAfterSalesClosed() {
            Event salesClosed = springPlayIn(EventStatus.SALES_CLOSED);
            Event ended = springPlayIn(EventStatus.ENDED);

            assertRejected(() -> salesClosed.saveBasicInformationStep(retitled(), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> ended.saveBasicInformationStep(retitled(), SAVED_AT), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
            assertThat(salesClosed.saveBasicInformationStep(twelveMonthsSpringPlay(), SAVED_AT)).isFalse();
            assertThat(ended.saveBasicInformationStep(twelveMonthsSpringPlay(), SAVED_AT)).isFalse();
        }
    }

    @Nested
    @DisplayName("5단계 — 공개 수준 · 예매 설정 · 취소 규정")
    class BookingStep {

        @Test
        @DisplayName("건너뛰면 처음 값 그대로다 — 공개, 1회 4석, 예매 종료 · 검표 시작 1시간 전, 재입장 끔, 연령 제한 없음, 취소 규정은 기준표")
        void skippedStepKeepsDefaults() {
            Event event = newSpringPlay();

            assertThat(event.visibility()).isEqualTo(EventVisibilitySetting.publicByDefault());
            assertThat(event.bookingSettings().bookingOpensAt()).isNull();
            assertThat(event.bookingSettings().seatsPerSelection().maxSeats()).isEqualTo(4);
            assertThat(event.bookingSettings().bookingCloseLeadTime().duration()).isEqualTo(Duration.ofHours(1));
            assertThat(event.bookingSettings().checkinOpenLeadTime().duration()).isEqualTo(Duration.ofHours(1));
            assertThat(event.bookingSettings().ticketsPerPerson().isLimited()).isFalse();
            assertThat(event.bookingSettings().sessionChoice()).isEqualTo(SessionChoiceRestriction.NONE);
            assertThat(event.bookingSettings().reentry()).isEqualTo(ReentrySetting.NOT_ALLOWED);
            assertThat(event.bookingSettings().ageRestriction().isRestricted()).isFalse();
            assertThat(event.cancellationPolicy().isStandard()).isTrue();
        }

        @Test
        @DisplayName("처음 값 그대로 넘겨도 진행 단계는 5단계로 올라 true — 값은 그대로이고 마지막 수정 시각이 바뀐다")
        void defaultsSaveProgresses() {
            Event event = newSpringPlay();

            assertThat(saveDefaultBookingStep(event, SAVED_AT)).isTrue();

            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BOOKING_SETTINGS);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
            assertThat(event.bookingSettings()).isEqualTo(BookingSettings.defaults());
            assertThat(event.cancellationPolicy()).isEqualTo(CancellationPolicy.standard());
        }

        @Test
        @DisplayName("같은 5단계를 다시 저장하면 아무것도 바꾸지 않고 false — 마지막 수정 시각도 그대로다")
        void sameValuesChangeNothing() {
            Event event = newSpringPlay();
            event.saveBookingStep(EventVisibility.LINK_ONLY, null, adjustedBookingSettings(), FULL_REFUND_UNTIL_THREE_DAYS_BEFORE,
                    SAVED_AT);

            assertThat(event.saveBookingStep(EventVisibility.LINK_ONLY, null, adjustedBookingSettings(),
                    FULL_REFUND_UNTIL_THREE_DAYS_BEFORE, SAVED_AGAIN_AT)).isFalse();

            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("조정한 공개 수준 · 예매 설정 · 취소 규정을 함께 저장한다")
        void savesAdjustedValues() {
            Event event = newSpringPlay();

            assertThat(event.saveBookingStep(EventVisibility.LINK_ONLY, null, adjustedBookingSettings(),
                    FULL_REFUND_UNTIL_THREE_DAYS_BEFORE, SAVED_AT)).isTrue();

            assertThat(event.visibility().visibility()).isEqualTo(EventVisibility.LINK_ONLY);
            assertThat(event.bookingSettings()).isEqualTo(adjustedBookingSettings());
            assertThat(event.cancellationPolicy()).isEqualTo(FULL_REFUND_UNTIL_THREE_DAYS_BEFORE);
        }

        @Test
        @DisplayName("암호 보호 — 새 암호를 적으면 걸고, 다시 저장할 때 적지 않으면 걸려 있던 암호를 그대로 써서 바뀐 것이 없다")
        void passwordProtection() {
            Event event = newSpringPlay();
            event.saveBookingStep(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315, BookingSettings.defaults(),
                    CancellationPolicy.standard(), SAVED_AT);

            assertThat(event.saveBookingStep(EventVisibility.PASSWORD_PROTECTED, null, BookingSettings.defaults(),
                    CancellationPolicy.standard(), SAVED_AGAIN_AT)).isFalse();
            assertThat(event.visibility().passwordHash()).isEqualTo(HASH_OF_0315);

            assertThat(event.saveBookingStep(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0412, BookingSettings.defaults(),
                    CancellationPolicy.standard(), SAVED_AGAIN_AT)).isTrue();
            assertThat(event.visibility().passwordHash()).isEqualTo(HASH_OF_0412);
        }

        @Test
        @DisplayName("암호가 걸린 적 없이 암호 보호로 두면서 암호를 적지 않으면 막고, 함께 보낸 다른 칸도 바꾸지 않는다")
        void passwordRequired() {
            Event event = newSpringPlay();

            assertRejected(() -> event.saveBookingStep(EventVisibility.PASSWORD_PROTECTED, null, adjustedBookingSettings(),
                    CancellationPolicy.standard(), SAVED_AT), EventErrorCode.EVENT_PASSWORD_REQUIRED);

            assertThat(event.bookingSettings()).isEqualTo(BookingSettings.defaults());
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
        }

        @Test
        @DisplayName("암호 보호가 아닌데 암호 해시를 넘기면 받지 않는다")
        void hashOnlyForPasswordProtection() {
            Event event = newSpringPlay();

            assertThatIllegalArgumentException().isThrownBy(() -> event.saveBookingStep(EventVisibility.LINK_ONLY, HASH_OF_0315,
                    BookingSettings.defaults(), CancellationPolicy.standard(), SAVED_AT));
        }

        @Test
        @DisplayName("예매가 열린 뒤 예매 종료 · 공개 수준은 바꾸고, 취소 규정은 그대로 보내면 막지 않는다")
        void bookingCloseAndVisibilityAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE, BookingSettings.defaults());

            assertThat(event.saveBookingStep(EventVisibility.LINK_ONLY, null, closingTwoHoursBefore(),
                    CancellationPolicy.standard(), SAVED_AT)).isTrue();

            assertThat(event.bookingSettings().bookingCloseLeadTime().duration()).isEqualTo(Duration.ofHours(2));
            assertThat(event.visibility().visibility()).isEqualTo(EventVisibility.LINK_ONLY);
        }

        @Test
        @DisplayName("예매가 열린 뒤 취소 규정을 바꾸면 막고, 함께 보낸 예매 종료도 바꾸지 않는다 — 예매 시점 규정이 따라다닌다")
        void cancellationPolicyLockedAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE, BookingSettings.defaults());

            assertRejected(() -> event.saveBookingStep(EventVisibility.PUBLIC, null, closingTwoHoursBefore(),
                    FULL_REFUND_UNTIL_THREE_DAYS_BEFORE, SAVED_AT), EventErrorCode.EDIT_LOCKED_AFTER_BOOKING_OPENED);

            assertThat(event.bookingSettings()).isEqualTo(BookingSettings.defaults());
            assertThat(event.cancellationPolicy()).isEqualTo(CancellationPolicy.standard());
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("확인 필요 (#38) — 예매가 열린 뒤 예매 종료 밖의 예매 설정(연령 제한 등)은 막는다")
        void otherBookingSettingsLockedAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE, BookingSettings.defaults());

            assertRejected(() -> event.saveBookingStep(EventVisibility.PUBLIC, null, adultsOnly(), CancellationPolicy.standard(),
                    SAVED_AT), EventErrorCode.EDIT_LOCKED_AFTER_BOOKING_OPENED);
        }

        @Test
        @DisplayName("판매 대기는 예매가 없어 취소 규정도 바꾼다")
        void awaitingOpenChangesCancellationPolicy() {
            Event event = springPlayIn(EventStatus.AWAITING_OPEN, BookingSettings.defaults());

            assertThat(event.saveBookingStep(EventVisibility.PUBLIC, null, BookingSettings.defaults(),
                    FULL_REFUND_UNTIL_THREE_DAYS_BEFORE, SAVED_AT)).isTrue();
        }

        @Test
        @DisplayName("판매가 끝나면 공개 수준이 고정된다 (docs/03 \"전환 — 판매 종료 전까지 가능\")")
        void visibilityLockedAfterSalesClosed() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED, BookingSettings.defaults());

            assertRejected(() -> event.saveBookingStep(EventVisibility.LINK_ONLY, null, BookingSettings.defaults(),
                    CancellationPolicy.standard(), SAVED_AT), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        }
    }

    @Nested
    @DisplayName("진행 단계")
    class Progress {

        @Test
        @DisplayName("판매를 시작한 뒤에는 진행 단계가 움직이지 않는다 — 같은 값을 저장하면 바뀐 것이 없다")
        void progressFrozenAfterSalesStarted() {
            Event event = startedEventStillAtFirstStep();

            assertThat(saveDefaultBookingStep(event, SAVED_AT)).isFalse();

            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("위저드는 7단계이고, 단계 번호는 1부터 차례다 — 진행률 \"4/7 단계\"의 분모와 분자")
        void stepNumbers() {
            assertThat(EventWizardStep.stepCount()).isEqualTo(7);
            assertThat(EventWizardStep.BASIC_INFORMATION.number()).isEqualTo(1);
            assertThat(EventWizardStep.PRICING.number()).isEqualTo(4);
            assertThat(EventWizardStep.BOOKING_SETTINGS.number()).isEqualTo(5);
            assertThat(EventWizardStep.REVIEW.number()).isEqualTo(7);
            assertThat(EventWizardStep.BOOKING_SETTINGS.isAfter(EventWizardStep.BASIC_INFORMATION)).isTrue();
            assertThat(EventWizardStep.BASIC_INFORMATION.isAfter(EventWizardStep.BOOKING_SETTINGS)).isFalse();
            assertThat(EventWizardStep.BOOKING_SETTINGS.isAfter(EventWizardStep.BOOKING_SETTINGS)).isFalse();
        }
    }

    @Nested
    @DisplayName("바뀐 수정 항목 나누기")
    class ChangedEditTargets {

        @Test
        @DisplayName("기본 정보 — 제목 · 소개, 유형 · 카테고리, 장소, 상세 안내 · 지도 링크 · 공개용 연락처로 나눈다. 같으면 비어 있다")
        void basicInformation() {
            EventBasicInformation original = twelveMonthsSpringPlay();
            EventBasicInformation newMapLink = new EventBasicInformation(original.title(), original.type(), original.category(),
                    original.introduction(), original.venue(), original.venueDetails(),
                    ExternalLink.of("https://map.example/place/99"), original.publicContact());
            EventBasicInformation newIntroduction = new EventBasicInformation(original.title(), original.type(),
                    original.category(), "새 소개입니다.", original.venue(), original.venueDetails(), original.mapLink(),
                    original.publicContact());

            assertThat(original.changedEditTargets(twelveMonthsSpringPlay())).isEmpty();
            assertThat(original.changedEditTargets(retitled())).containsExactly(EventEditTarget.TITLE_POSTER_DESCRIPTION);
            assertThat(original.changedEditTargets(newIntroduction)).containsExactly(EventEditTarget.TITLE_POSTER_DESCRIPTION);
            assertThat(original.changedEditTargets(recategorized())).containsExactly(EventEditTarget.TYPE_AND_CATEGORY);
            assertThat(original.changedEditTargets(retitledAndMoved()))
                    .containsExactlyInAnyOrder(EventEditTarget.TITLE_POSTER_DESCRIPTION, EventEditTarget.VENUE);
            assertThat(original.changedEditTargets(withNewVenueDetails())).containsExactly(EventEditTarget.VENUE_GUIDE_AND_CONTACT);
            assertThat(original.changedEditTargets(newMapLink)).containsExactly(EventEditTarget.VENUE_GUIDE_AND_CONTACT);
        }

        @Test
        @DisplayName("예매 설정 — 예매 종료와 그 밖의 칸으로 나눈다. 같으면 비어 있다")
        void bookingSettings() {
            BookingSettings defaults = BookingSettings.defaults();

            assertThat(defaults.changedEditTargets(BookingSettings.defaults())).isEmpty();
            assertThat(defaults.changedEditTargets(closingTwoHoursBefore())).containsExactly(EventEditTarget.BOOKING_CLOSE_TIME);
            assertThat(defaults.changedEditTargets(adultsOnly())).containsExactly(EventEditTarget.OTHER_BOOKING_SETTINGS);
            assertThat(closingTwoHoursBefore().changedEditTargets(adultsOnly()))
                    .containsExactlyInAnyOrder(EventEditTarget.BOOKING_CLOSE_TIME, EventEditTarget.OTHER_BOOKING_SETTINGS);
            assertThat(defaults.changedEditTargets(adjustedBookingSettings()))
                    .containsExactly(EventEditTarget.OTHER_BOOKING_SETTINGS);
        }
    }
}
