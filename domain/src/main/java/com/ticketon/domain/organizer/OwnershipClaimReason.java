package com.ticketon.domain.organizer;

import java.util.Objects;

/**
 * 소유자 이전 신청 사유 — 신청한 운영자가 소유자와 연락이 끊긴 사정을 적는다. 관리자는 이 글과 주최자 신청 때 받은 연락처 · SNS로 확인한 뒤
 * 소유자를 바꾼다(docs/11 "대표가 바뀔 때").
 *
 * @param text 사유. 앞뒤 공백을 걷는다.
 */
public record OwnershipClaimReason(String text) {

    public OwnershipClaimReason {
        Objects.requireNonNull(text, "text");
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("소유자 이전 신청 사유가 비어 있습니다");
        }
    }

    /**
     * 신청한 운영자가 적은 사유로 만든다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비면
     */
    public static OwnershipClaimReason of(String text) {
        return new OwnershipClaimReason(text);
    }
}
