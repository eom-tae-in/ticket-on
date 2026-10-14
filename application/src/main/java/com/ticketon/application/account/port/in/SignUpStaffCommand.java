package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffPersonalInformation;
import java.util.Objects;

/**
 * 운영진 로그인 가입 입력 — 이름·닉네임·이메일·비밀번호(docs/01 "수집 정보 — 주최자 운영진").
 *
 * @param personalInformation 이름·이메일
 * @param nickname            닉네임 — 직접 적었거나 [랜덤 생성]으로 받은 값
 * @param password            비밀번호 원문 — 해시로 바꿔 저장하고 원문은 버린다
 */
public record SignUpStaffCommand(StaffPersonalInformation personalInformation, Nickname nickname, NewPassword password) {

    public SignUpStaffCommand {
        Objects.requireNonNull(personalInformation, "personalInformation");
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(password, "password");
    }
}
