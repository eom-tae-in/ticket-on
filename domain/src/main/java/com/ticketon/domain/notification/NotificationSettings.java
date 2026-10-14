package com.ticketon.domain.notification;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 관객 한 사람의 알림 수신 설정 — 끌 수 있는 세 묶음({@link NotificationPreference})을 받는지.
 * <p>
 * <b>계정 단위 설정</b>이다. 행사마다 토글을 두면 10개 예매한 사람은 10번 꺼야 한다(docs/12 "알림 설정").
 * 바꾼 적 없는 묶음은 기본값({@link NotificationPreference#receivesByDefault})을 따른다.
 */
public final class NotificationSettings {

    private final long audienceAccountId;
    private final Map<NotificationPreference, Boolean> choices;

    private NotificationSettings(long audienceAccountId, Map<NotificationPreference, Boolean> choices) {
        if (audienceAccountId < 1) {
            throw new IllegalArgumentException("관객 계정 id는 1 이상이어야 합니다: " + audienceAccountId);
        }
        this.audienceAccountId = audienceAccountId;
        this.choices = choices;
    }

    /** 바꾼 적 없는 계정의 설정 — 모든 묶음이 기본값이다. */
    public static NotificationSettings defaultsFor(long audienceAccountId) {
        return restore(audienceAccountId, Map.of());
    }

    /**
     * 저장해 둔 선택으로 되살린다.
     *
     * @param storedChoices 관객이 바꾼 묶음과 그 값. 빠진 묶음은 기본값이다.
     */
    public static NotificationSettings restore(long audienceAccountId, Map<NotificationPreference, Boolean> storedChoices) {
        Objects.requireNonNull(storedChoices, "storedChoices");
        Map<NotificationPreference, Boolean> choices = new EnumMap<>(NotificationPreference.class);
        for (NotificationPreference preference : NotificationPreference.values()) {
            choices.put(preference, storedChoices.getOrDefault(preference, preference.receivesByDefault()));
        }
        return new NotificationSettings(audienceAccountId, Collections.unmodifiableMap(choices));
    }

    public long audienceAccountId() {
        return audienceAccountId;
    }

    /** 그 묶음을 받는지. */
    public boolean receives(NotificationPreference preference) {
        Objects.requireNonNull(preference, "preference");
        return choices.get(preference);
    }

    /** 그 종류의 알림을 받는지 — 끌 수 없는 종류는 늘 받는다. */
    public boolean receives(NotificationKind kind) {
        return NotificationPreference.covering(kind).map(this::receives).orElse(true);
    }

    /** 묶음 하나를 켜거나 끈 설정. 나머지 묶음은 그대로다. */
    public NotificationSettings change(NotificationPreference preference, boolean receives) {
        Objects.requireNonNull(preference, "preference");
        Map<NotificationPreference, Boolean> changed = new EnumMap<>(choices);
        changed.put(preference, receives);
        return new NotificationSettings(audienceAccountId, Collections.unmodifiableMap(changed));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NotificationSettings settings
                && audienceAccountId == settings.audienceAccountId
                && choices.equals(settings.choices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(audienceAccountId, choices);
    }

    @Override
    public String toString() {
        return "NotificationSettings[audienceAccountId=" + audienceAccountId + ", choices=" + choices + "]";
    }
}
