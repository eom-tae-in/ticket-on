package com.ticketon.message;

import com.ticketon.application.notification.port.out.SmsSendPort;
import com.ticketon.domain.notification.SendResult;
import com.ticketon.domain.notification.SmsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 문자 '기록만' 어댑터 — 실제로 보내지 않고 받는 번호·본문을 로그로 남긴 뒤 성공을 돌려준다.
 * <p>
 * 문자 업체는 배포 때 정한다(docs/16 "문자 · 이메일 · 파일"). 그때까지 모든 알림 흐름을 이 어댑터로 끝까지 돌린다.
 * 받는 번호와 본문(티켓 링크 등)을 그대로 남기므로 개발용이다. 업체 어댑터를 추가할 때 둘 중 무엇을 쓸지 함께 정한다.
 */
@Component
public class LogOnlySmsAdapter implements SmsSendPort {

    private static final Logger log = LoggerFactory.getLogger(LogOnlySmsAdapter.class);

    @Override
    public SendResult send(SmsMessage message) {
        log.info("[기록만] 문자 — 받는 곳: {}, 본문: {}", message.recipientPhoneNumber(), message.body());
        return SendResult.success();
    }
}
