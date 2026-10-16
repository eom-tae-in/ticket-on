package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 행사의 공개용 연락처 — 선택이고, 적으면 행사 페이지에 그대로 보인다(docs/03 "기본 정보", docs/13 "주최자 공개 연락처 (입력한 경우)").
 * <p>
 * 서비스 안에 메시지 기능을 두지 않는 대신 관객이 주최자에게 연락하는 창구다(docs/99 B "서비스 내 메시지 기능"). 전화번호 · 이메일 · 오픈채팅
 * 주소 무엇이든 될 수 있어 형식을 보지 않는다 — 앞뒤 공백을 걷고 비어 있지 않은지만 보며, 적은 그대로 둔다.
 * <p>
 * 연락처라 {@link #toString}에는 값을 싣지 않는다 — 예외 설명이나 로그로 새지 않게. 같은지는 값으로 본다.
 *
 * @param value 앞뒤 공백을 걷은 연락처
 */
public record EventPublicContact(String value) {

    public EventPublicContact {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("공개용 연락처가 비어 있습니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷는다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비면
     */
    public static EventPublicContact of(String input) {
        return new EventPublicContact(input);
    }

    @Override
    public String toString() {
        return "EventPublicContact[****]";
    }
}
