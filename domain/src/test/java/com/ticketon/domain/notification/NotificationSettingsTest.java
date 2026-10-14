package com.ticketon.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.policy.ProposedPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("알림 수신 설정 (docs/12 \"알림 설정\")")
class NotificationSettingsTest {

    private static final long SEOYUN = 7;

    /** 설정과 상관없이 보내는 안내 — 행사 변경·취소·연기·장소 변경·시간 변경·출연자 변경·공연 중단(docs/12). */
    private static final List<NotificationKind> MANDATORY_NOTICES = List.of(
            NotificationKind.EVENT_INFORMATION_CHANGED, NotificationKind.SESSION_CANCELLED, NotificationKind.EVENT_CANCELLED,
            NotificationKind.SESSION_POSTPONED, NotificationKind.VENUE_CHANGED, NotificationKind.SESSION_TIME_CHANGED,
            NotificationKind.PERFORMER_CHANGED, NotificationKind.PERFORMANCE_INTERRUPTED);

    @Nested
    @DisplayName("설정 목록")
    class PreferenceList {

        @Test
        @DisplayName("끌 수 있는 것은 예매·취소 확인, 공연 전날 리마인더, 예매 오픈 알림 셋뿐이고 이 순서로 보인다")
        void onlyThreePreferences() {
            assertThat(NotificationPreference.values()).containsExactly(NotificationPreference.BOOKING_CONFIRMATION,
                    NotificationPreference.DAY_BEFORE_REMINDER, NotificationPreference.BOOKING_OPEN);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(NotificationPreference.class)
        @DisplayName("제안 (#13): 거래 알림이라 처음에는 모두 받는다")
        void receivedByDefault(NotificationPreference preference) {
            assertThat(preference.receivesByDefault()).isTrue();
            assertThat(NotificationSettings.defaultsFor(SEOYUN).receives(preference)).isTrue();
        }

        @Test
        @DisplayName("기본값은 정책 숫자 모음의 '확인 필요' 구역 값을 따른다")
        void defaultsComeFromProposedPolicy() {
            assertThat(NotificationPreference.BOOKING_CONFIRMATION.receivesByDefault())
                    .isEqualTo(ProposedPolicy.BOOKING_CONFIRMATION_NOTICE_RECEIVED_BY_DEFAULT);
            assertThat(NotificationPreference.DAY_BEFORE_REMINDER.receivesByDefault())
                    .isEqualTo(ProposedPolicy.DAY_BEFORE_REMINDER_RECEIVED_BY_DEFAULT);
            assertThat(NotificationPreference.BOOKING_OPEN.receivesByDefault())
                    .isEqualTo(ProposedPolicy.BOOKING_OPEN_NOTICE_RECEIVED_BY_DEFAULT);
        }

        @Test
        @DisplayName("설정과 상관없이 보내는 안내는 어느 묶음에도 들지 않는다 — 설정 목록에 나타나지 않는다")
        void mandatoryNoticesAreNotCovered() {
            for (NotificationKind kind : MANDATORY_NOTICES) {
                assertThat(NotificationPreference.covering(kind)).as(kind.name()).isEmpty();
            }
        }

        @Test
        @DisplayName("주최자 쪽 알림은 끌 수 없다")
        void organizerKindsAreNotCovered() {
            Arrays.stream(NotificationKind.values())
                    .filter(kind -> kind.side() == RecipientSide.ORGANIZER)
                    .forEach(kind -> assertThat(NotificationPreference.covering(kind)).as(kind.name()).isEmpty());
        }

        @Test
        @DisplayName("한 종류는 많아야 한 묶음에 든다")
        void eachKindInAtMostOnePreference() {
            for (NotificationKind kind : NotificationKind.values()) {
                long coveringCount = Arrays.stream(NotificationPreference.values())
                        .filter(preference -> preference.coveredKinds().contains(kind))
                        .count();
                assertThat(coveringCount).as(kind.name()).isLessThanOrEqualTo(1);
            }
        }

        @Test
        @DisplayName("제안 (#13): 예매·취소 확인은 예매 완료·예매 변경 완료·취소·환불 완료를 덮고 결제 실패는 덮지 않는다")
        void bookingConfirmationCoverage() {
            assertThat(NotificationPreference.BOOKING_CONFIRMATION.coveredKinds()).containsExactlyInAnyOrder(
                    NotificationKind.BOOKING_COMPLETED, NotificationKind.BOOKING_CHANGED,
                    NotificationKind.CANCELLATION_REFUNDED);
            assertThat(NotificationPreference.covering(NotificationKind.PAYMENT_FAILED)).isEmpty();
        }

        @Test
        @DisplayName("제안 (#13): 리마인더는 리마인더만, 예매 오픈 알림은 오픈 알림만 덮는다 — 행사 삭제는 덮지 않는다")
        void reminderAndBookingOpenCoverage() {
            assertThat(NotificationPreference.DAY_BEFORE_REMINDER.coveredKinds())
                    .containsExactly(NotificationKind.DAY_BEFORE_REMINDER);
            assertThat(NotificationPreference.BOOKING_OPEN.coveredKinds()).containsExactly(NotificationKind.BOOKING_OPENED);
            assertThat(NotificationPreference.covering(NotificationKind.EVENT_DELETED)).isEmpty();
        }
    }

    @Nested
    @DisplayName("받는지 판단")
    class Receives {

        @Test
        @DisplayName("제안 (#13): 바꾼 적 없는 계정은 모든 종류를 받는다")
        void defaultsReceiveEverything() {
            NotificationSettings settings = NotificationSettings.defaultsFor(SEOYUN);

            for (NotificationKind kind : NotificationKind.values()) {
                assertThat(settings.receives(kind)).as(kind.name()).isTrue();
            }
        }

        @Test
        @DisplayName("리마인더를 끄면 리마인더는 안 가고, 회차 취소는 간다")
        void reminderOffStillGetsSessionCancellation() {
            NotificationSettings settings = NotificationSettings.defaultsFor(SEOYUN)
                    .change(NotificationPreference.DAY_BEFORE_REMINDER, false);

            assertThat(settings.receives(NotificationKind.DAY_BEFORE_REMINDER)).isFalse();
            assertThat(settings.receives(NotificationKind.SESSION_CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("세 묶음을 모두 꺼도 설정과 상관없이 보내는 안내와 덮지 않는 종류는 간다")
        void allOffStillGetsUncoveredKinds() {
            NotificationSettings settings = NotificationSettings.defaultsFor(SEOYUN);
            for (NotificationPreference preference : NotificationPreference.values()) {
                settings = settings.change(preference, false);
            }

            for (NotificationKind kind : MANDATORY_NOTICES) {
                assertThat(settings.receives(kind)).as(kind.name()).isTrue();
            }
            assertThat(settings.receives(NotificationKind.SIGNUP_COMPLETED)).isTrue();
            assertThat(settings.receives(NotificationKind.PAYMENT_FAILED)).isTrue();
            assertThat(settings.receives(NotificationKind.BOOKING_COMPLETED)).isFalse();
            assertThat(settings.receives(NotificationKind.BOOKING_OPENED)).isFalse();
        }
    }

    @Nested
    @DisplayName("바꾸기와 되살리기")
    class ChangeAndRestore {

        @Test
        @DisplayName("한 묶음을 바꿔도 나머지는 그대로고, 원래 설정은 바뀌지 않는다")
        void changeKeepsOthers() {
            NotificationSettings original = NotificationSettings.defaultsFor(SEOYUN);

            NotificationSettings changed = original.change(NotificationPreference.BOOKING_OPEN, false);

            assertThat(changed.receives(NotificationPreference.BOOKING_OPEN)).isFalse();
            assertThat(changed.receives(NotificationPreference.BOOKING_CONFIRMATION)).isTrue();
            assertThat(changed.receives(NotificationPreference.DAY_BEFORE_REMINDER)).isTrue();
            assertThat(original.receives(NotificationPreference.BOOKING_OPEN)).isTrue();
        }

        @Test
        @DisplayName("끈 것을 다시 켜면 받는다")
        void turnBackOn() {
            NotificationSettings settings = NotificationSettings.defaultsFor(SEOYUN)
                    .change(NotificationPreference.DAY_BEFORE_REMINDER, false)
                    .change(NotificationPreference.DAY_BEFORE_REMINDER, true);

            assertThat(settings.receives(NotificationKind.DAY_BEFORE_REMINDER)).isTrue();
            assertThat(settings).isEqualTo(NotificationSettings.defaultsFor(SEOYUN));
        }

        @Test
        @DisplayName("저장해 둔 것만으로 되살리면 빠진 묶음은 기본값이다")
        void restoreFillsDefaults() {
            NotificationSettings restored = NotificationSettings.restore(SEOYUN,
                    Map.of(NotificationPreference.DAY_BEFORE_REMINDER, false));

            assertThat(restored.audienceAccountId()).isEqualTo(SEOYUN);
            assertThat(restored).isEqualTo(NotificationSettings.defaultsFor(SEOYUN)
                    .change(NotificationPreference.DAY_BEFORE_REMINDER, false));
        }
    }
}
