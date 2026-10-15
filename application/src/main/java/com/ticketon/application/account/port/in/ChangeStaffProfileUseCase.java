package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 내 프로필 편집 — 들어오는 포트. 로그인한 운영진이 닉네임과 한 줄 소개를 함께 고친다(docs/13 "내 프로필 — 닉네임 · 한 줄 소개 편집",
 * docs/99 C "운영진 닉네임 — 필수 · 중복 불가 · 변경 가능").
 * <p>
 * 두 칸은 운영진 프로필에 공개된다(docs/01 "공개 페이지 — 운영진 프로필"). 참여 이력은 계정을 가리키므로 닉네임을 바꿔도 그대로 따라간다.
 * 닉네임이 다른 운영진과 겹치면 소개도 바꾸지 않는다. 둘 다 지금과 같으면 아무것도 바꾸지 않고 그대로 돌려준다.
 */
public interface ChangeStaffProfileUseCase {

    /**
     * @return 고친 뒤의 내 프로필
     * @throws RuleViolationException 다른 운영진이 쓰는 닉네임(STAFF_NICKNAME_ALREADY_TAKEN), 인증 대기(STAFF_NOT_ACTIVE)·탈퇴
     *                                (STAFF_WITHDRAWN) 계정 — 인증 전·탈퇴 뒤에는 로그인할 수 없으므로 보통은 생기지 않는다
     * @throws ConflictException      같은 닉네임으로 거의 동시에 바꾼 다른 요청이 먼저 저장됐으면 — 문구는 위와 같다
     */
    MyStaffProfileResult changeProfile(ChangeStaffProfileCommand command);
}
