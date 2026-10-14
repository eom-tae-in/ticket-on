package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.RuleViolationException;

/**
 * 내 관객 계정 조회 — 들어오는 포트. 로그인한 관객의 이름·이메일·전화번호·이용 정지 여부·연결된 OAuth를 돌려준다.
 * <p>
 * 이용 정지 중에도 조회된다. 정지 중에도 로그인은 되므로 화면이 이 값으로 정지 여부를 알려 준다(docs/01 "관객 이용 정지").
 */
public interface GetMyAudienceAccountUseCase {

    /**
     * @throws RuleViolationException 계정이 인증 대기·탈퇴 상태라 보여 줄 수 없으면(AUDIENCE_NOT_ACTIVE) — 탈퇴는 세션을 끊으므로 보통은 생기지 않는다
     */
    GetMyAudienceAccountResult getMyAccount(GetMyAudienceAccountQuery query);
}
