package com.ticketon.domain.organizer;

import java.util.Objects;

/**
 * 주최자 쪽 전화번호 — 신청서의 "담당자 연락처"와 사업자의 "사업장 전화번호"(docs/01 "신청 항목").
 * <p>
 * 담당자 연락처는 관리자가 <b>실제로 전화해 보는</b> 번호이고, 사업장 전화번호는 행사 페이지 "판매자 정보"에 그대로 나간다
 * (docs/01 "신청 항목", "판매자 정보"). 휴대폰만이 아니라 유선·대표번호일 수 있어 관객 전화번호의 규칙(010으로 시작하는 11자리)을
 * 쓰지 않는다. 문서에 형식이 없어 앞뒤 공백을 걷고 비어 있지 않은지만 보며, 적은 그대로 둔다.
 * <p>
 * 연락처라 {@link #toString}에는 값을 싣지 않는다 — 예외 설명이나 로그로 새지 않게. 같은지는 값으로 본다.
 *
 * @param value 앞뒤 공백을 걷은 번호
 */
public record TelephoneNumber(String value) {

    public TelephoneNumber {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("전화번호가 비어 있습니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷는다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비면
     */
    public static TelephoneNumber of(String input) {
        return new TelephoneNumber(input);
    }

    @Override
    public String toString() {
        return "TelephoneNumber[****]";
    }
}
