package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.out.SmsSendPort;
import com.ticketon.domain.notification.SendResult;
import com.ticketon.domain.notification.SmsMessage;
import java.util.ArrayList;
import java.util.List;

/** 보낸 문자를 모아 두는 발송 포트. 발송 결과를 정할 수 있다. */
final class RecordingSmsSendPort implements SmsSendPort {

    private final List<SmsMessage> sentMessages = new ArrayList<>();
    private SendResult result = SendResult.success();

    void failWith(String reason) {
        result = SendResult.failure(reason);
    }

    List<SmsMessage> sentMessages() {
        return List.copyOf(sentMessages);
    }

    @Override
    public SendResult send(SmsMessage message) {
        sentMessages.add(message);
        return result;
    }
}
