package com.ticketon.domain.notification;

import java.util.Objects;

/**
 * 이메일 제목 — 받는 쪽과 메일 종류에 따른 접두사 규칙을 가진다.
 * <ul>
 *   <li>관객 쪽 알림: 접두사 없음.</li>
 *   <li>주최자 쪽 알림: {@value #ORGANIZER_PREFIX}로 시작. 같은 이메일로 사용자 계정과 주최자 쪽 로그인을 둘 다 만들 수 있어
 *       메일함이 섞이기 때문이다(docs/12 "주최자 대상", docs/01 "같은 이메일로 양쪽 가입").</li>
 *   <li>광고(마케팅) 메일: {@value #ADVERTISEMENT_PREFIX}로 시작. 정보통신망법이 요구한다(docs/12 "마케팅 발송").
 *       마케팅 수신 동의는 사용자 가입 때 받으므로(docs/01 "가입 시") 광고 메일은 관객 쪽에만 가고, [주최] 접두사와 겹치지 않는다.</li>
 * </ul>
 * 규칙을 건너뛴 제목을 만들 수 없게 생성자를 닫고 {@link #notice}·{@link #advertisement}로만 만든다.
 * (record는 생성자를 닫을 수 없어 클래스로 둔다.)
 */
public final class EmailSubject {

    static final String ORGANIZER_PREFIX = "[주최] ";
    static final String ADVERTISEMENT_PREFIX = "(광고) ";

    private final String value;

    private EmailSubject(String value) {
        this.value = value;
    }

    /**
     * 거래 알림(예매 완료·회차 취소·승인 결과 등)의 제목.
     *
     * @param side  받는 쪽. 주최자 쪽이면 {@value #ORGANIZER_PREFIX} 접두사가 붙는다.
     * @param title 접두사를 뺀 제목
     */
    public static EmailSubject notice(RecipientSide side, String title) {
        Objects.requireNonNull(side, "side");
        requireTitle(title);
        return switch (side) {
            case AUDIENCE -> new EmailSubject(title);
            case ORGANIZER -> new EmailSubject(ORGANIZER_PREFIX + title);
        };
    }

    /**
     * 광고(마케팅) 메일의 제목. 맨 앞이 {@value #ADVERTISEMENT_PREFIX}다.
     *
     * @param title 접두사를 뺀 제목
     */
    public static EmailSubject advertisement(String title) {
        requireTitle(title);
        return new EmailSubject(ADVERTISEMENT_PREFIX + title);
    }

    /** 접두사까지 붙은, 실제로 보낼 제목. */
    public String value() {
        return value;
    }

    private static void requireTitle(String title) {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("이메일 제목이 비어 있습니다");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmailSubject subject && value.equals(subject.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
