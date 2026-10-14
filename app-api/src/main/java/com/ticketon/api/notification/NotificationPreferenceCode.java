package com.ticketon.api.notification;

import com.ticketon.domain.notification.NotificationPreference;

/**
 * 알림 설정 묶음의 API 이름 — 화면 타입과 주소에 쓰인다. 끌 수 있는 세 묶음뿐이고 선언 순서가 화면 순서다(docs/12 "알림 설정").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 묶음({@link NotificationPreference})과 같다.
 */
public enum NotificationPreferenceCode {

    /** 예매·취소 확인. */
    BOOKING_CONFIRMATION(NotificationPreference.BOOKING_CONFIRMATION),

    /** 공연 전날 리마인더. */
    DAY_BEFORE_REMINDER(NotificationPreference.DAY_BEFORE_REMINDER),

    /** 예매 오픈 알림. */
    BOOKING_OPEN(NotificationPreference.BOOKING_OPEN);

    private final NotificationPreference preference;

    NotificationPreferenceCode(NotificationPreference preference) {
        this.preference = preference;
    }

    NotificationPreference toPreference() {
        return preference;
    }

    static NotificationPreferenceCode from(NotificationPreference preference) {
        for (NotificationPreferenceCode code : values()) {
            if (code.preference == preference) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 알림 설정 묶음: " + preference);
    }
}
