package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 운영진 닉네임 변경 — 들어오는 포트(docs/99 C "운영진 닉네임 — 변경 가능", docs/13 "내 프로필").
 * <p>
 * 참여 이력은 계정을 가리키므로 닉네임을 바꿔도 그대로 따라간다. 지금 닉네임과 같으면 아무것도 바꾸지 않고 그대로 돌려준다.
 */
public interface ChangeStaffNicknameUseCase {

    /**
     * @throws RuleViolationException 다른 운영진이 쓰는 닉네임(STAFF_NICKNAME_ALREADY_TAKEN), 인증 대기(STAFF_NOT_ACTIVE)·탈퇴
     *                                (STAFF_WITHDRAWN) 계정 — 인증 전·탈퇴 뒤에는 로그인할 수 없으므로 보통은 생기지 않는다
     * @throws ConflictException      같은 닉네임으로 거의 동시에 바꾼 다른 요청이 먼저 저장됐으면 — 문구는 위와 같다
     */
    ChangeStaffNicknameResult changeNickname(ChangeStaffNicknameCommand command);
}
