package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.RuleViolationException;

/**
 * 내 프로필 조회 — 들어오는 포트. 로그인한 운영진의 닉네임 · 한 줄 소개 · 첫 글자 표시를 돌려준다. 내 프로필 편집 화면이 칸을 채울 때 쓴다
 * (docs/13 "내 프로필 — 닉네임 · 한 줄 소개 편집").
 */
public interface GetMyStaffProfileUseCase {

    /**
     * @throws RuleViolationException 계정이 인증 대기(STAFF_NOT_ACTIVE)·탈퇴(STAFF_WITHDRAWN) 상태면 — 인증 전·탈퇴 뒤에는 로그인할 수 없으므로
     *                                보통은 생기지 않는다
     */
    MyStaffProfileResult getMyProfile(GetMyStaffProfileQuery query);
}
