package com.ticketon.application.notification.port.in;

/**
 * 내 알림 설정 조회 — 들어오는 포트. 바꾼 적 없는 묶음은 기본값으로 보인다(확인 필요 #13 — 제안은 셋 다 받음, ProposedPolicy).
 */
public interface GetNotificationSettingsUseCase {

    NotificationSettingsResult getSettings(GetNotificationSettingsQuery query);
}
