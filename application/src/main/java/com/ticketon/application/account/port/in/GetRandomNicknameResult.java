package com.ticketon.application.account.port.in;

import java.util.Objects;

/**
 * 랜덤 닉네임 — 지금 아무도 쓰지 않는 형용사 + 자연물 조합 하나.
 *
 * @param nickname 제안하는 닉네임(예: 조용한고래)
 */
public record GetRandomNicknameResult(String nickname) {

    public GetRandomNicknameResult {
        Objects.requireNonNull(nickname, "nickname");
    }
}
