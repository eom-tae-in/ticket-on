package com.ticketon.domain.notification;

/**
 * 알림을 실제로 전달하는 수단 — 이메일과 문자(docs/12 "전달 수단과 알림함").
 * <p>
 * <b>긴급하거나 돈이 걸린 것만 문자, 나머지는 이메일</b>이다(docs/12 "원칙"). 알림 종류마다 어느 수단으로 보내는지는
 * {@link NotificationKind}가 정한다. 서비스 내 알림함은 전달 수단이 아니라 같은 내용을 한 벌 더 쌓아 두는 기록이라 여기 없다.
 * <p>
 * 이 이름은 발송 이력에 그대로 남으므로 바꾸지 않는다. 선언 순서가 보내는 순서다.
 */
public enum NotificationChannel {

    /** 이메일. */
    EMAIL,

    /** 문자. */
    SMS
}
