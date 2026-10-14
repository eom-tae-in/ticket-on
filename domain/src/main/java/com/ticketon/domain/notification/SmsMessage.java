package com.ticketon.domain.notification;

import java.util.Objects;

/**
 * 보낼 문자 한 통 — 받는 번호, 본문.
 * <p>
 * 문자는 긴급하거나 돈이 걸린 알림에만 쓴다(docs/12 "원칙"). 받는 번호는 관객이 직접 적은 값이고 인증하지 않는다
 * (docs/12 "전화번호는 인증하지 않습니다") — 번호가 틀려 실패해도 알림함에는 남는다.
 *
 * @param recipientPhoneNumber 받는 전화번호
 * @param body                 본문
 */
public record SmsMessage(String recipientPhoneNumber, String body) {

    public SmsMessage {
        requireText(recipientPhoneNumber, "recipientPhoneNumber", "받는 전화번호가 비어 있습니다");
        requireText(body, "body", "문자 본문이 비어 있습니다");
    }

    private static void requireText(String text, String name, String blankMessage) {
        Objects.requireNonNull(text, name);
        if (text.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
    }
}
