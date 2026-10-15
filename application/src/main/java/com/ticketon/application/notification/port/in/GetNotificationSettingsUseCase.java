package com.ticketon.application.notification.port.in;

/**
 * 내 알림 설정 조회 — 들어오는 포트. 바꾼 적 없는 묶음은 기본값으로 보인다(확인 필요 #13 — 제안은 셋 다 받음, ProposedPolicy).
 * 마케팅 수신 상태와 동의 일시도 함께 돌려준다(docs/12 "알림 설정" — "2026-03-01 동의 · 언제든 철회할 수 있습니다").
 */
public interface GetNotificationSettingsUseCase {

    NotificationSettingsResult getSettings(GetNotificationSettingsQuery query);
}
