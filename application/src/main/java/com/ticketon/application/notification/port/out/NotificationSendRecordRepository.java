package com.ticketon.application.notification.port.out;

import com.ticketon.domain.notification.NotificationSendRecord;

/**
 * 발송 이력 저장소 — 나가는 포트.
 * <p>
 * 알림 한 건을 한 수단으로 한 곳에 보낼 때마다 한 줄씩 남긴다. 부가 기록이라 90일 뒤 파기한다(docs/11 "데이터 보관", 파기는 #32).
 */
public interface NotificationSendRecordRepository {

    void add(NotificationSendRecord record);
}
