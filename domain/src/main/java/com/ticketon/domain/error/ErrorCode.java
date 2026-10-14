package com.ticketon.domain.error;

/**
 * 오류 코드 — 화면이 분기에 쓰는 고정 이름과, 사용자에게 그대로 보여 줄 문구.
 * <p>
 * 도메인 패키지마다 enum으로 구현한다. enum 상수 이름이 그대로 코드가 된다.
 * <pre>{@code
 * public enum BookingErrorCode implements ErrorCode {
 *     SEAT_ALREADY_TAKEN("방금 다른 분이 선택했습니다");   // docs/06
 *
 *     private final String message;
 *
 *     BookingErrorCode(String message) {
 *         this.message = message;
 *     }
 *
 *     @Override
 *     public String message() {
 *         return message;
 *     }
 * }
 * }</pre>
 */
public interface ErrorCode {

    /** enum 상수 이름. enum이 구현하면 저절로 채워진다. */
    String name();

    /** 사용자에게 그대로 보여 줄 문구. 문서에 정해 둔 문구가 있으면 글자 그대로 쓴다. */
    String message();

    /** 화면이 분기에 쓰는 코드 — enum 상수 이름(예: {@code SEAT_ALREADY_TAKEN}). */
    default String code() {
        return name();
    }
}
