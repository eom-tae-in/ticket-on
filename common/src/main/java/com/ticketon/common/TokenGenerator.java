package com.ticketon.common;

/**
 * 추측할 수 없는 값 생성기.
 * <p>
 * 비공개 행사 주소(docs/03), 예매번호·QR 열쇠·공유 링크(docs/07), 초대권 링크(docs/09)는 순차 번호처럼
 * 추측할 수 있으면 안 된다. 서비스가 이 생성기로 값을 만들어 도메인 메서드에 인자로 넘긴다.
 * 도메인은 생성기를 모르고 받은 값만 쓴다. 테스트는 고정 생성기(common 테스트 픽스처의 FixedTokenGenerator)를 쓴다.
 * <p>
 * 길이는 값을 쓰는 쪽이 정한다. URL 안전 문자는 한 자에 6비트, 숫자는 한 자에 약 3.3비트를 담는다.
 */
public interface TokenGenerator {

    /** URL·경로에 그대로 넣을 수 있는 문자 64종 (RFC 4648 base64url 알파벳). */
    String URL_SAFE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    /** 숫자 10종. */
    String DIGITS = "0123456789";

    /**
     * {@link #URL_SAFE_ALPHABET} 문자로만 된 길이 {@code length}의 값.
     *
     * @throws IllegalArgumentException 길이가 1보다 작을 때
     */
    String urlSafe(int length);

    /**
     * {@link #DIGITS}로만 된 길이 {@code length}의 값. 첫 자리가 0일 수 있다.
     *
     * @throws IllegalArgumentException 길이가 1보다 작을 때
     */
    String digits(int length);
}
