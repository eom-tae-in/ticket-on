package com.ticketon.application.notification.port.in;

/**
 * 알림함 열기 — 들어오는 포트. 마이페이지 알림함 목록을 돌려주고, 같은 요청에서 처음 본 시각을 채운다(docs/12 "전달 수단과 알림함").
 * <p>
 * 읽음은 목록 안에서만 구분한다. 지난번에 연 뒤 새로 온 것만 '새로 온 것'으로 표시되고, 다음에 열면 보통 글자로 돌아간다.
 * 안 읽은 개수는 돌려주지 않는다 — 탭에 숫자 배지를 달지 않는다(docs/12, docs/99 D "알림함 읽음 표시").
 */
public interface OpenNotificationInboxUseCase {

    NotificationInboxResult open(OpenNotificationInboxCommand command);
}
