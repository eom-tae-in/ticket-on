package com.ticketon.domain.account;

/**
 * 그 닉네임을 쓸 수 있는지 — 저장소가 확인해 가입({@link StaffAccount#signUp})과 닉네임 변경({@link StaffAccount#changeNickname})에 넘긴다.
 * <p>
 * 닉네임은 운영진 계정 사이에서 하나뿐이다(docs/99 C "운영진 닉네임 — 중복 불가"). 탈퇴한 계정의 닉네임도 쥔 것으로 본다 — 공개 프로필의
 * 이름이라, 지난 이력에 남은 이름을 다른 사람이 가져가면 구분되지 않는다. 관객에게는 닉네임이 없다(docs/01).
 */
public enum NicknameAvailability {

    /** 그 닉네임을 쓰는 운영진 계정이 없다. */
    AVAILABLE,

    /** 그 닉네임을 쓰는 운영진 계정이 있다. */
    ALREADY_TAKEN
}
