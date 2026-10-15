package com.ticketon.application.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.application.account.port.in.MarketingConsentResult;
import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceCommand;
import com.ticketon.application.notification.port.in.GetNotificationSettingsQuery;
import com.ticketon.application.notification.port.in.NotificationSettingsResult;
import com.ticketon.application.notification.port.in.NotificationSettingsResult.PreferenceChoice;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationPreference;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("알림 설정 조회·변경 유스케이스")
class NotificationSettingsServiceTest {

    private static final AccountRef SEOYUN = AccountRef.of(AccountKind.AUDIENCE, 7);
    private static final AccountRef JIHOON = AccountRef.of(AccountKind.AUDIENCE, 8);

    /** docs/12 "알림 설정" 예시의 동의 일시 — "2026-03-01 동의 · 언제든 철회할 수 있습니다". */
    private static final Instant MARKETING_AGREED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 1, 9, 30));

    private final FakeNotificationSettingsRepository settingsRepository = new FakeNotificationSettingsRepository();
    private final FakeGetMarketingConsentUseCase getMarketingConsentUseCase = new FakeGetMarketingConsentUseCase();

    private final GetNotificationSettingsService getService =
            new GetNotificationSettingsService(settingsRepository, getMarketingConsentUseCase);
    private final ChangeNotificationPreferenceService changeService =
            new ChangeNotificationPreferenceService(settingsRepository, getMarketingConsentUseCase);

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
    @DisplayName("마케팅 수신은 묶음 목록에 섞지 않고 상태와 동의 일시를 따로 담는다 — 2026-03-01 동의 (docs/12 예시)")
    void marketingConsentSeparately() {
        getMarketingConsentUseCase.set(SEOYUN.id(), new MarketingConsentResult(true, MARKETING_AGREED_AT));

        NotificationSettingsResult result = getService.getSettings(new GetNotificationSettingsQuery(SEOYUN));

        assertThat(result.marketing()).isEqualTo(new MarketingConsentResult(true, MARKETING_AGREED_AT));
        assertThat(result.preferences()).hasSize(3);
        assertThat(getService.getSettings(new GetNotificationSettingsQuery(JIHOON)).marketing())
                .isEqualTo(new MarketingConsentResult(false, null));
    }

    @Test
    @DisplayName("한 줄 바꾸기도 바뀐 뒤의 설정 전체와 함께 마케팅 수신 상태를 돌려준다 — 화면이 이 응답으로 다시 그린다")
    void changeAlsoReturnsMarketingConsent() {
        getMarketingConsentUseCase.set(SEOYUN.id(), new MarketingConsentResult(true, MARKETING_AGREED_AT));

        NotificationSettingsResult changed = changeService.changePreference(
                new ChangeNotificationPreferenceCommand(SEOYUN, NotificationPreference.BOOKING_OPEN, false));

        assertThat(changed.marketing()).isEqualTo(new MarketingConsentResult(true, MARKETING_AGREED_AT));
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
