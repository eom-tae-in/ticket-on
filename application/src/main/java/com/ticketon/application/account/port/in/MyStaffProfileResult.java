package com.ticketon.application.account.port.in;

import java.util.Objects;

/**
 * 내 프로필 — 운영진 프로필에 공개되는 닉네임 · 한 줄 소개와, 사진 대신 보이는 첫 글자 표시(docs/01 "운영진 프로필 — 표시"). 이름 · 이메일은
 * 공개되지 않아 담지 않는다.
 *
 * @param staffAccountId 운영진 계정 id — 공개 프로필을 가리킬 때 쓴다
 * @param nickname       닉네임 — 앞뒤 공백을 걷고 맞춘, 저장된 그대로
 * @param introduction   한 줄 소개 — 저장된 그대로. 적지 않았으면 null.
 * @param initial        닉네임 첫 글자
 * @param paletteIndex   사람마다 정해진 색의 팔레트 번호(0부터, 주최자 로고와 같은 8가지)
 */
public record MyStaffProfileResult(long staffAccountId, String nickname, String introduction, String initial,
        int paletteIndex) {

    public MyStaffProfileResult {
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(initial, "initial");
    }
}
