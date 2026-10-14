package com.ticketon.application.notification.port.out;

import com.ticketon.domain.notification.EmailMessage;
import com.ticketon.domain.notification.SendResult;

/**
 * 이메일 발송 — 나가는 포트.
 * <p>
 * 발송 업체는 배포 때 정한다(docs/16 "문자 · 이메일 · 파일"). 그때까지는 받는 곳·제목·본문을 기록만 하는 어댑터가 구현한다.
 */
public interface EmailSendPort {

    /**
     * 이메일 한 통을 보낸다.
     * <p>
     * 보내지 못해도 예외를 던지지 않고 {@link SendResult.Failure}에 사유를 담아 돌려준다. 한 통의 실패가 알림함 기록이나
     * 다른 수단의 발송을 막지 않게 하려는 것이다({@link SendResult}).
     */
    SendResult send(EmailMessage message);
}
