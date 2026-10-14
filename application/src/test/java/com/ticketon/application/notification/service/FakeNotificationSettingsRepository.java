package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.out.NotificationSettingsRepository;
import com.ticketon.domain.notification.NotificationPreference;
import com.ticketon.domain.notification.NotificationSettings;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** 메모리 수신 설정 저장소 — 표처럼 바꾼 묶음만 담아 두고, 없는 묶음은 기본값으로 되살린다. */
final class FakeNotificationSettingsRepository implements NotificationSettingsRepository {

    private final Map<Long, Map<NotificationPreference, Boolean>> storedChoices = new HashMap<>();

    @Override
    public NotificationSettings findByAudienceAccountId(long audienceAccountId) {
        return NotificationSettings.restore(audienceAccountId, storedChoices.getOrDefault(audienceAccountId, Map.of()));
    }

    @Override
    public void saveChoice(long audienceAccountId, NotificationPreference preference, boolean receives) {
        storedChoices.computeIfAbsent(audienceAccountId, id -> new EnumMap<>(NotificationPreference.class))
                .put(preference, receives);
    }
}
