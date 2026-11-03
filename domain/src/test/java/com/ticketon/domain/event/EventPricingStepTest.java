package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static com.ticketon.domain.event.EventStatusFixtures.ADDRESS;
import static com.ticketon.domain.event.EventStatusFixtures.EVENT_ID;
import static com.ticketon.domain.event.EventStatusFixtures.LAST_MODIFIED_AT;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 위저드 4단계(가격 · 조기예매 · 쿠폰 · 묶음권)를 저장할 때 행사가 맡는 것 — 가격의 수정 범위와 진행 단계 · 마지막 수정 시각(docs/03 "행사 만들기
 * 위저드", docs/04 "예매가 열린 뒤(판매 중) 수정 범위 — 가격"). 극단 열두달(docs/15 박정훈)의 봄 정기공연.
 */
@DisplayName("위저드 4단계 저장 — 가격의 수정 범위와 진행 단계 (docs/03 \"행사 만들기 위저드\", docs/04 \"가격\")")
class EventPricingStepTest {

    /** 4단계에서 조기예매를 저장한 시각 — 2/14 20:00. */
    private static final Instant SAVED_AT = korean(LocalDate.of(2026, 2, 14), 20, 0);

    /** 3단계까지 저장한 작성 중 봄 정기공연. */
    private static Event draftAtSeatingStep() {
        return Event.restore(EVENT_ID, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), null,
                EventVisibilitySetting.publicByDefault(), BookingSettings.defaults(), CancellationPolicy.standard(),
                EventStatus.DRAFT, null, List.of(), EventWizardStep.SEATING, LAST_MODIFIED_AT);
    }

    @Test
    @DisplayName("작성 중에 조기예매를 더하면 진행 단계가 4단계까지 오르고(\"4/7 단계 · 가격 설정까지 완료\") 마지막 수정 시각이 바뀐다")
    void draftProgressesToPricingStep() {
        Event event = draftAtSeatingStep();

        assertThat(event.savePricingStep(true, SAVED_AT)).isTrue();

        assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.PRICING);
        assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
    }

    @Test
    @DisplayName("같은 값을 다시 저장해 바뀐 것이 없으면 수정 범위를 보지 않고, 진행 단계만 오르면 바뀐 것이다")
    void unchangedPricingSkipsEditScope() {
        Event pastEvent = springPlayIn(EventStatus.ENDED);

        assertThat(pastEvent.savePricingStep(false, SAVED_AT)).isFalse();
        assertThat(pastEvent.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);

        Event draft = draftAtSeatingStep();
        assertThat(draft.savePricingStep(false, SAVED_AT)).isTrue();
        assertThat(draft.wizardProgress()).isEqualTo(EventWizardStep.PRICING);
    }

    @Test
    @DisplayName("앞 단계를 다시 저장해도 진행 단계는 내려가지 않는다 — 5단계까지 저장한 행사는 5단계 그대로다")
    void progressDoesNotGoBack() {
        Event event = Event.restore(EVENT_ID, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), null,
                EventVisibilitySetting.publicByDefault(), BookingSettings.defaults(), CancellationPolicy.standard(),
                EventStatus.DRAFT, null, List.of(), EventWizardStep.BOOKING_SETTINGS, LAST_MODIFIED_AT);

        event.savePricingStep(true, SAVED_AT);

        assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BOOKING_SETTINGS);
    }

    @Test
    @DisplayName("예매가 열린 뒤(판매 중 · 판매 중단)에도 가격은 고친다 — 신규 예매부터 적용, 진행 단계는 움직이지 않는다")
    void pricingEditableAfterBookingOpened() {
        Event onSale = springPlayIn(EventStatus.ON_SALE);
        Event paused = springPlayIn(EventStatus.SALES_PAUSED);

        assertThat(onSale.savePricingStep(true, SAVED_AT)).isTrue();
        assertThat(paused.savePricingStep(true, SAVED_AT)).isTrue();

        assertThat(onSale.wizardProgress()).isEqualTo(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
        assertThat(onSale.lastModifiedAt()).isEqualTo(SAVED_AT);
    }

    @Test
    @DisplayName("판매가 끝나면 가격은 고정이다 — 판매 종료는 EDIT_LOCKED_AFTER_SALES_CLOSED, 행사 종료 뒤는 EDIT_LOCKED_FOR_PAST_EVENT")
    void pricingLockedAfterSalesClosed() {
        assertRejected(() -> springPlayIn(EventStatus.SALES_CLOSED).savePricingStep(true, SAVED_AT),
                EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> springPlayIn(EventStatus.ENDED).savePricingStep(true, SAVED_AT), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
    }

    @Test
    @DisplayName("시각은 꼭 받는다")
    void requiresNow() {
        assertThatNullPointerException().isThrownBy(() -> draftAtSeatingStep().savePricingStep(true, null));
    }
}
