package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.domain.notification.EmailMessage;
import com.ticketon.domain.notification.SendResult;
import java.util.ArrayList;
import java.util.List;

/** 보낸 이메일을 모아 두는 발송 포트. 발송 결과를 정할 수 있다. */
final class RecordingEmailSendPort implements EmailSendPort {

    private final List<EmailMessage> sentMessages = new ArrayList<>();
    private SendResult result = SendResult.success();

    void failWith(String reason) {
        result = SendResult.failure(reason);
    }

    List<EmailMessage> sentMessages() {
        return List.copyOf(sentMessages);
    }

    @Override
    public SendResult send(EmailMessage message) {
        sentMessages.add(message);
        return result;
    }
}
