package com.ticketon.domain.organizer;

import java.util.Objects;

/**
 * 관리자가 신청을 반려한 사유 — 주최자 신청과 소유자 이전 신청({@link OwnershipClaim})에 쓴다. <b>반드시 남기고 전달한다</b>(docs/01 "반려").
 * <p>
 * 주최자 신청이면 관리자는 템플릿({@link RejectionReasonTemplate})을 고르거나 직접 입력한다. 소유자 이전 신청에는 문서에 정한 템플릿이 없어
 * 직접 입력한다. 어느 쪽이든 전달되는 것은 이 글이다.
 *
 * @param text 사유. 앞뒤 공백을 걷는다.
 */
public record RejectionReason(String text) {

    public RejectionReason {
        Objects.requireNonNull(text, "text");
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("반려 사유가 비어 있습니다");
        }
    }

    /**
     * 관리자가 직접 입력한 사유로 만든다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비면
     */
    public static RejectionReason of(String text) {
        return new RejectionReason(text);
    }
}
