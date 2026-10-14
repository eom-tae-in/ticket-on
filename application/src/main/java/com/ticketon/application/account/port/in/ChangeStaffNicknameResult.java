package com.ticketon.application.account.port.in;

import java.util.Objects;

/**
 * 운영진 닉네임 변경 결과.
 *
 * @param nickname 지금 닉네임 — 앞뒤 공백을 걷고 맞춘, 저장된 그대로
 */
public record ChangeStaffNicknameResult(String nickname) {

    public ChangeStaffNicknameResult {
        Objects.requireNonNull(nickname, "nickname");
    }
}
