package com.ticketon.domain.notification;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PhoneNumber;
import java.util.Objects;
import java.util.Optional;

/**
 * 알림을 받을 연락처 한 사람분 — 이메일 주소와 전화번호 중 하나 이상.
 * <p>
 * 관객 계정은 둘 다 있다(가입 정보 넷이 전부 필수, docs/01 "수집 정보 — 사용자"). 초대권 받는 사람은 발권할 때 받은 수단만 있어
 * 하나뿐일 수 있고, 그 수단으로만 알림이 간다(docs/12 "초대권 받는 사람"). 주최자 운영진은 이메일만 있다(주최자 쪽 알림은 전부 이메일).
 *
 * @param email       이메일 주소. 없으면 비어 있다.
 * @param phoneNumber 전화번호. 없으면 비어 있다.
 */
public record ContactPoint(Optional<EmailAddress> email, Optional<PhoneNumber> phoneNumber) {

    public ContactPoint {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phoneNumber, "phoneNumber");
        if (email.isEmpty() && phoneNumber.isEmpty()) {
            throw new IllegalArgumentException("연락처에 이메일과 전화번호가 모두 없습니다");
        }
    }

    public static ContactPoint of(EmailAddress email, PhoneNumber phoneNumber) {
        return new ContactPoint(Optional.of(email), Optional.of(phoneNumber));
    }

    public static ContactPoint emailOnly(EmailAddress email) {
        return new ContactPoint(Optional.of(email), Optional.empty());
    }

    public static ContactPoint phoneNumberOnly(PhoneNumber phoneNumber) {
        return new ContactPoint(Optional.empty(), Optional.of(phoneNumber));
    }

    /** 이 연락처로 그 수단을 쓸 수 있는지 — 이메일이면 이메일 주소가, 문자면 전화번호가 있어야 한다. */
    public boolean canReceiveBy(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> email.isPresent();
            case SMS -> phoneNumber.isPresent();
        };
    }

    /**
     * 그 수단으로 받는 곳 — 이메일 주소 또는 숫자만 남긴 전화번호. 발송 이력의 '받는 곳'에 남는다.
     *
     * @throws IllegalStateException 이 연락처로 그 수단을 쓸 수 없으면({@link #canReceiveBy})
     */
    public String addressFor(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> requireEmail().value();
            case SMS -> requirePhoneNumber().digits();
        };
    }

    private EmailAddress requireEmail() {
        return email.orElseThrow(() -> new IllegalStateException("이메일 주소가 없는 연락처입니다"));
    }

    private PhoneNumber requirePhoneNumber() {
        return phoneNumber.orElseThrow(() -> new IllegalStateException("전화번호가 없는 연락처입니다"));
    }
}
