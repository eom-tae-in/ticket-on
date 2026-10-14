package com.ticketon.application.notification.port.in;

import com.ticketon.domain.notification.NotificationPreference;
import com.ticketon.domain.notification.NotificationSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 내 알림 설정 — 마이페이지 알림 설정의 목록(docs/12 "알림 설정").
 * <p>
 * 끌 수 있는 묶음만 화면 순서대로 담는다. 끌 수 없는 알림은 목록에 넣지 않는다(docs/12 "해제할 수 없는 알림은 목록에 넣지 않습니다").
 *
 * @param preferences 묶음과 받는지, 화면 순서대로
 */
public record NotificationSettingsResult(List<PreferenceChoice> preferences) {

    public NotificationSettingsResult {
        preferences = List.copyOf(preferences);
    }

    public static NotificationSettingsResult from(NotificationSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new NotificationSettingsResult(Arrays.stream(NotificationPreference.values())
                .map(preference -> new PreferenceChoice(preference, settings.receives(preference)))
                .toList());
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
