package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("작성 중 행사 관리 — 30일 미수정 알림 · 접어둠 · 이어서 작성 (docs/04 \"작성 중 행사 관리\")")
class DraftEventNudgesTest {

    /** ○○밴드 단독공연을 마지막으로 고친 때 — 9/14 15:00(한국 시각). */
    private static final Instant LAST_MODIFIED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 9, 14, 15, 0));

    @Nested
    @DisplayName("30일 미수정 알림")
    class IdleNotice {

        @Test
        @DisplayName("9/14 15:00에 마지막으로 고쳤으면 30일 뒤인 10/14 15:00부터 알릴 때다 — 29일째인 10/13 15:00에는 아니다")
        void thirtyDaysButNotTwentyNine() {
            Instant twentyNineDaysLater = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 13, 15, 0));
            Instant thirtyDaysLater = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 14, 15, 0));

            assertThat(DraftEventNudges.isIdleNoticeDue(LAST_MODIFIED_AT, twentyNineDaysLater)).isFalse();
            assertThat(DraftEventNudges.isIdleNoticeDue(LAST_MODIFIED_AT, thirtyDaysLater)).isTrue();
        }

        @Test
        @DisplayName("30일이 꼬박 지나야 한다 — 1마이크로초 모자라면 아직 아니고, 31일째에도 여전히 알릴 때다")
        void boundary() {
            Instant thirtyDaysLater = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 14, 15, 0));

            assertThat(DraftEventNudges.isIdleNoticeDue(LAST_MODIFIED_AT, thirtyDaysLater.minus(KoreanTime.PRECISION))).isFalse();
            assertThat(DraftEventNudges.isIdleNoticeDue(LAST_MODIFIED_AT, thirtyDaysLater.plus(Duration.ofDays(1)))).isTrue();
        }

        @Test
        @DisplayName("상한은 지금에서 30일을 거슬러 올라간 순간이다 — 10/14 15:00의 상한은 9/14 15:00")
        void cutoff() {
            Instant now = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 14, 15, 0));

            assertThat(DraftEventNudges.idleNoticeCutoff(now)).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("고치면 새 구간이 시작된다 — 10/1에 고쳤으면 10/14에는 아니고 10/31부터 다시 알릴 때다")
        void editingStartsNewIdlePeriod() {
            Instant editedAt = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 1, 9, 0));

            assertThat(DraftEventNudges.isIdleNoticeDue(editedAt, KoreanTime.toInstant(LocalDateTime.of(2026, 10, 14, 15, 0))))
                    .isFalse();
            assertThat(DraftEventNudges.isIdleNoticeDue(editedAt, KoreanTime.toInstant(LocalDateTime.of(2026, 10, 31, 9, 0))))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("회차 날짜가 이미 지나면 접어둠")
    class Folding {

        private static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);

        @Test
        @DisplayName("마지막 날짜 3/14가 지난 3/15 00:00(한국 시각)부터 접는다 — 3/14 당일 23:59까지는 접지 않는다")
        void foldsAfterLastDate() {
            assertThat(DraftEventNudges.isFolded(MARCH_14, KoreanTime.toInstant(LocalDateTime.of(2026, 3, 14, 23, 59)))).isFalse();
            assertThat(DraftEventNudges.isFolded(MARCH_14, KoreanTime.startOfDay(LocalDate.of(2026, 3, 15)))).isTrue();
        }

        @Test
        @DisplayName("날짜는 한국 날짜로 본다 — 3/15 00:30(한국)은 UTC로 아직 3/14지만 접는다")
        void usesKoreanDate() {
            Instant justAfterMidnightInKorea = Instant.parse("2026-03-14T15:30:00Z");

            assertThat(DraftEventNudges.isFolded(MARCH_14, justAfterMidnightInKorea)).isTrue();
        }

        @Test
        @DisplayName("남은 날짜가 있으면 접지 않는다 — 앞 날짜 3/14가 지나도 마지막 날짜 3/21이 오지 않았으면 그대로")
        void keepsWhileAnyDateRemains() {
            assertThat(DraftEventNudges.isFolded(LocalDate.of(2026, 3, 21), KoreanTime.startOfDay(LocalDate.of(2026, 3, 15))))
                    .isFalse();
        }

        @Test
        @DisplayName("날짜가 하나도 없으면(2단계 전) 접지 않는다")
        void keepsWithoutDates() {
            assertThat(DraftEventNudges.isFolded(null, KoreanTime.startOfDay(LocalDate.of(2026, 3, 15)))).isFalse();
        }
    }

    @Nested
    @DisplayName("이어서 작성 — 멈춘 단계로 가는 버튼")
    class ResumeStep {

        @Test
        @DisplayName("'4/7 단계 · 가격 설정까지 완료'면 이어서 5단계를 연다")
        void afterPricing() {
            assertThat(EventWizardStep.PRICING.number()).isEqualTo(4);
            assertThat(EventWizardStep.PRICING.next()).isEqualTo(EventWizardStep.BOOKING_SETTINGS);
            assertThat(EventWizardStep.PRICING.next().number()).isEqualTo(5);
        }

        @Test
        @DisplayName("단계마다 바로 다음 단계 — 1단계 뒤는 2단계, 6단계 뒤는 7단계(검토), 7단계는 그대로")
        void nextSteps() {
            assertThat(EventWizardStep.BASIC_INFORMATION.next()).isEqualTo(EventWizardStep.SCHEDULE);
            assertThat(EventWizardStep.SCHEDULE.next()).isEqualTo(EventWizardStep.SEATING);
            assertThat(EventWizardStep.SEATING.next()).isEqualTo(EventWizardStep.PRICING);
            assertThat(EventWizardStep.BOOKING_SETTINGS.next()).isEqualTo(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
            assertThat(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS.next()).isEqualTo(EventWizardStep.REVIEW);
            assertThat(EventWizardStep.REVIEW.next()).isEqualTo(EventWizardStep.REVIEW);
        }
    }
}
