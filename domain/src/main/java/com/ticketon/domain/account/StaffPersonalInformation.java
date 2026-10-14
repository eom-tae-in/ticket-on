package com.ticketon.domain.account;

import java.util.Objects;

/**
 * 운영진 가입 정보 — 이름·이메일.
 * <p>
 * 운영진에게는 <b>이름 · 닉네임 · 이메일(인증) · 비밀번호만 받는다</b>(docs/01 "수집 정보 — 주최자 운영진"). 주최자의 연락처와 정산
 * 계좌는 사람이 아니라 주최자 신청서에 붙는다. 닉네임은 공개 프로필에 나가는 값이라 여기 두지 않고 따로 둔다({@link Nickname}) —
 * 이름과 이메일은 공개 프로필에 보이지 않는다(docs/01 "운영진 프로필 — 보이지 않는 것").
 * <p>
 * 이메일은 운영진 로그인 아이디다. 같은 이메일로 관객 계정을 따로 만들 수 있고 서로 연결하지 않는다(docs/01 "같은 이메일로 양쪽 가입").
 *
 * @param name  이름. 앞뒤 공백을 걷는다.
 * @param email 이메일
 */
public record StaffPersonalInformation(String name, EmailAddress email) {

    public StaffPersonalInformation {
        Objects.requireNonNull(name, "name");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("이름이 비어 있습니다");
        }
        Objects.requireNonNull(email, "email");
    }

    /**
     * 운영진이 적은 값으로 만든다. 이메일은 앞뒤 공백을 걷는다.
     *
     * @throws IllegalArgumentException 이름·이메일이 비었거나 이메일 형식이 맞지 않으면
     */
    public static StaffPersonalInformation of(String name, String email) {
        return new StaffPersonalInformation(name, EmailAddress.of(email));
    }
}
