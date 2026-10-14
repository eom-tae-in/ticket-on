package com.ticketon.application.notification.port.out;

import com.ticketon.domain.notification.SendResult;
import com.ticketon.domain.notification.SmsMessage;

/**
 * 문자 발송 — 나가는 포트.
 * <p>
 * 발송 업체는 배포 때 정한다(docs/16 "문자 · 이메일 · 파일"). 그때까지는 받는 번호·본문을 기록만 하는 어댑터가 구현한다.
 */
public interface SmsSendPort {

    /**
     * 문자 한 통을 보낸다.
     * <p>
     * 보내지 못해도 예외를 던지지 않고 {@link SendResult.Failure}에 사유를 담아 돌려준다. 번호가 틀려 실패해도 알림함에는
     * 남고, 긴급 통지의 이메일 발송은 그대로 이어져야 하기 때문이다({@link SendResult}).
     */
    SendResult send(SmsMessage message);
}
