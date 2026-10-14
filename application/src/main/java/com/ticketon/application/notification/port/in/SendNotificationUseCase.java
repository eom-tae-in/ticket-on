package com.ticketon.application.notification.port.in;

import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;

/**
 * 알림 발송 — 들어오는 포트. 다른 도메인은 알림을 보낼 때 이 유스케이스만 부른다(notification은 ⓪층이라 모든 층이 부를 수 있다).
 * <p>
 * 알림 종류만으로 수단과 수신 설정이 정해진다. 부르는 쪽은 받는 사람·종류·내용만 넘긴다(docs/12).
 * <ul>
 *   <li>수단 — 종류의 채널 표({@link NotificationKind})대로. 연락처가 하나뿐이면(초대권 받는 사람) 그 수단으로만.</li>
 *   <li>수신 설정 — 관객 계정이 끈 묶음의 알림은 보내지 않는다. 끌 수 없는 알림(행사 변경·취소 등)은 설정과 상관없이 보낸다.</li>
 *   <li>주최자 id — 종류에 따라 소유자와 운영자 전원, 또는 소유자에게만 펼친다. 메일 제목에 [주최]가 붙는다.</li>
 *   <li>발송 이력 — 수단마다 한 줄. 한 수단이 실패해도 예외 없이 사유를 남기고 다른 수단은 그대로 보낸다.</li>
 *   <li>알림함 — 관객 계정으로 보낸 알림은 그 계정의 알림함에도 한 줄 쌓는다. 이메일·문자가 실패해도 남고, 보내지 않은 알림(끈 묶음,
 *       탈퇴한 계정)은 남지 않는다. 직접 연락처와 주최자 쪽 알림은 알림함이 없다(docs/12 "전달 수단과 알림함").</li>
 * </ul>
 */
public interface SendNotificationUseCase {

    /**
     * @throws IllegalArgumentException 그 종류를 그 받는 사람에게 보낼 수 없거나({@link NotificationKind#canBeSentTo}),
     *                                  직접 연락처로 그 종류를 받을 수단이 없거나, 없는 주최자면 — 부르는 쪽의 잘못이다
     */
    void send(SendNotificationCommand command);
}
