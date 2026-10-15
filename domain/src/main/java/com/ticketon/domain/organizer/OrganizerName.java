package com.ticketon.domain.organizer;

import java.text.Normalizer;
import java.util.Objects;

/**
 * 주최자명 — 신청서의 "이름 또는 단체명"이고, 승인되면 주최자 페이지와 행사 상세에 나가는 이름이다(docs/01 "신청 항목", "공개 페이지").
 * <p>
 * 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 화면에 똑같이 보이는 이름이 글자로는 달라 심사 화면의 "같은 단체명의 닫힌 주최자"
 * (docs/01 "닫힌 주최자를 다시 열 때")에서 빠지지 않게 하기 위해서다. 겹쳐도 된다 — 같은 이름은 막지 않고 심사에서 사람이 본다.
 * 길이와 쓸 수 있는 글자는 문서에 없어 정하지 않았다.
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 이름
 */
public record OrganizerName(String value) {

    public OrganizerName {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("주최자명이 비어 있습니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비면
     */
    public static OrganizerName of(String input) {
        return new OrganizerName(input);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && !normalize(input).isEmpty();
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
    }
}
