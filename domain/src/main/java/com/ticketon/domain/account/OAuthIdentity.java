package com.ticketon.domain.account;

import java.util.Objects;

/**
 * 제공자 계정 — 어느 제공자의 어느 사용자인지.
 * <p>
 * 제공자가 정한 사용자 id(구글은 sub, 카카오는 회원번호)는 그 제공자 안에서 바뀌지 않고 겹치지 않는다. 그래서 연결은 이 값으로 찾는다.
 * 이메일로 찾지 않는다 — 이메일은 바뀔 수 있고, 같은 이메일을 주장하는 다른 사람이 있을 수 있다(docs/01 "계정 연동").
 *
 * @param provider       제공자
 * @param providerUserId 제공자가 정한 사용자 id. 받은 그대로 비교한다. {@value #MAX_LENGTH}자 이하.
 */
public record OAuthIdentity(OAuthProvider provider, String providerUserId) {

    /** 사용자 id 길이 상한 — 구글 sub가 255자 이하로 정해져 있고, 카카오 회원번호는 숫자 몇 자리다. */
    public static final int MAX_LENGTH = 255;

    public OAuthIdentity {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerUserId, "providerUserId");
        if (providerUserId.isBlank()) {
            throw new IllegalArgumentException("제공자 사용자 id가 비어 있습니다");
        }
        if (providerUserId.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("제공자 사용자 id는 " + MAX_LENGTH + "자 이하여야 합니다");
        }
    }
}
