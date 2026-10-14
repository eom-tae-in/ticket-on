package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.AccountRef;

/**
 * 로그인 세션 끊기 — 나가는 포트.
 * <p>
 * 이미 로그인된 세션을 바로 끊어야 하는 일이 있다: 관리자 정지, 탈퇴, 검표 계정 비밀번호 재발급("기존 로그인 전부 끊김")
 * (docs/01). 로그인을 JWT가 아니라 DB에 저장한 세션으로 한 이유가 이것이다(docs/16 "고르지 않은 것 — JWT 로그인").
 * 구현은 세션 저장소에서 그 주체의 세션을 지운다. 끊긴 기기는 다음 요청부터 로그인이 필요하다.
 */
public interface SessionTerminationPort {

    /**
     * 그 주체가 로그인해 있는 세션을 모두 끊는다. 검표 계정처럼 여러 기기에서 동시에 로그인해 있으면 전부 끊는다.
     * <p>
     * 다른 주체의 세션은 건드리지 않는다 — 같은 id의 다른 종류 계정(예: 같은 이메일로 만든 운영진 로그인)도 그대로 둔다.
     * 로그인된 세션이 없어도 오류가 아니다.
     *
     * @return 끊은 세션 수
     */
    int terminateAllSessions(AccountRef account);
}
