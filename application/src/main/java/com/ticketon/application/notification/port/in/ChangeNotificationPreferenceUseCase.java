package com.ticketon.application.notification.port.in;

/**
 * 알림 설정 한 줄 바꾸기 — 들어오는 포트. 묶음 하나를 켜거나 끄고, 바뀐 뒤의 설정 전체를 돌려준다.
 * <p>
 * 이용 정지 중에도 바꿀 수 있다 — 정지로 막히는 것은 새 예매·변경·신고와 전화번호 변경뿐이다(docs/01 "관객 이용 정지").
 */
public interface ChangeNotificationPreferenceUseCase {

    NotificationSettingsResult changePreference(ChangeNotificationPreferenceCommand command);
}
