package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.MyStaffProfileResult;
import com.ticketon.domain.account.InitialAvatar;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffIntroduction;

/** 운영진 계정을 내 프로필 결과로 옮긴다 — 조회와 편집이 같은 모양을 돌려준다. 이름 · 이메일은 옮기지 않는다. */
final class MyStaffProfileResults {

    private MyStaffProfileResults() {
    }

    static MyStaffProfileResult of(StaffAccount account) {
        InitialAvatar avatar = account.initialAvatar();
        return new MyStaffProfileResult(account.id().getAsLong(), account.nickname().value(),
                account.introduction().map(StaffIntroduction::value).orElse(null), avatar.initial(),
                avatar.paletteIndex());
    }
}
