package com.ticketon.application.notification.port.in;

/**
 * 내 알림 설정 조회 — 들어오는 포트. 바꾼 적 없는 묶음은 기본값(받음)으로 보인다.
 */
public interface GetNotificationSettingsUseCase {

    NotificationSettingsResult getSettings(GetNotificationSettingsQuery query);
}
