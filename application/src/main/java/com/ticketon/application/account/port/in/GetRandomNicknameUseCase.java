package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.RuleViolationException;

/**
 * 랜덤 닉네임 받기 — 들어오는 포트. 운영진 가입·닉네임 변경 화면의 [랜덤 생성] 버튼(docs/01 "닉네임 — 입력", docs/13 "내 프로필").
 * <p>
 * 지금 다른 운영진이 쓰지 않는 조합을 하나 제안한다. 제안일 뿐 쥐어 두지 않는다 — 가입하거나 바꿀 때 중복을 다시 확인한다.
 */
public interface GetRandomNicknameUseCase {

    /**
     * @throws RuleViolationException 만들 수 있는 조합을 모두 쓰고 있으면(RANDOM_NICKNAME_EXHAUSTED)
     */
    GetRandomNicknameResult getRandomNickname();
}
