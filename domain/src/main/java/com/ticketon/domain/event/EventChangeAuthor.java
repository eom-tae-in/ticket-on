package com.ticketon.domain.event;

import com.ticketon.domain.account.Nickname;
import java.util.Objects;

/**
 * 변경 이력의 "누가 바꿨는지" — 바꾼 운영진(docs/03 "이력에 남길 것 — 누가 바꿨는지 (운영진 닉네임)"). 첫 버전은 판매를 시작한 운영진이다.
 * <p>
 * 닉네임은 바꿀 수 있고(docs/99 C "운영진 닉네임 — 변경 가능") 관리자가 초기화할 수도 있어, 바꾼 그때의 닉네임을 그대로 남긴다 — 관리자가 신고를
 * 확인할 때 그때 보이던 이름을 본다. 사람이 누구인지는 운영진 계정 id로 이어진다.
 *
 * @param staffAccountId 바꾼 운영진의 계정 id
 * @param nickname       바꾼 그때의 닉네임
 */
public record EventChangeAuthor(long staffAccountId, Nickname nickname) {

    public EventChangeAuthor {
        if (staffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + staffAccountId);
        }
        Objects.requireNonNull(nickname, "nickname");
    }
}
