package com.ticketon.domain.notification;

import java.util.Objects;

/**
 * 보낼 이메일 한 통 — 받는 주소, 제목, 본문.
 * <p>
 * 제목은 {@link EmailSubject}로만 받는다. 그래서 [주최]·(광고) 접두사 규칙을 거치지 않은 메일은 만들 수 없다.
 *
 * @param recipientAddress 받는 이메일 주소. 가입 때 인증한 주소다(docs/01 "수집 정보").
 * @param subject          제목
 * @param body             본문
 */
public record EmailMessage(String recipientAddress, EmailSubject subject, String body) {

    public EmailMessage {
        requireText(recipientAddress, "recipientAddress", "받는 이메일 주소가 비어 있습니다");
        Objects.requireNonNull(subject, "subject");
        requireText(body, "body", "이메일 본문이 비어 있습니다");
    }

    private static void requireText(String text, String name, String blankMessage) {
        Objects.requireNonNull(text, name);
        if (text.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
    }
}
