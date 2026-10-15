package com.ticketon.application.notification.port.in;

import com.ticketon.application.account.port.in.MarketingConsentResult;
import com.ticketon.domain.notification.NotificationPreference;
import com.ticketon.domain.notification.NotificationSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 내 알림 설정 — 마이페이지 알림 설정의 목록과, 그 아래 따로 구분한 마케팅 수신(docs/12 "알림 설정").
 * <p>
 * 끌 수 있는 묶음만 화면 순서대로 담는다. 끌 수 없는 알림은 목록에 넣지 않는다(docs/12 "해제할 수 없는 알림은 목록에 넣지 않습니다").
 * 마케팅 수신은 거래 알림이 아니라 법적 동의 항목이라 묶음 목록에 섞지 않고 동의 일시와 함께 따로 담는다(docs/12 "마케팅 수신은 구분해서 표시").
 *
 * @param preferences 묶음과 받는지, 화면 순서대로
 * @param marketing   마케팅 수신 상태와 동의 일시 — 계정의 동의 기록에서 읽는다
 */
public record NotificationSettingsResult(List<PreferenceChoice> preferences, MarketingConsentResult marketing) {

    public NotificationSettingsResult {
        preferences = List.copyOf(preferences);
        Objects.requireNonNull(marketing, "marketing");
    }

    public static NotificationSettingsResult from(NotificationSettings settings, MarketingConsentResult marketing) {
        Objects.requireNonNull(settings, "settings");
        return new NotificationSettingsResult(Arrays.stream(NotificationPreference.values())
                .map(preference -> new PreferenceChoice(preference, settings.receives(preference)))
                .toList(), marketing);
    }

    /**
     * @param preference 묶음
     * @param receives   받는지
     */
    public record PreferenceChoice(NotificationPreference preference, boolean receives) {

        public PreferenceChoice {
            Objects.requireNonNull(preference, "preference");
        }
    }
}
