package com.ticketon.application.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceCommand;
import com.ticketon.application.notification.port.in.GetNotificationSettingsQuery;
import com.ticketon.application.notification.port.in.NotificationSettingsResult;
import com.ticketon.application.notification.port.in.NotificationSettingsResult.PreferenceChoice;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("알림 설정 조회·변경 유스케이스")
class NotificationSettingsServiceTest {

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, 7);
    private static final AccountRef JIHOON = AccountRef.of(AccountKind.AUDIENCE, 8);

    private final FakeNotificationSettingsRepository settingsRepository = new FakeNotificationSettingsRepository();

    private final GetNotificationSettingsService getService = new GetNotificationSettingsService(settingsRepository);
    private final ChangeNotificationPreferenceService changeService =
            new ChangeNotificationPreferenceService(settingsRepository);

    @Test
    @DisplayName("바꾼 적 없으면 세 묶음 모두 받음으로, 예매·취소 확인 → 리마인더 → 예매 오픈 알림 순서로 보인다")
    void defaultsInScreenOrder() {
        NotificationSettingsResult result = getService.getSettings(new GetNotificationSettingsQuery(SEOYUN));

        assertThat(result.preferences()).containsExactly(
                new PreferenceChoice(NotificationPreference.BOOKING_CONFIRMATION, true),
                new PreferenceChoice(NotificationPreference.DAY_BEFORE_REMINDER, true),
                new PreferenceChoice(NotificationPreference.BOOKING_OPEN, true));
    }

    @Test
    @DisplayName("끌 수 없는 알림은 목록에 없다 — 세 묶음뿐이다")
    void onlySwitchablePreferencesListed() {
        NotificationSettingsResult result = getService.getSettings(new GetNotificationSettingsQuery(SEOYUN));

        assertThat(result.preferences()).hasSize(3);
        assertThat(result.preferences()).extracting(PreferenceChoice::preference)
                .noneMatch(preference -> preference.coveredKinds().contains(NotificationKind.SESSION_CANCELLED));
    }

    @Test
    @DisplayName("리마인더를 끄면 바뀐 뒤의 설정 전체를 돌려주고, 다시 조회해도 꺼져 있다")
    void turnOffReminder() {
        NotificationSettingsResult changed = changeService.changePreference(
                new ChangeNotificationPreferenceCommand(SEOYUN, NotificationPreference.DAY_BEFORE_REMINDER, false));

        assertThat(changed.preferences()).containsExactly(
                new PreferenceChoice(NotificationPreference.BOOKING_CONFIRMATION, true),
                new PreferenceChoice(NotificationPreference.DAY_BEFORE_REMINDER, false),
                new PreferenceChoice(NotificationPreference.BOOKING_OPEN, true));
        assertThat(getService.getSettings(new GetNotificationSettingsQuery(SEOYUN))).isEqualTo(changed);
        assertThat(settingsRepository.findByAudienceAccountId(SEOYUN.id()).receives(NotificationKind.DAY_BEFORE_REMINDER))
                .isFalse();
    }

    @Test
    @DisplayName("끈 것을 다시 켜면 받음으로 돌아온다")
    void turnBackOn() {
        changeService.changePreference(
                new ChangeNotificationPreferenceCommand(SEOYUN, NotificationPreference.BOOKING_OPEN, false));

        NotificationSettingsResult changed = changeService.changePreference(
                new ChangeNotificationPreferenceCommand(SEOYUN, NotificationPreference.BOOKING_OPEN, true));

        assertThat(changed.preferences()).allMatch(PreferenceChoice::receives);
    }

    @Test
    @DisplayName("계정 단위다 — 한 사람이 바꿔도 다른 사람의 설정은 그대로다")
    void perAccount() {
        changeService.changePreference(
                new ChangeNotificationPreferenceCommand(SEOYUN, NotificationPreference.BOOKING_CONFIRMATION, false));

        assertThat(getService.getSettings(new GetNotificationSettingsQuery(JIHOON)).preferences())
                .allMatch(PreferenceChoice::receives);
    }

    @Test
    @DisplayName("관객 계정이 아닌 주체로는 조회·변경 입력을 만들 수 없다")
    void audienceOnly() {
        AccountRef staff = AccountRef.of(AccountKind.STAFF, 7);

        assertThatThrownBy(() -> new GetNotificationSettingsQuery(staff)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeNotificationPreferenceCommand(staff, NotificationPreference.BOOKING_OPEN, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
