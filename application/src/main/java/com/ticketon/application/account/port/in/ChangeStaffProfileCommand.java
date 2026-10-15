package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffIntroduction;
import java.util.Objects;

/**
 * 내 프로필 편집 입력 — 닉네임과 한 줄 소개를 함께 고친다(docs/13 "내 프로필 — 닉네임 · 한 줄 소개 편집").
 *
 * @param account      로그인한 주체 — 운영진(STAFF) 계정이어야 한다
 * @param nickname     새 닉네임 — 필수
 * @param introduction 새 한 줄 소개. 비웠으면 null — 소개를 지운다.
 */
public record ChangeStaffProfileCommand(AccountRef account, Nickname nickname, StaffIntroduction introduction) {

    public ChangeStaffProfileCommand {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(nickname, "nickname");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("운영진 계정만 프로필을 고칩니다: " + account.principalName());
        }
    }
}
