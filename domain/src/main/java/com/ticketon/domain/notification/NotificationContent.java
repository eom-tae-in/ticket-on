package com.ticketon.domain.notification;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PhoneNumber;
import java.util.Objects;

/**
 * 알림 내용 — 제목과 본문. 무엇을 쓸지는 알림을 보내는 도메인이 정하고, 수단에 맞게 담는 일은 여기서 한다.
 * <ul>
 *   <li>이메일 — 제목은 받는 쪽 접두사 규칙을 거친다(주최자 쪽이면 [주최], {@link EmailSubject#notice}).</li>
 *   <li>문자 — 제목 칸이 없어 제목을 첫 줄에 두고 한 줄 띄워 본문을 잇는다. 문자만 받는 리마인더도 무슨 알림인지 첫 줄에서 보인다.</li>
 * </ul>
 *
 * @param title 접두사를 뺀 제목
 * @param body  본문
 */
public record NotificationContent(String title, String body) {

    public NotificationContent {
        requireText(title, "title", "알림 제목이 비어 있습니다");
        requireText(body, "body", "알림 본문이 비어 있습니다");
    }

    /** 그 주소로 보낼 이메일. */
    public EmailMessage emailTo(EmailAddress recipient, RecipientSide side) {
        Objects.requireNonNull(recipient, "recipient");
        return new EmailMessage(recipient.value(), EmailSubject.notice(side, title), body);
    }

    /** 그 번호로 보낼 문자. */
    public SmsMessage smsTo(PhoneNumber recipient) {
        Objects.requireNonNull(recipient, "recipient");
        return new SmsMessage(recipient.digits(), title + "\n\n" + body);
    }

    private static void requireText(String text, String name, String blankMessage) {
        Objects.requireNonNull(text, name);
        if (text.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
    }
}
